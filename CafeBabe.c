
// 
// CafeBabe grammar parser.
//

#include<stdio.h>
#include<stdlib.h>
#include<string.h>
#include<assert.h>

#ifdef __cplusplus
extern "C" {
#endif

struct Tree;
struct TreeLog;
struct MemoEntry;

typedef unsigned long int symbol_t;

typedef struct ParserContext {
    const unsigned char  *inputs;
    size_t length;
    const unsigned char  *pos;
    struct Tree *left;
    int err;

    // AST
    struct TreeLog *logs;
    size_t log_size;
    size_t unused_log;

	// Stack
    struct Wstack *stacks;
    size_t stack_size;
    size_t unused_stack;
    size_t fail_stack;

    // SymbolTable
    struct SymbolTableEntry* tables;
	size_t tableSize;
	size_t tableMax;
	size_t stateValue;
	size_t stateCount;
	unsigned long int count;

    // Memo
    struct MemoEntry *memoArray;
    size_t memoSize;

    // APIs
    void *thunk;
	void* (*fnew)(symbol_t, const unsigned char *, size_t, size_t, void *);
    void (*perr)(const char *, const unsigned char *, void *);
	void (*fsub)(void *, size_t, symbol_t, void *, void *);
	void (*fgc)(void *, int, void*);
} ParserContext;

#ifdef CNEZ_NOGC
#define GCINC(c, v2)     
#define GCDEC(c, v1)     
#define GCSET(c, v1, v2) 
#else
#define GCINC(c, v2)     c->fgc(v2,  1, c->thunk)
#define GCDEC(c, v1)     c->fgc(v1, -1, c->thunk)
#define GCSET(c, v1, v2) c->fgc(v2, 1, c->thunk); c->fgc(v1, -1, c->thunk)
#endif

/* TreeLog */

#define STACKSIZE 64

#define OpLink 0
#define OpTag  1
#define OpReplace 2
#define OpNew 3

typedef struct TreeLog {
	int op;
	void *value;
	struct Tree *tree;
} TreeLog;

static const char* ops[5] = {"link", "tag", "value", "new"};

static size_t cnez_used = 0;

static void *_malloc(size_t t)
{
	size_t *d = (size_t*)malloc(sizeof(size_t) + t);
	cnez_used += t;
	d[0] = t;
	memset((void*)(d+1), 0, t);
	return (void*)(d+1);
}

static void *_calloc(size_t items, size_t size)
{
	return _malloc(items * size);
}

static void _free(void *p)
{
	size_t *d = (size_t*)p;
	cnez_used -= d[-1];
	free(d-1);
}

// Stack

typedef struct Wstack {
	size_t value;
	struct Tree *tree;
} Wstack;

static Wstack *unusedStack(ParserContext *c)
{
	if (c->stack_size == c->unused_stack) {
		Wstack *newstack = (Wstack *)_calloc(c->stack_size * 2, sizeof(struct Wstack));
		memcpy(newstack, c->stacks, sizeof(struct Wstack) * c->stack_size);
		_free(c->stacks);
		c->stacks = newstack;
		c->stack_size *= 2;
	}
	Wstack *s = c->stacks + c->unused_stack;
	c->unused_stack++;
	return s;
}

static void push(ParserContext *c, size_t value)
{
	Wstack *s = unusedStack(c);
	s->value = value;
	GCDEC(c, s->tree);
	s->tree  = NULL;
}

static void pushW(ParserContext *c, size_t value, struct Tree *t)
{
	Wstack *s = unusedStack(c);
	s->value = value;
	GCSET(c, s->tree, t);
	s->tree  = t;
}

static Wstack *popW(ParserContext *c)
{
	c->unused_stack--;
	return c->stacks + c->unused_stack;
}

/* memoization */

#define NotFound    0
#define SuccFound   1
#define FailFound   2

typedef long long int  uniquekey_t;

typedef struct MemoEntry {
    uniquekey_t key;
    long consumed;
	struct Tree *memoTree;
	int result;
	int stateValue;
} MemoEntry;


/* Tree */

typedef struct Tree {
    long           refc;
    symbol_t       tag;
    const unsigned char    *text;
    size_t         len;
    size_t         size;
    symbol_t      *labels;
    struct Tree  **childs;
} Tree;

static size_t t_used = 0;
static size_t t_newcount = 0;
static size_t t_gccount = 0;
 
static void *tree_malloc(size_t t)
{
	size_t *d = (size_t*)malloc(sizeof(size_t) + t);
	t_used += t;
	d[0] = t;
	return (void*)(d+1);
}

static void *tree_calloc(size_t items, size_t size)
{
	void *d = tree_malloc(items * size);
	memset(d, 0, items * size);
	return d;
}

static void tree_free(void *p)
{
	size_t *d = (size_t*)p;
	t_used -= d[-1];
	free(d-1);
}

static void *NEW(symbol_t tag, const unsigned char *text, size_t len, size_t n, void *thunk)
{
    Tree *t = (Tree*)tree_malloc(sizeof(struct Tree));
	t->refc = 0;
    t->tag = tag;
    t->text = text;
    t->len = len;
    t->size = n;
    if(n > 0) {
        t->labels = (symbol_t*)tree_calloc(n, sizeof(symbol_t));
        t->childs = (struct Tree**)tree_calloc(n, sizeof(struct Tree*));
    }
    else {
        t->labels = NULL;
        t->childs = NULL;
    }
	t_newcount++;
    return t;
}

static void GC(void *parent, int c, void *thunk)
{
    Tree *t = (Tree*)parent;
	if(t == NULL) {
		return;
	}
#ifdef CNEZ_NOGC
	if(t->size > 0) {
		size_t i = 0;
		for(i = 0; i < t->size; i++) {
			GC(t->childs[i], -1, thunk);
		}
		tree_free(t->labels);
		tree_free(t->childs);
	}
	tree_free(t);
#else
	if(c == 1) {
		t->refc ++;
		return;
	}
	t->refc --;
	if(t->refc == 0) {
		if(t->size > 0) {
			size_t i = 0;
			for(i = 0; i < t->size; i++) {
				GC(t->childs[i], -1, thunk);
			}
			tree_free(t->labels);
			tree_free(t->childs);
		}
		tree_free(t);
		t_gccount++;
	}
#endif
}

static void LINK(void *parent, size_t n, symbol_t label, void *child, void *thunk)
{
    Tree *t = (Tree*)parent;
    t->labels[n] = label;
    t->childs[n] = (struct Tree*)child;
#ifndef CNEZ_NOGC
	GC(child, 1, thunk);
#endif
}

static void PERR(const char *message, const unsigned char *pos, void *thunk) {
    ParserContext *ctx = (ParserContext*) thunk;

    if(pos > ctx->inputs) {
        const unsigned char* p = ctx->inputs;
        const unsigned char* line = ctx->pos - 1;
        int r = 1;
        int c = 1;

        while(p != line) {
            if (*p == '\n') {
                r++;
                c = 1;
            } else {
                c++;
            }
            p++;
        }

        fprintf(stderr, "ERROR at %d:%d\n", r, c);

        while(*--line != '\n' && line > ctx->inputs) {
        }
        if (*line == '\n') {
            line++;
        }

        p = line;
        fputs(" > ", stderr);
        while(p != (ctx->pos - 1)) {
            fputc(*p++, stderr);
        }
        fprintf(stderr, "\n");
        p = line;
        fputs(" > ", stderr);
        while(p++ != (ctx->pos - 1)) {
            fputc('-', stderr);
        }
        fprintf(stderr, "^ - %s\n", message);
    }
}

void cnez_free(void *t)
{
	GC(t, -1, NULL);
}

static size_t cnez_count(void *v, size_t c)
{
	size_t i;
	Tree *t = (Tree*)v;
	if(t == NULL) {
		return c+0;
	}
	c++;
	for(i = 0; i < t->size; i++) {
		c = cnez_count(t->childs[i], c);
	}
	return c;
}

static void cnez_dump_memory(const char *msg, void *t)
{
	size_t alive = cnez_count(t, 0);
	size_t used = (t_newcount - t_gccount);
	fprintf(stdout, "%s: tree=%ld[bytes], new=%ld, gc=%ld, alive=%ld %s\n", msg, t_used, t_newcount, t_gccount, alive, alive == used ? "OK" : "LEAK");
}

static void ParserContext_initTreeFunc(ParserContext *c, 
	void *thunk,
	void (*ferr)(const char *, const unsigned char *, void *),
	void* (*fnew)(symbol_t, const unsigned char *, size_t, size_t, void *), 
	void  (*fset)(void *, size_t, symbol_t, void *, void *), 
	void  (*fgc)(void *, int, void*))
{
	if(fnew != NULL && fset != NULL && fgc != NULL) {
		c->fnew = fnew;
		c->fsub = fset;
		c->fgc  = fgc;
	}
	else {
		c->thunk = c;
    	c->fnew = NEW;
    	c->fsub = LINK;
    	c->fgc  = GC;
    }

    c->perr = ferr == NULL ? PERR : ferr;
    c->thunk = thunk == NULL ? c : thunk;
}

static void *nonew(symbol_t tag, const unsigned char *pos, size_t len, size_t n, void *thunk)
{
    return NULL;
}

static void nosub(void *parent, size_t n, symbol_t label, void *child, void *thunk)
{
}

static void nogc(void *parent, int c, void *thunk)
{
}

static void noperr(const char *message, const unsigned char *saved_pos, void *thunk)
{
}

static void ParserContext_initNoTreeFunc(ParserContext *c)
{
    c->fnew = nonew;
    c->fsub = nosub;
    c->fgc  = nogc;
    c->perr = noperr;
    c->thunk = c;
}

/* ParserContext */

static ParserContext *ParserContext_new(const unsigned char *text, size_t len)
{
    ParserContext *c = (ParserContext*) _malloc(sizeof(ParserContext));
    c->inputs = text;
    c->length = len;
    c->pos = text;
    c->left = NULL;
    // tree
    c->log_size = 64;
    c->logs = (struct TreeLog*) _calloc(c->log_size, sizeof(struct TreeLog));
    c->unused_log = 0;
    // stack
    c->stack_size = 64;
    c->stacks = (struct Wstack*) _calloc(c->stack_size, sizeof(struct Wstack));
    c->unused_stack = 0;
    c->fail_stack   = 0;
    // symbol table
    c->tables = NULL;
    c->tableSize = 0;
    c->tableMax  = 0;
    c->stateValue = 0;
    c->stateCount = 0;
    c->count = 0;
    // memo
    c->memoArray = NULL;
    c->memoSize = 0;
    return c;
}

static int ParserContext_eof(ParserContext *c)
{
    return !(c->pos < (c->inputs + c->length));
}

static const unsigned char ParserContext_read(ParserContext *c)
{
    return *(c->pos++);
}

static const unsigned char ParserContext_prefetch(ParserContext *c)
{
    return *(c->pos);
}

static void ParserContext_move(ParserContext *c, int shift)
{
    c->pos += shift;
}

static void ParserContext_back(ParserContext *c, const unsigned char *ppos)
{
    c->pos = ppos;
}

static int ParserContext_match(ParserContext *c, const unsigned char *text, size_t len) {
	if (c->pos + len > c->inputs + c->length) {
		return 0;
	}
	size_t i;
	for (i = 0; i < len; i++) {
		if (text[i] != c->pos[i]) {
			return 0;
		}
	}
	c->pos += len;
	return 1;
}

static int ParserContext_eof2(ParserContext *c, size_t n) {
	if (c->pos + n <= c->inputs + c->length) {
		return 1;
	}
	return 0;
}

static int ParserContext_match2(ParserContext *c, const unsigned char c1, const unsigned char c2) {
	if (c->pos[0] == c1 && c->pos[1] == c2) {
		c->pos+=2;
		return 1;
	}
	return 0;
}

static int ParserContext_match3(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3) {
		c->pos+=3;
		return 1;
	}
	return 0;
}

static int ParserContext_match4(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3, const unsigned char c4) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3 && c->pos[3] == c4) {
		c->pos+=4;
		return 1;
	}
	return 0;
}

static int ParserContext_match5(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3, const unsigned char c4, const unsigned char c5) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3 && c->pos[3] == c4 && c->pos[4] == c5 ) {
		c->pos+=5;
		return 1;
	}
	return 0;
}

static int ParserContext_match6(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3, const unsigned char c4, const unsigned char c5, const unsigned char c6) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3 && c->pos[3] == c4 && c->pos[4] == c5 && c->pos[5] == c6 ) {
		c->pos+=6;
		return 1;
	}
	return 0;
}

static int ParserContext_match7(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3, const unsigned char c4, const unsigned char c5, const unsigned char c6, const unsigned char c7) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3 && c->pos[3] == c4 && c->pos[4] == c5 && c->pos[5] == c6 && c->pos[6] == c7) {
		c->pos+=7;
		return 1;
	}
	return 0;
}

static int ParserContext_match8(ParserContext *c, const unsigned char c1, const unsigned char c2, const unsigned char c3, const unsigned char c4, const unsigned char c5, const unsigned char c6, const unsigned char c7, const unsigned char c8) {
	if (c->pos[0] == c1 && c->pos[1] == c2 && c->pos[2] == c3 && c->pos[3] == c4 && c->pos[4] == c5 && c->pos[5] == c6 && c->pos[6] == c7 && c->pos[7] == c8) {
		c->pos+=8;
		return 1;
	}
	return 0;
}

#ifdef CNEZ_SSE
#ifdef _MSC_VER
#include <intrin.h>
#else
#include <x86intrin.h>
#endif
#endif

static const unsigned char *_findchar(
	const unsigned char *p, long size,
	const unsigned char *range, long range_size) {
#ifdef CNEZ_SSE
	const __m128i r = _mm_loadu_si128((const __m128i*)range);
	__m128i v;
	while (size > 0) {
		v = _mm_loadu_si128((const __m128i*)p);
		if (!_mm_cmpestra(r, range_size, v, size, 4)) {
			if (_mm_cmpestrc(r, range_size, v, size, 4)) {
				return p + _mm_cmpestri(r, range_size, v, size, 4);
			}
			break;
		}
		p += 16;
		size -= 16;
	}
#else
	size_t i,j;
	for (i = 0; i < size; i++) {
		const unsigned char c = p[i];
		for (j = 0; j < range_size ; j+=2) {
			if (range[j] <= c && c <= range[j+1]) {
				return p + i;
			}
		}
	}
#endif
	return p + size;
}

static void ParserContext_skipRange(ParserContext *c, const unsigned char *range, size_t range_size)
{
	size_t size = c->length - (c->pos - c->inputs);
	c->pos = _findchar(c->pos, size, range, range_size);
}

static int ParserContext_checkOneMoreRange(ParserContext *c, const unsigned char *range, size_t range_size)
{
	size_t size = c->length - (c->pos - c->inputs);
	const unsigned char *p = _findchar(c->pos, size, range, range_size);
	if(p > c->pos) {
		c->pos = p;
		return 1;
	}
	return 0;
}

// AST

static
void _log(ParserContext *c, int op, void *value, struct Tree *tree)
{
	if(!(c->unused_log < c->log_size)) {
		TreeLog *newlogs = (TreeLog *)_calloc(c->log_size * 2, sizeof(TreeLog));
		memcpy(newlogs, c->logs, c->log_size * sizeof(TreeLog));
		_free(c->logs);
		c->logs = newlogs;
		c->log_size *= 2;
	}
	TreeLog *l = c->logs + c->unused_log;
	l->op = op;
	l->value = value;
	assert(l->tree == NULL);
	if(op == OpLink) {
		GCSET(c, l->tree, tree);
	}
	l->tree  = tree;
	//printf("LOG[%d] %s %p %p\n", (int)c->unused_log, ops[op], value, tree);
	c->unused_log++;
}

void cnez_dump(Tree* v, FILE *fp, int depth);

static void DEBUG_dumplog(ParserContext *c)
{
	long i;
	for(i = c->unused_log-1; i >= 0; i--) {
		TreeLog *l = c->logs + i;
		printf("[%d] %s %p ", (int)i, ops[l->op], l->value);
		if(l->tree != NULL) {
			cnez_dump(l->tree, stdout, 0);
		}
		printf("\n");
	}
}

static void ParserContext_beginTree(ParserContext *c, int shift)
{
    _log(c, OpNew, (void *)(c->pos + shift), NULL);
}

static void ParserContext_linkTree(ParserContext *c, symbol_t label)
{
    _log(c, OpLink, (void*)label, c->left);
}

static void ParserContext_tagTree(ParserContext *c, symbol_t tag)
{
    _log(c, OpTag, (void*)((long)tag), NULL);
}

static void ParserContext_valueTree(ParserContext *c, const unsigned char *text, size_t len)
{
    _log(c, OpReplace, (void*)text, (Tree*)len);
}

static void ParserContext_foldTree(ParserContext *c, int shift, symbol_t label)
{
    _log(c, OpNew, (void*)(c->pos + shift), NULL);
    _log(c, OpLink, (void*)label, c->left);
}

static size_t ParserContext_saveLog(ParserContext *c)
{
    return c->unused_log;
}

static void ParserContext_backLog(ParserContext *c, size_t unused_log)
{
    if (unused_log < c->unused_log) {
  		size_t i;
		for(i = unused_log; i < c->unused_log; i++) {
			TreeLog *l = c->logs + i;
			if(l->op == OpLink) {
				GCDEC(c, l->tree);
			}
			l->op = 0;
			l->value = NULL;
			l->tree = NULL;
		}
		c->unused_log = unused_log;
    }
}

static void ParserContext_endTree(ParserContext *c, int shift, symbol_t tag, const unsigned char *text, size_t len)
{
    int objectSize = 0;
    long i;
    for(i = c->unused_log - 1; i >= 0; i--) {
 	   TreeLog * l = c->logs + i;
 	   if(l->op == OpLink) {
	     objectSize++;
	     continue;
	   }
 	   if(l->op == OpNew) {
 	     break;
 	   }
	   if(l->op == OpTag && tag == 0) {
	     tag = (symbol_t)l->value;
	   }
	   if(l->op == OpReplace) {
	   	 if(text == NULL) {
		     text = (const unsigned char*)l->value;
		     len = (size_t)l->tree;
		 }
	     l->tree = NULL;
	   }
	}
 	TreeLog * start = c->logs + i;
 	if(text == NULL) {
    	text = (const unsigned char*)start->value;
    	len = ((c->pos + shift) - text);
    }
    Tree *t = c->fnew(tag, text, len, objectSize, c->thunk);
	GCSET(c, c->left, t);
    c->left = t;
    if (objectSize > 0) {
        int n = 0;
        size_t j;
        for(j = i; j < c->unused_log; j++) {
 		   TreeLog * cur = c->logs + j;
           if (cur->op == OpLink) {
              c->fsub(c->left, n++, (symbol_t)cur->value, cur->tree, c->thunk);
           }
        }
    }
    ParserContext_backLog(c, i);
}

static size_t ParserContext_saveTree(ParserContext *c)
{
	size_t back = c->unused_stack;
	pushW(c, 0, c->left);
	return back;
}

static void ParserContext_backTree(ParserContext *c, size_t back)
{
	Tree* t = c->stacks[back].tree;
	if(c->left != t) {
		GCSET(c, c->left, t);
		c->left = t;
	}
	c->unused_stack = back;
}

static void ParserContext_reportError(ParserContext* c, const char* message) {
    c->perr(message, c->pos, c->thunk);
}

// Symbol Table ---------------------------------------------------------

static const unsigned char NullSymbol[4] = { 0, 0, 0, 0 };

typedef struct SymbolTableEntry {
	int stateValue;
	symbol_t table;
	const unsigned char* symbol;
	size_t      length;
} SymbolTableEntry;


static
void _push(ParserContext *c, symbol_t table, const unsigned char * utf8, size_t length) 
{
	if (!(c->tableSize < c->tableMax)) {
		SymbolTableEntry* newtable = (SymbolTableEntry*)_calloc(sizeof(SymbolTableEntry), (c->tableMax + 256));
		if(c->tables != NULL) {
			memcpy(newtable, c->tables, sizeof(SymbolTableEntry) * (c->tableMax));
			_free(c->tables);
		}
		c->tables = newtable;
		c->tableMax += 256;
	}
	SymbolTableEntry *entry = c->tables + c->tableSize;
	c->tableSize++;
	if (entry->table == table && entry->length == length && memcmp(utf8, entry->symbol, length) == 0) {
		// reuse state value
		c->stateValue = entry->stateValue;
	} else {
		entry->table = table;
		entry->symbol = utf8;
		entry->length = length;
		c->stateValue = c->stateCount++;
		entry->stateValue = c->stateValue;
	}
}

static int ParserContext_saveSymbolPoint(ParserContext *c) 
{
	return c->tableSize;
}

static 
void ParserContext_backSymbolPoint(ParserContext *c, int savePoint) 
{
	if (c->tableSize != savePoint) {
		c->tableSize = savePoint;
		if (c->tableSize == 0) {
			c->stateValue = 0;
		} else {
			c->stateValue = c->tables[savePoint - 1].stateValue;
		}
	}
}

static
void ParserContext_addSymbol(ParserContext *c, symbol_t table, const unsigned char *ppos) {
	size_t length = c->pos - ppos;
	_push(c, table, ppos, length);
}

static
void ParserContext_addSymbolMask(ParserContext *c, symbol_t table) {
	_push(c, table, NullSymbol, 4);
}

static int ParserContext_exists(ParserContext *c, symbol_t table) 
{
	long i;
	for (i = c->tableSize - 1; i >= 0; i--) {
		SymbolTableEntry * entry = c->tables + i;
		if (entry->table == table) {
			return entry->symbol != NullSymbol;
		}
	}
	return 0;
}

static int ParserContext_existsSymbol(ParserContext *c, symbol_t table, const unsigned char *symbol, size_t length) 
{
	long i;
	for (i = c->tableSize - 1; i >= 0; i--) {
		SymbolTableEntry * entry = c->tables + i;
		if (entry->table == table) {
			if (entry->symbol == NullSymbol) {
				return 0; // masked
			}
			if (entry->length == length && memcmp(entry->symbol, symbol, length) == 0) {
				return 1;
			}
		}
	}
	return 0;
}

static int ParserContext_matchSymbol(ParserContext *c, symbol_t table) 
{
	long i;
	for (i = c->tableSize - 1; i >= 0; i--) {
		SymbolTableEntry * entry = c->tables + i;
		if (entry->table == table) {
			if (entry->symbol == NullSymbol) {
				return 0; // masked
			}
			return ParserContext_match(c, entry->symbol, entry->length);
		}
	}
	return 0;
}

static int ParserContext_equals(ParserContext *c, symbol_t table, const unsigned char *ppos) {
	long i;
	size_t length = c->pos - ppos;
	for (i = c->tableSize - 1; i >= 0; i--) {
		SymbolTableEntry * entry = c->tables + i;
		if (entry->table == table) {
			if (entry->symbol == NullSymbol) {
				return 0; // masked
			}
			return (entry->length == length && memcmp(entry->symbol, ppos, length) == 0);
		}
	}
	return 0;
}

static int ParserContext_contains(ParserContext *c, symbol_t table, const unsigned char *ppos) 
{
	long i;
	size_t length = c->pos - ppos;
	for (i = c->tableSize - 1; i >= 0; i--) {
		SymbolTableEntry * entry = c->tables + i;
		if (entry->table == table) {
			if (entry->symbol == NullSymbol) {
				return 0; // masked
			}
			if (length == entry->length && memcmp(ppos, entry->symbol, length) == 0) {
				return 1;
			}
		}
	}
	return 0;
}


// Counter -----------------------------------------------------------------

static void ParserContext_scanCount(ParserContext *c, const unsigned char *ppos, long mask, int shift) 
{
	long i;
	size_t length = c->pos - ppos;
	if (mask == 0) {
		c->count = strtol((const char*)ppos, NULL, 10);
	} else {
		long n = 0;
		const unsigned char *p = ppos;
		while(p < c->pos) {
			n <<= 8;
			n |= (*p & 0xff);
			p++;
		}
		c->count = (n & mask) >> shift;
	}
}

static int ParserContext_decCount(ParserContext *c) 
{
	return (c->count--) > 0;
}

// Memotable ------------------------------------------------------------

static
void ParserContext_initMemo(ParserContext *c, int w, int n)
{
    int i;
    c->memoSize = w * n + 1;
    c->memoArray = (MemoEntry *)_calloc(sizeof(MemoEntry), c->memoSize);
    for (i = 0; i < c->memoSize; i++) {
        c->memoArray[i].key = -1LL;
    }
}

static  uniquekey_t longkey( uniquekey_t pos, int memoPoint) {
    return ((pos << 12) | memoPoint);
}

static
int ParserContext_memoLookup(ParserContext *c, int memoPoint)
{
    uniquekey_t key = longkey((c->pos - c->inputs), memoPoint);
    unsigned int hash = (unsigned int) (key % c->memoSize);
    MemoEntry* m = c->memoArray + hash;
    if (m->key == key) {
        c->pos += m->consumed;
        return m->result;
    }
    return NotFound;
}

static
int ParserContext_memoLookupTree(ParserContext *c, int memoPoint)
{
    uniquekey_t key = longkey((c->pos - c->inputs), memoPoint);
    unsigned int hash = (unsigned int) (key % c->memoSize);
    MemoEntry* m = c->memoArray + hash;
    if (m->key == key) {
        c->pos += m->consumed;
    	GCSET(c, c->left, m->memoTree);
        c->left = m->memoTree;
        return m->result;
    }
    return NotFound;
}

static
void ParserContext_memoSucc(ParserContext *c, int memoPoint, const unsigned char* ppos)
{
    uniquekey_t key = longkey((ppos - c->inputs), memoPoint);
    unsigned int hash = (unsigned int) (key % c->memoSize);
    MemoEntry* m = c->memoArray + hash;
    m->key = key;
    GCSET(c, m->memoTree, c->left);
    m->memoTree = c->left;
    m->consumed = c->pos - ppos;
    m->result = SuccFound;
    m->stateValue = -1;
}

static
void ParserContext_memoTreeSucc(ParserContext *c, int memoPoint, const unsigned char* ppos)
{
    uniquekey_t key = longkey((ppos - c->inputs), memoPoint);
    unsigned int hash = (unsigned int) (key % c->memoSize);
    MemoEntry* m = c->memoArray + hash;
    m->key = key;
    GCSET(c, m->memoTree, c->left);
    m->memoTree = c->left;
    m->consumed = c->pos - ppos;
    m->result = SuccFound;
    m->stateValue = -1;
}

static
void ParserContext_memoFail(ParserContext *c, int memoPoint)
{
	uniquekey_t key = longkey((c->pos - c->inputs), memoPoint);
    unsigned int hash = (unsigned int) (key % c->memoSize);
    MemoEntry* m = c->memoArray + hash;
    m->key = key;
    GCSET(c, m->memoTree, c->left);
    m->memoTree = c->left;
    m->consumed = 0;
    m->result = FailFound;
    m->stateValue = -1;
}

	/* State Version */

//	public final int lookupStateMemo(int memoPoint) {
//		long key = longkey(pos, memoPoint, shift);
//		int hash = (int) (key % memoArray.length);
//		MemoEntry m = c->memoArray[hash];
//		if (m.key == key) {
//			c->pos += m.consumed;
//			return m.result;
//		}
//		return NotFound;
//	}
//
//	public final int lookupStateTreeMemo(int memoPoint) {
//		long key = longkey(pos, memoPoint, shift);
//		int hash = (int) (key % memoArray.length);
//		MemoEntry m = c->memoArray[hash];
//		if (m.key == key && m.stateValue == c->stateValue) {
//			c->pos += m.consumed;
//			c->left = m.memoTree;
//			return m.result;
//		}
//		return NotFound;
//	}
//
//	public void memoStateSucc(int memoPoint, int ppos) {
//		long key = longkey(ppos, memoPoint, shift);
//		int hash = (int) (key % memoArray.length);
//		MemoEntry m = c->memoArray[hash];
//		m.key = key;
//		m.memoTree = left;
//		m.consumed = pos - ppos;
//		m.result = SuccFound;
//		m.stateValue = c->stateValue;
//		// c->CountStored += 1;
//	}
//
//	public void memoStateTreeSucc(int memoPoint, int ppos) {
//		long key = longkey(ppos, memoPoint, shift);
//		int hash = (int) (key % memoArray.length);
//		MemoEntry m = c->memoArray[hash];
//		m.key = key;
//		m.memoTree = left;
//		m.consumed = pos - ppos;
//		m.result = SuccFound;
//		m.stateValue = c->stateValue;
//		// c->CountStored += 1;
//	}
//
//	public void memoStateFail(int memoPoint) {
//		long key = longkey(pos, memoPoint, shift);
//		int hash = (int) (key % memoArray.length);
//		MemoEntry m = c->memoArray[hash];
//		m.key = key;
//		m.memoTree = left;
//		m.consumed = 0;
//		m.result = FailFound;
//		m.stateValue = c->stateValue;
//	}


static void ParserContext_free(ParserContext *c)
{
	size_t i;
    if(c->memoArray != NULL) {
    	for(i = 0; i < c->memoSize; i++) {
    		GCDEC(c, c->memoArray[i].memoTree);
    		c->memoArray[i].memoTree = NULL;
    	}
        _free(c->memoArray);
        c->memoArray = NULL;
    }
    if(c->tables != NULL) {
    	_free(c->tables);
    	c->tables = NULL;
    }
    ParserContext_backLog(c, 0);
    _free(c->logs);
    c->logs = NULL;
    for(i = 0; i < c->stack_size; i++) {
    	GCDEC(c, c->stacks[i].tree);
    	c->stacks[i].tree = NULL;
    }
    _free(c->stacks);
    c->stacks = NULL;
    _free(c);
}

//----------------------------------------------------------------------------

static inline int ParserContext_bitis(ParserContext *c, int *bits, size_t n)
{
	return (bits[n / 32] & (1 << (n % 32))) != 0;
}


static int _T = 0;
static int _L = 0;
static int _S = 0;
static const unsigned char _set0[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index1[256] = {1,0,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index2[256] = {1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
static const unsigned char _set3[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set4[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TQualifiedName = 1;
static const unsigned char _set5[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TName = 2;
static int _TNameWithAlias = 3;
static int _TImportList = 4;
static int _TUse = 5;
static int _TPublic = 6;
static const unsigned char _index6[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TTypeNameList = 7;
static int _TOptionalEllipsis = 8;
static int _TTypeArguments = 9;
static int _TArrayDecl = 10;
static const unsigned char _text7[2] = {91,93};
static int _TTypeName = 11;
static const unsigned char _index8[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,2,0,2,0,0,2,3,0,0,2,0,2,2,1,2,2,2,2,2,2,2,2,2,2,2,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,2,0,2,2,2,2,2,2,2,2,4,2,2,2,5,2,2,2,2,2,6,2,2,2,2,2,2,2,2,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TBooleanExpression = 12;
static const unsigned char _index9[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,0,1,5,5,5,5,5,5,5,5,5,5,0,0,2,0,0,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,5,2,0,0,3,0,3,6,7,3,3,8,3,3,9,3,3,3,10,3,3,3,3,11,12,3,3,3,3,3,3,3,13,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index10[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TVarTitle = 13;
static int _TForSetup = 14;
static int _TForStatement = 15;
static int _TNestedExpr = 16;
static const unsigned char _index11[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set12[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set13[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _THexNumLiteral = 17;
static const unsigned char _set14[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TOctNumLiteral = 18;
static const unsigned char _set15[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TBinNumLiteral = 19;
static const unsigned char _set16[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set17[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set18[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TFloatNumLiteral = 20;
static int _TDecNumLiteral = 21;
static const unsigned char _index19[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index20[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TNumSuffix = 22;
static const unsigned char _index21[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,3,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TNumLiteral = 23;
static const unsigned char _index22[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index23[256] = {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
static const unsigned char _set24[256] = {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
static const unsigned char _index25[256] = {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
static int _TInterpolatedStringLiteral = 24;
static int _TStringSegment = 25;
static int _TStringLiteral = 26;
static const unsigned char _index26[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,3,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index27[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TNamedAssignment = 27;
static int _TNamedAssignmentList = 28;
static int _TNamedCallParameters = 29;
static int _TExpressionList = 30;
static int _TAnonymousCallParameters = 31;
static int _TCallParameters = 32;
static int _TMethodCall = 33;
static int _TIndex = 34;
static int _TInvoke = 35;
static int _TValueReference = 36;
static int _TObjConstructionBody = 37;
static int _TObjConstruction = 38;
static int _TSelectCase = 39;
static int _TDefault = 40;
static int _TSelectBody = 41;
static int _TSelectExpression = 42;
static const unsigned char _index28[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TTypeConstructor = 43;
static int _TMatchCase = 44;
static int _TMatchBody = 45;
static int _TMatchExpression = 46;
static int _TReturnStatement = 47;
static int _TBreakStatement = 48;
static int _TContinueStatement = 49;
static int _TRegularAssignment = 50;
static int _TNameList = 51;
static int _TRequiredNameList = 52;
static int _TTupleConstruction = 53;
static int _TParallelAssignment = 54;
static int _TAssignmentExpr = 55;
static int _TAssignment = 56;
static int _TVariableDeclaration = 57;
static const unsigned char _index29[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TOpenRange = 58;
static int _TNamedRange = 59;
static int _TComprPipeline = 60;
static int _TComprehension = 61;
static int _TIterable = 62;
static int _TIterableStatement = 63;
static const unsigned char _index30[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,0,1,5,5,5,5,5,5,5,5,5,5,0,0,6,0,0,0,3,3,3,3,3,3,7,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,8,2,0,0,3,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TArrayElements = 64;
static int _TArrayConstruction = 65;
static int _TSingleParamLambda = 66;
static int _TMultiparamLambda = 67;
static int _TLambda = 68;
static const unsigned char _index31[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,3,0,0,0,0,0,0,0,1,4,4,4,4,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set32[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _set33[256] = {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1};
static int _TCharLiteral = 69;
static int _TLiteralValue = 70;
static const unsigned char _index34[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TValue = 71;
static int _TValueStatement = 72;
static int _TStatement = 73;
static int _TIfExpression = 74;
static int _TTernaryExpression = 75;
static const unsigned char _index35[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,0,0,0,0,0,0,3,0,0,2,0,2,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TCast = 76;
static const unsigned char _index36[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,2,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index37[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,5,1,2,2,2,2,2,2,2,2,2,2,6,0,2,0,0,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,2,0,0,3,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TMethodReference = 77;
static int _TProduct = 78;
static const unsigned char _set38[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TSum = 79;
static int _TShift = 80;
static const unsigned char _index39[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TCompare = 81;
static const unsigned char _index40[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TComparison = 82;
static int _TTypeCheck = 83;
static int _TEquality = 84;
static const unsigned char _index41[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TBitwiseAnd = 85;
static int _TBitwiseXor = 86;
static int _TBitwiseOr = 87;
static int _TAnd = 88;
static int _TOr = 89;
static int _TExpr = 90;
static int _TExpression = 91;
static int _TConstant = 92;
static int _TAnnotationRef = 93;
static const unsigned char _index42[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,3,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,4,4,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,5,2,6,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static const unsigned char _index43[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,3,2,2,2,2,2,2,2,2,2,2,2,4,2,2,4,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TLimitedTypeName = 94;
static int _TTypeVarsDecl = 95;
static const unsigned char _index44[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TArgumentType = 96;
static int _TTypedArgument = 97;
static int _TTypedArgList = 98;
static int _TTypedArgDecl = 99;
static int _TTypedFunction = 100;
static int _TOptionalNameList = 101;
static int _TUntypedFunction = 102;
static int _TMethodSignature = 103;
static const unsigned char _index45[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TMethodExpression = 104;
static int _TMethodBody = 105;
static int _TStaticMethodImpl = 106;
static int _TDefaultMethodImpl = 107;
static int _TApiRefs = 108;
static int _TConstructor = 109;
static int _TDestructor = 110;
static const unsigned char _index46[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,3,2,2,2,2,2,2,4,2,2,4,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TFunction = 111;
static int _TMethodImpl = 112;
static int _TClassBlock = 113;
static int _TClassBody = 114;
static int _TNamedClassDecl = 115;
static int _TCommonApiBody = 116;
static const unsigned char _text47[10] = {97,110,110,111,116,97,116,105,111,110};
static int _TAnnotationBody = 117;
static int _TAnnotationDecl = 118;
static int _TTypeAliasDecl = 119;
static const unsigned char _index48[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TAndType = 120;
static int _TOrType = 121;
static const unsigned char _index49[256] = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,2,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TCommonApiDecl = 122;
static int _TCompoundTypeDecl = 123;
static int _TTupleDecl = 124;
static int _TMutable = 125;
static int _TClassField = 126;
static int _TClassFields = 127;
static int _TClassDataDecl = 128;
static int _TClassDecl = 129;
static int _TType = 130;
static int _TExtension = 131;
static int _TCommons = 132;
static int _TTest = 133;
static int _TUnsafe = 134;
static int _TProgram = 135;
static const char * _tags[136] = {"","QualifiedName","Name","NameWithAlias","ImportList","Use","Public","TypeNameList","OptionalEllipsis","TypeArguments","ArrayDecl","TypeName","BooleanExpression","VarTitle","ForSetup","ForStatement","NestedExpr","HexNumLiteral","OctNumLiteral","BinNumLiteral","FloatNumLiteral","DecNumLiteral","NumSuffix","NumLiteral","InterpolatedStringLiteral","StringSegment","StringLiteral","NamedAssignment","NamedAssignmentList","NamedCallParameters","ExpressionList","AnonymousCallParameters","CallParameters","MethodCall","Index","Invoke","ValueReference","ObjConstructionBody","ObjConstruction","SelectCase","Default","SelectBody","SelectExpression","TypeConstructor","MatchCase","MatchBody","MatchExpression","ReturnStatement","BreakStatement","ContinueStatement","RegularAssignment","NameList","RequiredNameList","TupleConstruction","ParallelAssignment","AssignmentExpr","Assignment","VariableDeclaration","OpenRange","NamedRange","ComprPipeline","Comprehension","Iterable","IterableStatement","ArrayElements","ArrayConstruction","SingleParamLambda","MultiparamLambda","Lambda","CharLiteral","LiteralValue","Value","ValueStatement","Statement","IfExpression","TernaryExpression","Cast","MethodReference","Product","Sum","Shift","Compare","Comparison","TypeCheck","Equality","BitwiseAnd","BitwiseXor","BitwiseOr","And","Or","Expr","Expression","Constant","AnnotationRef","LimitedTypeName","TypeVarsDecl","ArgumentType","TypedArgument","TypedArgList","TypedArgDecl","TypedFunction","OptionalNameList","UntypedFunction","MethodSignature","MethodExpression","MethodBody","StaticMethodImpl","DefaultMethodImpl","ApiRefs","Constructor","Destructor","Function","MethodImpl","ClassBlock","ClassBody","NamedClassDecl","CommonApiBody","AnnotationBody","AnnotationDecl","TypeAliasDecl","AndType","OrType","CommonApiDecl","CompoundTypeDecl","TupleDecl","Mutable","ClassField","ClassFields","ClassDataDecl","ClassDecl","Type","Extension","Commons","Test","Unsafe","Program"};
static const char * _labels[1] = {""};
static const char * _tables[1] = {""};
// Prototypes
int e168(ParserContext *c);
int pMethodBlockBody(ParserContext *c);
int e40(ParserContext *c);
int e133(ParserContext *c);
int e85(ParserContext *c);
int e123(ParserContext *c);
int e112(ParserContext *c);
int e154(ParserContext *c);
int pNamedClassDecl(ParserContext *c);
int e140(ParserContext *c);
int e283(ParserContext *c);
int pValue(ParserContext *c);
int e27(ParserContext *c);
int pExpression(ParserContext *c);
int pClassBlock(ParserContext *c);
int p_whitespace(ParserContext *c);
int pAssignment(ParserContext *c);
int e248(ParserContext *c);
// "+="
static inline int e113(ParserContext * c) {
   if (!ParserContext_match2(c,43,61)) {
      // "CPG:1107: Expecting \"+=\""
      return 0;
   }
   return 1;
}
// "-="
static inline int e114(ParserContext * c) {
   if (!ParserContext_match2(c,45,61)) {
      // "CPG:1107: Expecting \"-=\""
      return 0;
   }
   return 1;
}
// ''
static inline int e6(ParserContext * c) {
   return 1;
}
// '\n'
static inline int e4(ParserContext * c) {
   if (ParserContext_read(c) != 10) {
      // "CPG:1037: Expecting '\\n'"
      return 0;
   }
   return 1;
}
// '\n' / ''
static inline int e7(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      // '\n'
      if (e4(c)) {
         temp = 0;
      } else {
         c->pos = pos;
      }
   }
   if (temp) {
      const unsigned char * pos2 = c->pos;
      // ''
      if (e6(c)) {
         temp = 0;
      } else {
         c->pos = pos2;
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of '\\n' / ''"
      return 0;
   }
   return 1;
}
// '\r' ('' / ('\n' / ''))
static inline int e5(ParserContext * c) {
   if (ParserContext_read(c) != 13) {
      // "CPG:1037: Expecting '\\r'"
      return 0;
   }
   int temp = 1;
   switch(_index2[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // ''
      temp = e6(c);
      break;
      case 2: 
      // '\n' / ''
      temp = e7(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting '' / ('\\n' / '')"
      return 0;
   }
   return 1;
}
// !.
static inline int e3(ParserContext * c) {
   if (!ParserContext_eof(c)) {
      // "CPG:1566: Expecting EOF"
      return 0;
   }
   return 1;
}
// !. / '\n' / '\r' ('' / ('\n' / ''))
static inline int p_end(ParserContext * c) {
   int temp = 1;
   switch(_index1[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // !.
      temp = e3(c);
      break;
      case 2: 
      // '\n'
      temp = e4(c);
      break;
      case 3: 
      // '\r' ('' / ('\n' / ''))
      temp = e5(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting !. / '\\n' / '\\r' ('' / ('\\n' / ''))"
      return 0;
   }
   return 1;
}
// !~end .
static inline int e2(ParserContext * c) {
   {
      const unsigned char * pos = c->pos;
      // ~end
      if (p_end(c)) {
         // "CPG:1328: Expecting !~end"
         return 0;
      }
      c->pos = pos;
   }
   if (ParserContext_read(c) == 0) {
      // "CPG:1096: Expecting ."
      return 0;
   }
   return 1;
}
// "//" (!~end .)* &~end ~whitespace?
static inline int p_Comment(ParserContext * c) {
   if (!ParserContext_match2(c,47,47)) {
      // "CPG:1107: Expecting \"//\""
      return 0;
   }
   while (1) {
      const unsigned char * pos = c->pos;
      // !~end .
      if (!e2(c)) {
         c->pos = pos;
         break;
      }
   }
   {
      const unsigned char * pos1 = c->pos;
      // ~end
      if (!p_end(c)) {
         // "CPG:1308: Expecting &~end"
         return 0;
      }
      c->pos = pos1;
   }
   const unsigned char * pos2 = c->pos;
   // ~whitespace
   if (!p_whitespace(c)) {
      c->pos = pos2;
   }
   return 1;
}
// [\t-\n\r ]* ~Comment?
static inline int e1(ParserContext * c) {
   while (_set0[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   const unsigned char * pos = c->pos;
   // ~Comment
   if (!p_Comment(c)) {
      c->pos = pos;
   }
   return 1;
}
// [\t-\n\r ]* ~Comment?
int p_whitespace(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,0);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e1(c)) {
         ParserContext_memoSucc(c,0,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_whitespace ([\\t-\\n\\r ]* ~Comment?)"
         c->pos = pos;
         ParserContext_memoFail(c,0);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace !"F\"" [$@-Z_a-z] { [$0-9A-Z_a-z]* #Name } ~whitespace
static inline int e11(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_match2(c,70,34)) {
      // "CPG:1555: Expecting not F\""
      return 0;
   }
   if (!_set3[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [$@-Z_a-z]"
      return 0;
   }
   ParserContext_beginTree(c,-1);
   while (_set5[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   ParserContext_endTree(c,0,_TName,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   return 1;
}
// ~whitespace !"F\"" [$@-Z_a-z] { [$0-9A-Z_a-z]* #Name } ~whitespace
static inline int pName(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,2);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e11(c)) {
         ParserContext_memoTreeSucc(c,2,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pName (~whitespace !\"F\\\"\" [$@-Z_a-z] { [$0-9A-Z_a-z]* #Name } ~whitespace)"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,2);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace ']'
static inline int p_RSqB(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 93) {
      // "CPG:1037: Expecting ']'"
      return 0;
   }
   return 1;
}
// ~whitespace '['
static inline int p_LSqB(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 91) {
      // "CPG:1037: Expecting '['"
      return 0;
   }
   return 1;
}
// $((~LSqB { `[]` #ArrayDecl } ~RSqB))
static inline int e32(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_LSqB(c)) {
      // "CPG:1014: Expecting ~LSqB"
      return 0;
   }
   ParserContext_beginTree(c,0);
   ParserContext_endTree(c,0,_TArrayDecl,_text7, 2);
   if (!p_RSqB(c)) {
      // "CPG:1014: Expecting ~RSqB"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~whitespace '&'
static inline int p_BitAnd(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 38) {
      // "CPG:1037: Expecting '&'"
      return 0;
   }
   return 1;
}
// { $(Name) ($(TypeArguments))? ~BitAnd? ($((~LSqB { `[]` #ArrayDecl } ~RSqB)))* #TypeName }
static inline int e26(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pName(c)) {
         // "CPG:1014: Expecting Name"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   const unsigned char * pos = c->pos;
   size_t left2 = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(TypeArguments)
   if (!e27(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left2);
      ParserContext_backLog(c,log);
   }
   const unsigned char * pos4 = c->pos;
   // ~BitAnd
   if (!p_BitAnd(c)) {
      c->pos = pos4;
   }
   while (1) {
      const unsigned char * pos5 = c->pos;
      size_t left6 = ParserContext_saveTree(c);
      size_t log7 = ParserContext_saveLog(c);
      // $((~LSqB { `[]` #ArrayDecl } ~RSqB))
      if (!e32(c)) {
         c->pos = pos5;
         ParserContext_backTree(c,left6);
         ParserContext_backLog(c,log7);
         break;
      }
   }
   ParserContext_endTree(c,0,_TTypeName,NULL, 0);
   return 1;
}
// { $(Name) ($(TypeArguments))? ~BitAnd? ($((~LSqB { `[]` #ArrayDecl } ~RSqB)))* #TypeName }
static inline int pTypeName(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,1);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e26(c)) {
         ParserContext_memoTreeSucc(c,1,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pTypeName ({ $(Name) ($(TypeArguments))? ~BitAnd? ($((~LSqB { `[]` #ArrayDecl } ~RSqB)))* #TypeName })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,1);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace ','
static inline int e14(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 44) {
      // "CPG:1037: Expecting ','"
      return 0;
   }
   return 1;
}
// ~whitespace ','
static inline int p_Comma(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,18);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e14(c)) {
         ParserContext_memoSucc(c,18,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_Comma (~whitespace ',')"
         c->pos = pos;
         ParserContext_memoFail(c,18);
         return 0;
      }
   }
   return memo == 1;
}
// ~Comma $(TypeName)
static inline int e29(ParserContext * c) {
   if (!p_Comma(c)) {
      // "CPG:1014: Expecting ~Comma"
      return 0;
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pTypeName(c)) {
         // "CPG:1014: Expecting TypeName"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   return 1;
}
// { $(TypeName) (~Comma $(TypeName))* #TypeNameList }
static inline int pTypeNameList(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pTypeName(c)) {
         // "CPG:1014: Expecting TypeName"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // ~Comma $(TypeName)
      if (!e29(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
   }
   ParserContext_endTree(c,0,_TTypeNameList,NULL, 0);
   return 1;
}
// ~whitespace "..."
static inline int p_Ellipsis(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match3(c,46,46,46)) {
      // "CPG:1107: Expecting \"...\""
      return 0;
   }
   return 1;
}
// { ~Comma ~Ellipsis #OptionalEllipsis }
static inline int e31(ParserContext * c) {
   ParserContext_beginTree(c,0);
   if (!p_Comma(c)) {
      // "CPG:1014: Expecting ~Comma"
      return 0;
   }
   if (!p_Ellipsis(c)) {
      // "CPG:1014: Expecting ~Ellipsis"
      return 0;
   }
   ParserContext_endTree(c,0,_TOptionalEllipsis,NULL, 0);
   return 1;
}
// { ~Comma ~Ellipsis #OptionalEllipsis }
static inline int pOptionalEllipsis(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,14);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e31(c)) {
         ParserContext_memoTreeSucc(c,14,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pOptionalEllipsis ({ ~Comma ~Ellipsis #OptionalEllipsis })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,14);
         return 0;
      }
   }
   return memo == 1;
}
// $(OptionalEllipsis)
static inline int e30(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pOptionalEllipsis(c)) {
      // "CPG:1014: Expecting OptionalEllipsis"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~whitespace '<' { $(TypeNameList) ($(OptionalEllipsis))? #TypeArguments } ~whitespace '>'
static inline int e28(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 60) {
      // "CPG:1037: Expecting '<'"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pTypeNameList(c)) {
         // "CPG:1014: Expecting TypeNameList"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   const unsigned char * pos = c->pos;
   size_t left2 = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(OptionalEllipsis)
   if (!e30(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left2);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TTypeArguments,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 62) {
      // "CPG:1037: Expecting '>'"
      return 0;
   }
   return 1;
}
// ~whitespace '<' { $(TypeNameList) ($(OptionalEllipsis))? #TypeArguments } ~whitespace '>'
static inline int pTypeArguments(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,29);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e28(c)) {
         ParserContext_memoTreeSucc(c,29,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pTypeArguments (~whitespace '<' { $(TypeNameList) ($(OptionalEllipsis))? #TypeArguments } ~whitespace '>')"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,29);
         return 0;
      }
   }
   return memo == 1;
}
// $(TypeArguments)
int e27(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pTypeArguments(c)) {
      // "CPG:1014: Expecting TypeArguments"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(TypeName)
static inline int e25(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pTypeName(c)) {
      // "CPG:1014: Expecting TypeName"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(TypeArguments) #TypeConstructor
static inline int e104(ParserContext * c) {
   {
      size_t left = ParserContext_saveTree(c);
      if (!pTypeArguments(c)) {
         // "CPG:1014: Expecting TypeArguments"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_tagTree(c,_TTypeConstructor);
   return 1;
}
// $(TypeName) / $(TypeArguments) #TypeConstructor
static inline int e103(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(TypeName)
      if (e25(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $(TypeArguments) #TypeConstructor
      if (e104(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(TypeName) / $(TypeArguments) #TypeConstructor"
      return 0;
   }
   return 1;
}
// ~whitespace ';' ~whitespace
static inline int e16(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 59) {
      // "CPG:1037: Expecting ';'"
      return 0;
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   return 1;
}
// ~whitespace ';' ~whitespace
static inline int p_Semicolon(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,12);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e16(c)) {
         ParserContext_memoSucc(c,12,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_Semicolon (~whitespace ';' ~whitespace)"
         c->pos = pos;
         ParserContext_memoFail(c,12);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace "->"
static inline int p_Arrow(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,45,62)) {
      // "CPG:1107: Expecting \"->\""
      return 0;
   }
   return 1;
}
// { (($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor) }
static inline int e102(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index28[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $(TypeName) / $(TypeArguments) #TypeConstructor
      temp = e103(c);
      break;
      case 2: 
      // $(TypeName)
      temp = e25(c);
      break;
      case 3: 
      // $(TypeArguments) #TypeConstructor
      temp = e104(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   return 1;
}
// { (($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor) }
static inline int pTypeConstructor(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,45);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e102(c)) {
         ParserContext_memoTreeSucc(c,45,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pTypeConstructor ({ (($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor) })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,45);
         return 0;
      }
   }
   return memo == 1;
}
// { $(TypeConstructor) ~Arrow $(Expression) #MatchCase } ~Semicolon
static inline int pMatchCase(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pTypeConstructor(c)) {
         // "CPG:1014: Expecting TypeConstructor"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   if (!p_Arrow(c)) {
      // "CPG:1014: Expecting ~Arrow"
      return 0;
   }
   {
      size_t left1 = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left1);
   }
   ParserContext_endTree(c,0,_TMatchCase,NULL, 0);
   if (!p_Semicolon(c)) {
      // "CPG:1014: Expecting ~Semicolon"
      return 0;
   }
   return 1;
}
// $(MatchCase)
static inline int e101(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pMatchCase(c)) {
      // "CPG:1014: Expecting MatchCase"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~whitespace "default" ~Arrow { $(Expression) #Default } ~Semicolon
static inline int pDefault(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match7(c,100,101,102,97,117,108,116)) {
      // "CPG:1107: Expecting \"default\""
      return 0;
   }
   if (!p_Arrow(c)) {
      // "CPG:1014: Expecting ~Arrow"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_endTree(c,0,_TDefault,NULL, 0);
   if (!p_Semicolon(c)) {
      // "CPG:1014: Expecting ~Semicolon"
      return 0;
   }
   return 1;
}
// $(Default)
static inline int e98(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pDefault(c)) {
      // "CPG:1014: Expecting Default"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~whitespace '{'
static inline int e10(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 123) {
      // "CPG:1037: Expecting '{'"
      return 0;
   }
   return 1;
}
// ~whitespace '{'
static inline int p_BlockStart(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,38);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e10(c)) {
         ParserContext_memoSucc(c,38,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_BlockStart (~whitespace '{')"
         c->pos = pos;
         ParserContext_memoFail(c,38);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace '}' ~whitespace
static inline int e15(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 125) {
      // "CPG:1037: Expecting '}'"
      return 0;
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   return 1;
}
// ~whitespace '}' ~whitespace
static inline int p_BlockEnd(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,39);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e15(c)) {
         ParserContext_memoSucc(c,39,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_BlockEnd (~whitespace '}' ~whitespace)"
         c->pos = pos;
         ParserContext_memoFail(c,39);
         return 0;
      }
   }
   return memo == 1;
}
// ~BlockStart { ($(MatchCase))* ($(Default))? #MatchBody } ~BlockEnd
static inline int pMatchBody(ParserContext * c) {
   if (!p_BlockStart(c)) {
      // "CPG:1014: Expecting ~BlockStart"
      return 0;
   }
   ParserContext_beginTree(c,0);
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(MatchCase)
      if (!e101(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         break;
      }
   }
   const unsigned char * pos3 = c->pos;
   size_t left4 = ParserContext_saveTree(c);
   size_t log5 = ParserContext_saveLog(c);
   // $(Default)
   if (!e98(c)) {
      c->pos = pos3;
      ParserContext_backTree(c,left4);
      ParserContext_backLog(c,log5);
   }
   ParserContext_endTree(c,0,_TMatchBody,NULL, 0);
   if (!p_BlockEnd(c)) {
      // "CPG:1014: Expecting ~BlockEnd"
      return 0;
   }
   return 1;
}
// ~whitespace '('
static inline int e37(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 40) {
      // "CPG:1037: Expecting '('"
      return 0;
   }
   return 1;
}
// ~whitespace '('
static inline int p_LP(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,21);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e37(c)) {
         ParserContext_memoSucc(c,21,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_LP (~whitespace '(')"
         c->pos = pos;
         ParserContext_memoFail(c,21);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace ')'
static inline int e38(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 41) {
      // "CPG:1037: Expecting ')'"
      return 0;
   }
   return 1;
}
// ~whitespace ')'
static inline int p_RP(ParserContext * c) {
   int memo = ParserContext_memoLookup(c,22);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      if (e38(c)) {
         ParserContext_memoSucc(c,22,pos);
         return 1;
      } else {
         // "CPG:891: Expecting p_RP (~whitespace ')')"
         c->pos = pos;
         ParserContext_memoFail(c,22);
         return 0;
      }
   }
   return memo == 1;
}
// ~LP { $(Expression) #NestedExpr } ~RP
static inline int e50(ParserContext * c) {
   if (!p_LP(c)) {
      // "CPG:1014: Expecting ~LP"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_endTree(c,0,_TNestedExpr,NULL, 0);
   if (!p_RP(c)) {
      // "CPG:1014: Expecting ~RP"
      return 0;
   }
   return 1;
}
// ~LP { $(Expression) #NestedExpr } ~RP
static inline int pNestedExpr(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,23);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e50(c)) {
         ParserContext_memoTreeSucc(c,23,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pNestedExpr (~LP { $(Expression) #NestedExpr } ~RP)"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,23);
         return 0;
      }
   }
   return memo == 1;
}
// ~whitespace "match" { $(NestedExpr) $(MatchBody) #MatchExpression }
static inline int e100(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match5(c,109,97,116,99,104)) {
      // "CPG:1107: Expecting \"match\""
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNestedExpr(c)) {
         // "CPG:1014: Expecting NestedExpr"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   {
      size_t left1 = ParserContext_saveTree(c);
      if (!pMatchBody(c)) {
         // "CPG:1014: Expecting MatchBody"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left1);
   }
   ParserContext_endTree(c,0,_TMatchExpression,NULL, 0);
   return 1;
}
// ~whitespace "match" { $(NestedExpr) $(MatchBody) #MatchExpression }
static inline int pMatchExpression(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,33);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e100(c)) {
         ParserContext_memoTreeSucc(c,33,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pMatchExpression (~whitespace \"match\" { $(NestedExpr) $(MatchBody) #MatchExpression })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,33);
         return 0;
      }
   }
   return memo == 1;
}
// $(MatchExpression)
static inline int e99(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pMatchExpression(c)) {
      // "CPG:1014: Expecting MatchExpression"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// "16"
static inline int e64(ParserContext * c) {
   if (!ParserContext_match2(c,49,54)) {
      // "CPG:1107: Expecting \"16\""
      return 0;
   }
   return 1;
}
// "64" #NumSuffix
static inline int e66(ParserContext * c) {
   if (!ParserContext_match2(c,54,52)) {
      // "CPG:1107: Expecting \"64\""
      return 0;
   }
   ParserContext_tagTree(c,_TNumSuffix);
   return 1;
}
// "32"
static inline int e65(ParserContext * c) {
   if (!ParserContext_match2(c,51,50)) {
      // "CPG:1107: Expecting \"32\""
      return 0;
   }
   return 1;
}
// 'f' ("16" / "32" / "64" #NumSuffix)
static inline int e63(ParserContext * c) {
   if (ParserContext_read(c) != 102) {
      // "CPG:1037: Expecting 'f'"
      return 0;
   }
   int temp = 1;
   switch(_index20[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // "16"
      temp = e64(c);
      break;
      case 2: 
      // "32"
      temp = e65(c);
      break;
      case 3: 
      // "64" #NumSuffix
      temp = e66(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting \"16\" / \"32\" / \"64\" #NumSuffix"
      return 0;
   }
   return 1;
}
// "64"
static inline int e68(ParserContext * c) {
   if (!ParserContext_match2(c,54,52)) {
      // "CPG:1107: Expecting \"64\""
      return 0;
   }
   return 1;
}
// '8'
static inline int e69(ParserContext * c) {
   if (ParserContext_read(c) != 56) {
      // "CPG:1037: Expecting '8'"
      return 0;
   }
   return 1;
}
// 'i' ("16" / "32" / "64" / '8')
static inline int e67(ParserContext * c) {
   if (ParserContext_read(c) != 105) {
      // "CPG:1037: Expecting 'i'"
      return 0;
   }
   int temp = 1;
   switch(_index21[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // "16"
      temp = e64(c);
      break;
      case 2: 
      // "32"
      temp = e65(c);
      break;
      case 3: 
      // "64"
      temp = e68(c);
      break;
      case 4: 
      // '8'
      temp = e69(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting \"16\" / \"32\" / \"64\" / '8'"
      return 0;
   }
   return 1;
}
// 'u' ("16" / "32" / "64" / '8')
static inline int e70(ParserContext * c) {
   if (ParserContext_read(c) != 117) {
      // "CPG:1037: Expecting 'u'"
      return 0;
   }
   int temp = 1;
   switch(_index21[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // "16"
      temp = e64(c);
      break;
      case 2: 
      // "32"
      temp = e65(c);
      break;
      case 3: 
      // "64"
      temp = e68(c);
      break;
      case 4: 
      // '8'
      temp = e69(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting \"16\" / \"32\" / \"64\" / '8'"
      return 0;
   }
   return 1;
}
// $(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) }))
static inline int e62(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index19[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // 'f' ("16" / "32" / "64" #NumSuffix)
      temp = e63(c);
      break;
      case 2: 
      // 'i' ("16" / "32" / "64" / '8')
      temp = e67(c);
      break;
      case 3: 
      // 'u' ("16" / "32" / "64" / '8')
      temp = e70(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting 'f' (\"16\" / \"32\" / \"64\" #NumSuffix) / 'i' (\"16\" / \"32\" / \"64\" / '8') / 'u' (\"16\" / \"32\" / \"64\" / '8')"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// [0-9] [0-9_]*
static inline int p_DecSegment(ParserContext * c) {
   if (!(48 <= ParserContext_prefetch(c) && ParserContext_read(c) < 58)) {
      // "CPG:1049: Expecting [0-9]"
      return 0;
   }
   while (_set16[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   return 1;
}
// [Ee] [+\-]? ~DecSegment
static inline int e59(ParserContext * c) {
   if (!_set17[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [Ee]"
      return 0;
   }
   if (_set18[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   if (!p_DecSegment(c)) {
      // "CPG:1014: Expecting ~DecSegment"
      return 0;
   }
   return 1;
}
// $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace))
static inline int e58(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_beginTree(c,0);
   if (!p_DecSegment(c)) {
      // "CPG:1014: Expecting ~DecSegment"
      return 0;
   }
   if (ParserContext_read(c) != 46) {
      // "CPG:1037: Expecting '.'"
      return 0;
   }
   if (!p_DecSegment(c)) {
      // "CPG:1014: Expecting ~DecSegment"
      return 0;
   }
   const unsigned char * pos = c->pos;
   // [Ee] [+\-]? ~DecSegment
   if (!e59(c)) {
      c->pos = pos;
   }
   ParserContext_endTree(c,0,_TFloatNumLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
static inline int e60(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_beginTree(c,0);
   if (!p_DecSegment(c)) {
      // "CPG:1014: Expecting ~DecSegment"
      return 0;
   }
   ParserContext_endTree(c,0,_TDecNumLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
static inline int e61(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace))
      if (e58(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
      if (e60(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))"
      return 0;
   }
   return 1;
}
// $((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace))
static inline int e55(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,48,120)) {
      // "CPG:1107: Expecting \"0x\""
      return 0;
   }
   if (!_set12[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [0-9A-Fa-f]"
      return 0;
   }
   ParserContext_beginTree(c,-3);
   while (_set13[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   ParserContext_endTree(c,0,_THexNumLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace))
static inline int e57(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,48,98)) {
      // "CPG:1107: Expecting \"0b\""
      return 0;
   }
   if (!(48 <= ParserContext_prefetch(c) && ParserContext_read(c) < 50)) {
      // "CPG:1049: Expecting [0-1]"
      return 0;
   }
   ParserContext_beginTree(c,-3);
   while (_set15[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   ParserContext_endTree(c,0,_TBinNumLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace))
static inline int e56(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,48,111)) {
      // "CPG:1107: Expecting \"0o\""
      return 0;
   }
   if (!(48 <= ParserContext_prefetch(c) && ParserContext_read(c) < 56)) {
      // "CPG:1049: Expecting [0-7]"
      return 0;
   }
   ParserContext_beginTree(c,-3);
   while (_set14[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   ParserContext_endTree(c,0,_TOctNumLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
static inline int e54(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace))
      if (e55(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace))
      if (e56(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      const unsigned char * pos7 = c->pos;
      size_t left8 = ParserContext_saveTree(c);
      size_t log9 = ParserContext_saveLog(c);
      // $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace))
      if (e57(c)) {
         temp = 0;
      } else {
         c->pos = pos7;
         ParserContext_backTree(c,left8);
         ParserContext_backLog(c,log9);
      }
   }
   if (temp) {
      const unsigned char * pos10 = c->pos;
      size_t left11 = ParserContext_saveTree(c);
      size_t log12 = ParserContext_saveLog(c);
      // $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace))
      if (e58(c)) {
         temp = 0;
      } else {
         c->pos = pos10;
         ParserContext_backTree(c,left11);
         ParserContext_backLog(c,log12);
      }
   }
   if (temp) {
      const unsigned char * pos13 = c->pos;
      size_t left14 = ParserContext_saveTree(c);
      size_t log15 = ParserContext_saveLog(c);
      // $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
      if (e60(c)) {
         temp = 0;
      } else {
         c->pos = pos13;
         ParserContext_backTree(c,left14);
         ParserContext_backLog(c,log15);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $((~whitespace \"0x\" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace \"0o\" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace \"0b\" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))"
      return 0;
   }
   return 1;
}
// { (($((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))) / ($((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace)))) ($(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) })))? #NumLiteral }
static inline int e53(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index11[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
      temp = e54(c);
      break;
      case 2: 
      // $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))
      temp = e61(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($((~whitespace \"0x\" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace \"0o\" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace \"0b\" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))) / ($((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace)))"
      return 0;
   }
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) }))
   if (!e62(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TNumLiteral,NULL, 0);
   return 1;
}
// { (($((~whitespace "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace "0o" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace "0b" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))) / ($((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace)))) ($(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) })))? #NumLiteral }
static inline int pNumLiteral(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,7);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e53(c)) {
         ParserContext_memoTreeSucc(c,7,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pNumLiteral ({ (($((~whitespace \"0x\" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~whitespace)) / $((~whitespace \"0o\" [0-7] { [0-7_]* #OctNumLiteral } ~whitespace)) / $((~whitespace \"0b\" [0-1] { [0-1_]* #BinNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace))) / ($((~whitespace { ~DecSegment '.' ~DecSegment ([Ee] [+\\-]? ~DecSegment)? #FloatNumLiteral } ~whitespace)) / $((~whitespace { ~DecSegment #DecNumLiteral } ~whitespace)))) ($(({ ('f' (\"16\" / \"32\" / \"64\" #NumSuffix) / 'i' (\"16\" / \"32\" / \"64\" / '8') / 'u' (\"16\" / \"32\" / \"64\" / '8')) })))? #NumLiteral })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,7);
         return 0;
      }
   }
   return memo == 1;
}
// $(NumLiteral)
static inline int e52(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pNumLiteral(c)) {
      // "CPG:1014: Expecting NumLiteral"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// '"'
static inline int e79(ParserContext * c) {
   if (ParserContext_read(c) != 34) {
      // "CPG:1037: Expecting '\"'"
      return 0;
   }
   return 1;
}
// '"' / ''
static inline int e78(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      // '"'
      if (e79(c)) {
         temp = 0;
      } else {
         c->pos = pos;
      }
   }
   if (temp) {
      const unsigned char * pos2 = c->pos;
      // ''
      if (e6(c)) {
         temp = 0;
      } else {
         c->pos = pos2;
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of '\"' / ''"
      return 0;
   }
   return 1;
}
// '\\' ('' / ('"' / ''))
static inline int e77(ParserContext * c) {
   if (ParserContext_read(c) != 92) {
      // "CPG:1037: Expecting '\\\\'"
      return 0;
   }
   int temp = 1;
   switch(_index25[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // ''
      temp = e6(c);
      break;
      case 2: 
      // '"' / ''
      temp = e78(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting '' / ('\"' / '')"
      return 0;
   }
   return 1;
}
// [\x01-!#-\xff]
static inline int e76(ParserContext * c) {
   if (!_set24[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [\\x01-!#-\\xff]"
      return 0;
   }
   return 1;
}
// [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
static inline int e75(ParserContext * c) {
   int temp = 1;
   switch(_index23[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // [\x01-!#-\xff]
      temp = e76(c);
      break;
      case 2: 
      // '\\' ('' / ('"' / ''))
      temp = e77(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting [\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / ''))"
      return 0;
   }
   return 1;
}
// $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace))
static inline int e74(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,70,34)) {
      // "CPG:1107: Expecting \"F\\\"\""
      return 0;
   }
   ParserContext_beginTree(c,0);
   while (1) {
      const unsigned char * pos = c->pos;
      // [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
      if (!e75(c)) {
         c->pos = pos;
         break;
      }
   }
   ParserContext_endTree(c,0,_TInterpolatedStringLiteral,NULL, 0);
   if (ParserContext_read(c) != 34) {
      // "CPG:1037: Expecting '\"'"
      return 0;
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace))
static inline int e81(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 34) {
      // "CPG:1037: Expecting '\"'"
      return 0;
   }
   ParserContext_beginTree(c,0);
   while (1) {
      const unsigned char * pos = c->pos;
      // [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
      if (!e75(c)) {
         c->pos = pos;
         break;
      }
   }
   ParserContext_endTree(c,0,_TStringSegment,NULL, 0);
   if (ParserContext_read(c) != 34) {
      // "CPG:1037: Expecting '\"'"
      return 0;
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral
static inline int e80(ParserContext * c) {
   if (!e81(c)) {
      // "CPG:1276: Expecting ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+"
      return 0;
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace))
      if (!e81(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         break;
      }
   }
   ParserContext_tagTree(c,_TStringLiteral);
   return 1;
}
// $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace)) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral
static inline int e73(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace))
      if (e74(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral
      if (e80(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $((~whitespace \"F\\\"\" { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #InterpolatedStringLiteral } '\"' ~whitespace)) / ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+ #StringLiteral"
      return 0;
   }
   return 1;
}
// { (($((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace)) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral / $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace))) }
static inline int e72(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index22[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace)) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral
      temp = e73(c);
      break;
      case 2: 
      // ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral
      temp = e80(c);
      break;
      case 3: 
      // $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace))
      temp = e74(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($((~whitespace \"F\\\"\" { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #InterpolatedStringLiteral } '\"' ~whitespace)) / ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+ #StringLiteral) / ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+ #StringLiteral / $((~whitespace \"F\\\"\" { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #InterpolatedStringLiteral } '\"' ~whitespace))"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   return 1;
}
// { (($((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace)) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral) / ($((~whitespace '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~whitespace)))+ #StringLiteral / $((~whitespace "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~whitespace))) }
static inline int pStringLiteral(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,10);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e72(c)) {
         ParserContext_memoTreeSucc(c,10,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pStringLiteral ({ (($((~whitespace \"F\\\"\" { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #InterpolatedStringLiteral } '\"' ~whitespace)) / ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+ #StringLiteral) / ($((~whitespace '\"' { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #StringSegment } '\"' ~whitespace)))+ #StringLiteral / $((~whitespace \"F\\\"\" { ([\\x01-!#-\\xff] / '\\\\' ('' / ('\"' / '')))* #InterpolatedStringLiteral } '\"' ~whitespace))) })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,10);
         return 0;
      }
   }
   return memo == 1;
}
// $(StringLiteral) #LiteralValue
static inline int e150(ParserContext * c) {
   {
      size_t left = ParserContext_saveTree(c);
      if (!pStringLiteral(c)) {
         // "CPG:1014: Expecting StringLiteral"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_tagTree(c,_TLiteralValue);
   return 1;
}
// $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace))
static inline int e149(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!_set32[ParserContext_read(c)]) {
      // "CPG:1049: Expecting ['\\\\]"
      return 0;
   }
   if (!_set33[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [\\x01-[\\]-\\xff]"
      return 0;
   }
   if (!_set32[ParserContext_read(c)]) {
      // "CPG:1049: Expecting ['\\\\]"
      return 0;
   }
   ParserContext_beginTree(c,-3);
   ParserContext_endTree(c,0,_TCharLiteral,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(NumLiteral) / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue
static inline int e148(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(NumLiteral)
      if (e52(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace))
      if (e149(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      const unsigned char * pos7 = c->pos;
      size_t left8 = ParserContext_saveTree(c);
      size_t log9 = ParserContext_saveLog(c);
      // $(StringLiteral) #LiteralValue
      if (e150(c)) {
         temp = 0;
      } else {
         c->pos = pos7;
         ParserContext_backTree(c,left8);
         ParserContext_backLog(c,log9);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(NumLiteral) / $((~whitespace ['\\\\] [\\x01-[\\]-\\xff] ['\\\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue"
      return 0;
   }
   return 1;
}
// $(({ (($(NumLiteral) / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(NumLiteral)) }))
static inline int e147(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index31[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $(NumLiteral) / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue
      temp = e148(c);
      break;
      case 2: 
      // $(StringLiteral) #LiteralValue
      temp = e150(c);
      break;
      case 3: 
      // $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace))
      temp = e149(c);
      break;
      case 4: 
      // $(NumLiteral)
      temp = e52(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($(NumLiteral) / $((~whitespace ['\\\\] [\\x01-[\\]-\\xff] ['\\\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~whitespace ['\\\\] [\\x01-[\\]-\\xff] ['\\\\] { #CharLiteral } ~whitespace)) / $(NumLiteral)"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// { $(NumLiteral) ~whitespace ".." ($(NumLiteral))? #OpenRange }
static inline int e128(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNumLiteral(c)) {
         // "CPG:1014: Expecting NumLiteral"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (!ParserContext_match2(c,46,46)) {
      // "CPG:1107: Expecting \"..\""
      return 0;
   }
   const unsigned char * pos = c->pos;
   size_t left2 = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(NumLiteral)
   if (!e52(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left2);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TOpenRange,NULL, 0);
   return 1;
}
// { $(NumLiteral) ~whitespace ".." ($(NumLiteral))? #OpenRange }
static inline int pOpenRange(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,47);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e128(c)) {
         ParserContext_memoTreeSucc(c,47,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pOpenRange ({ $(NumLiteral) ~whitespace \"..\" ($(NumLiteral))? #OpenRange })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,47);
         return 0;
      }
   }
   return memo == 1;
}
// $(OpenRange) #Iterable
static inline int e132(ParserContext * c) {
   {
      size_t left = ParserContext_saveTree(c);
      if (!pOpenRange(c)) {
         // "CPG:1014: Expecting OpenRange"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_tagTree(c,_TIterable);
   return 1;
}
// ~whitespace '|'
static inline int p_BitOr(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 124) {
      // "CPG:1037: Expecting '|'"
      return 0;
   }
   return 1;
}
// ~BitOr $(Expression)
static inline int e131(ParserContext * c) {
   if (!p_BitOr(c)) {
      // "CPG:1014: Expecting ~BitOr"
      return 0;
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   return 1;
}
// ~Arrow { $(Expression) (~BitOr $(Expression))* #ComprPipeline }
static inline int pComprPipeline(ParserContext * c) {
   if (!p_Arrow(c)) {
      // "CPG:1014: Expecting ~Arrow"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // ~BitOr $(Expression)
      if (!e131(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
   }
   ParserContext_endTree(c,0,_TComprPipeline,NULL, 0);
   return 1;
}
// ~whitespace ':'
static inline int p_Colon(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 58) {
      // "CPG:1037: Expecting ':'"
      return 0;
   }
   return 1;
}
// { $(Name) ~Colon $(OpenRange) #NamedRange }
static inline int pNamedRange(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pName(c)) {
         // "CPG:1014: Expecting Name"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   if (!p_Colon(c)) {
      // "CPG:1014: Expecting ~Colon"
      return 0;
   }
   {
      size_t left1 = ParserContext_saveTree(c);
      if (!pOpenRange(c)) {
         // "CPG:1014: Expecting OpenRange"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left1);
   }
   ParserContext_endTree(c,0,_TNamedRange,NULL, 0);
   return 1;
}
// ~whitespace '|'
static inline int e130(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 124) {
      // "CPG:1037: Expecting '|'"
      return 0;
   }
   return 1;
}
// (~whitespace ',' / ~whitespace '|') $(NamedRange)
static inline int e129(ParserContext * c) {
   {
      int temp = 1;
      if (temp) {
         const unsigned char * pos = c->pos;
         // ~whitespace ','
         if (e14(c)) {
            temp = 0;
         } else {
            c->pos = pos;
         }
      }
      if (temp) {
         const unsigned char * pos2 = c->pos;
         // ~whitespace '|'
         if (e130(c)) {
            temp = 0;
         } else {
            c->pos = pos2;
         }
      }
      if (temp) {
         // "CPG:1158: Expecting one of ~whitespace ',' / ~whitespace '|'"
         return 0;
      }
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNamedRange(c)) {
         // "CPG:1014: Expecting NamedRange"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   return 1;
}
// ~LSqB { $(NamedRange) ((~whitespace ',' / ~whitespace '|') $(NamedRange))* $(ComprPipeline) #Comprehension } ~RSqB
static inline int pComprehension(ParserContext * c) {
   if (!p_LSqB(c)) {
      // "CPG:1014: Expecting ~LSqB"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNamedRange(c)) {
         // "CPG:1014: Expecting NamedRange"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // (~whitespace ',' / ~whitespace '|') $(NamedRange)
      if (!e129(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
   }
   {
      size_t left4 = ParserContext_saveTree(c);
      if (!pComprPipeline(c)) {
         // "CPG:1014: Expecting ComprPipeline"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left4);
   }
   ParserContext_endTree(c,0,_TComprehension,NULL, 0);
   if (!p_RSqB(c)) {
      // "CPG:1014: Expecting ~RSqB"
      return 0;
   }
   return 1;
}
// $(Comprehension)
static inline int e127(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pComprehension(c)) {
      // "CPG:1014: Expecting Comprehension"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(Comprehension) / $(OpenRange) #Iterable
static inline int e126(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(Comprehension)
      if (e127(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $(OpenRange) #Iterable
      if (e132(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(Comprehension) / $(OpenRange) #Iterable"
      return 0;
   }
   return 1;
}
// { (($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)) }
static inline int e125(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index29[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $(Comprehension) / $(OpenRange) #Iterable
      temp = e126(c);
      break;
      case 2: 
      // $(OpenRange) #Iterable
      temp = e132(c);
      break;
      case 3: 
      // $(Comprehension)
      temp = e127(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   return 1;
}
// { (($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)) }
static inline int pIterable(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,34);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e125(c)) {
         ParserContext_memoTreeSucc(c,34,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pIterable ({ (($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)) })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,34);
         return 0;
      }
   }
   return memo == 1;
}
// $(Iterable)
static inline int e146(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pIterable(c)) {
      // "CPG:1014: Expecting Iterable"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~Comma $(Expression)
static inline int e95(ParserContext * c) {
   if (!p_Comma(c)) {
      // "CPG:1014: Expecting ~Comma"
      return 0;
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   return 1;
}
// { $(Expression) (~Comma $(Expression))* #ExpressionList }
static inline int e94(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // ~Comma $(Expression)
      if (!e95(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
   }
   ParserContext_endTree(c,0,_TExpressionList,NULL, 0);
   return 1;
}
// { $(Expression) (~Comma $(Expression))* #ExpressionList }
static inline int pExpressionList(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,44);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e94(c)) {
         ParserContext_memoTreeSucc(c,44,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pExpressionList ({ $(Expression) (~Comma $(Expression))* #ExpressionList })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,44);
         return 0;
      }
   }
   return memo == 1;
}
// { $(ExpressionList) #ArrayElements } ~Comma?
static inline int pArrayElements(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpressionList(c)) {
         // "CPG:1014: Expecting ExpressionList"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_endTree(c,0,_TArrayElements,NULL, 0);
   const unsigned char * pos = c->pos;
   // ~Comma
   if (!p_Comma(c)) {
      c->pos = pos;
   }
   return 1;
}
// $(ArrayElements)
static inline int e139(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pArrayElements(c)) {
      // "CPG:1014: Expecting ArrayElements"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~LSqB { ($(ArrayElements))? #ArrayConstruction } ~RSqB
static inline int pArrayConstruction(ParserContext * c) {
   if (!p_LSqB(c)) {
      // "CPG:1014: Expecting ~LSqB"
      return 0;
   }
   ParserContext_beginTree(c,0);
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(ArrayElements)
   if (!e139(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TArrayConstruction,NULL, 0);
   if (!p_RSqB(c)) {
      // "CPG:1014: Expecting ~RSqB"
      return 0;
   }
   return 1;
}
// $(ArrayConstruction)
static inline int e138(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pArrayConstruction(c)) {
      // "CPG:1014: Expecting ArrayConstruction"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(ArrayConstruction) / $(Iterable)
static inline int e155(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(ArrayConstruction)
      if (e138(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $(Iterable)
      if (e146(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(ArrayConstruction) / $(Iterable)"
      return 0;
   }
   return 1;
}
// ~LSqB { $(Expression) #Index } ~RSqB
static inline int pIndex(ParserContext * c) {
   if (!p_LSqB(c)) {
      // "CPG:1014: Expecting ~LSqB"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_endTree(c,0,_TIndex,NULL, 0);
   if (!p_RSqB(c)) {
      // "CPG:1014: Expecting ~RSqB"
      return 0;
   }
   return 1;
}
// $(Index) #Invoke
static inline int e97(ParserContext * c) {
   {
      size_t left = ParserContext_saveTree(c);
      if (!pIndex(c)) {
         // "CPG:1014: Expecting Index"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_tagTree(c,_TInvoke);
   return 1;
}
// ~whitespace !"F\"" [$@-Z_a-z] { [$.0-9A-Z_a-z]* #QualifiedName } ~whitespace
static inline int e8(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_match2(c,70,34)) {
      // "CPG:1555: Expecting not F\""
      return 0;
   }
   if (!_set3[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [$@-Z_a-z]"
      return 0;
   }
   ParserContext_beginTree(c,-1);
   while (_set4[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   ParserContext_endTree(c,0,_TQualifiedName,NULL, 0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   return 1;
}
// ~whitespace !"F\"" [$@-Z_a-z] { [$.0-9A-Z_a-z]* #QualifiedName } ~whitespace
static inline int pQualifiedName(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,9);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e8(c)) {
         ParserContext_memoTreeSucc(c,9,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pQualifiedName (~whitespace !\"F\\\"\" [$@-Z_a-z] { [$.0-9A-Z_a-z]* #QualifiedName } ~whitespace)"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,9);
         return 0;
      }
   }
   return memo == 1;
}
// { ~whitespace '.' $(QualifiedName) ($(Invoke))? #MethodCall }
static inline int pMethodCall(ParserContext * c) {
   ParserContext_beginTree(c,0);
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 46) {
      // "CPG:1037: Expecting '.'"
      return 0;
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pQualifiedName(c)) {
         // "CPG:1014: Expecting QualifiedName"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   const unsigned char * pos = c->pos;
   size_t left2 = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(Invoke)
   if (!e85(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left2);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TMethodCall,NULL, 0);
   return 1;
}
// $(MethodCall)
static inline int e96(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pMethodCall(c)) {
      // "CPG:1014: Expecting MethodCall"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// { $(Name) ~whitespace '=' $(Expression) #NamedAssignment }
static inline int pNamedAssignment(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pName(c)) {
         // "CPG:1014: Expecting Name"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   if (ParserContext_read(c) != 61) {
      // "CPG:1037: Expecting '='"
      return 0;
   }
   {
      size_t left1 = ParserContext_saveTree(c);
      if (!pExpression(c)) {
         // "CPG:1014: Expecting Expression"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left1);
   }
   ParserContext_endTree(c,0,_TNamedAssignment,NULL, 0);
   return 1;
}
// ~Comma $(NamedAssignment)
static inline int e91(ParserContext * c) {
   if (!p_Comma(c)) {
      // "CPG:1014: Expecting ~Comma"
      return 0;
   }
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNamedAssignment(c)) {
         // "CPG:1014: Expecting NamedAssignment"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   return 1;
}
// { $(NamedAssignment) (~Comma $(NamedAssignment))* #NamedAssignmentList }
static inline int pNamedAssignmentList(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNamedAssignment(c)) {
         // "CPG:1014: Expecting NamedAssignment"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // ~Comma $(NamedAssignment)
      if (!e91(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
   }
   ParserContext_endTree(c,0,_TNamedAssignmentList,NULL, 0);
   return 1;
}
// ~LP { $(NamedAssignmentList) #NamedCallParameters } ~RP
static inline int pNamedCallParameters(ParserContext * c) {
   if (!p_LP(c)) {
      // "CPG:1014: Expecting ~LP"
      return 0;
   }
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pNamedAssignmentList(c)) {
         // "CPG:1014: Expecting NamedAssignmentList"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   ParserContext_endTree(c,0,_TNamedCallParameters,NULL, 0);
   if (!p_RP(c)) {
      // "CPG:1014: Expecting ~RP"
      return 0;
   }
   return 1;
}
// $(NamedCallParameters)
static inline int e90(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pNamedCallParameters(c)) {
      // "CPG:1014: Expecting NamedCallParameters"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(ExpressionList) ($(OptionalEllipsis))?
static inline int e93(ParserContext * c) {
   {
      size_t left = ParserContext_saveTree(c);
      if (!pExpressionList(c)) {
         // "CPG:1014: Expecting ExpressionList"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   const unsigned char * pos = c->pos;
   size_t left2 = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(OptionalEllipsis)
   if (!e30(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left2);
      ParserContext_backLog(c,log);
   }
   return 1;
}
// ~LP { ($(ExpressionList) ($(OptionalEllipsis))?)? #AnonymousCallParameters } ~RP
static inline int pAnonymousCallParameters(ParserContext * c) {
   if (!p_LP(c)) {
      // "CPG:1014: Expecting ~LP"
      return 0;
   }
   ParserContext_beginTree(c,0);
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(ExpressionList) ($(OptionalEllipsis))?
   if (!e93(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   ParserContext_endTree(c,0,_TAnonymousCallParameters,NULL, 0);
   if (!p_RP(c)) {
      // "CPG:1014: Expecting ~RP"
      return 0;
   }
   return 1;
}
// $(AnonymousCallParameters)
static inline int e92(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pAnonymousCallParameters(c)) {
      // "CPG:1014: Expecting AnonymousCallParameters"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// { ($(TypeArguments))? (($(NamedCallParameters) / $(AnonymousCallParameters))) #CallParameters }
static inline int e89(ParserContext * c) {
   ParserContext_beginTree(c,0);
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(TypeArguments)
   if (!e27(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   {
      int temp = 1;
      if (temp) {
         const unsigned char * pos4 = c->pos;
         size_t left5 = ParserContext_saveTree(c);
         size_t log6 = ParserContext_saveLog(c);
         // $(NamedCallParameters)
         if (e90(c)) {
            temp = 0;
         } else {
            c->pos = pos4;
            ParserContext_backTree(c,left5);
            ParserContext_backLog(c,log6);
         }
      }
      if (temp) {
         const unsigned char * pos7 = c->pos;
         size_t left8 = ParserContext_saveTree(c);
         size_t log9 = ParserContext_saveLog(c);
         // $(AnonymousCallParameters)
         if (e92(c)) {
            temp = 0;
         } else {
            c->pos = pos7;
            ParserContext_backTree(c,left8);
            ParserContext_backLog(c,log9);
         }
      }
      if (temp) {
         // "CPG:1158: Expecting one of $(NamedCallParameters) / $(AnonymousCallParameters)"
         return 0;
      }
   }
   ParserContext_endTree(c,0,_TCallParameters,NULL, 0);
   return 1;
}
// { ($(TypeArguments))? (($(NamedCallParameters) / $(AnonymousCallParameters))) #CallParameters }
static inline int pCallParameters(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,43);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e89(c)) {
         ParserContext_memoTreeSucc(c,43,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pCallParameters ({ ($(TypeArguments))? (($(NamedCallParameters) / $(AnonymousCallParameters))) #CallParameters })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,43);
         return 0;
      }
   }
   return memo == 1;
}
// $(CallParameters)
static inline int e88(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pCallParameters(c)) {
      // "CPG:1014: Expecting CallParameters"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// $(CallParameters) / $(MethodCall) / $(Index) #Invoke
static inline int e87(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(CallParameters)
      if (e88(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $(MethodCall)
      if (e96(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      const unsigned char * pos7 = c->pos;
      size_t left8 = ParserContext_saveTree(c);
      size_t log9 = ParserContext_saveLog(c);
      // $(Index) #Invoke
      if (e97(c)) {
         temp = 0;
      } else {
         c->pos = pos7;
         ParserContext_backTree(c,left8);
         ParserContext_backLog(c,log9);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(CallParameters) / $(MethodCall) / $(Index) #Invoke"
      return 0;
   }
   return 1;
}
// { (($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke) }
static inline int e86(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   switch(_index26[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // $(CallParameters) / $(MethodCall) / $(Index) #Invoke
      temp = e87(c);
      break;
      case 2: 
      // $(CallParameters)
      temp = e88(c);
      break;
      case 3: 
      // $(MethodCall)
      temp = e96(c);
      break;
      case 4: 
      // $(Index) #Invoke
      temp = e97(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting ($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke"
      return 0;
   }
   ParserContext_endTree(c,0,_T,NULL, 0);
   return 1;
}
// { (($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke) }
static inline int pInvoke(ParserContext * c) {
   int memo = ParserContext_memoLookupTree(c,32);
   if (memo == 0) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      if (e86(c)) {
         ParserContext_memoTreeSucc(c,32,pos);
         return 1;
      } else {
         // "CPG:891: Expecting pInvoke ({ (($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke) })"
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         ParserContext_memoFail(c,32);
         return 0;
      }
   }
   return memo == 1;
}
// $(Invoke)
int e85(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pInvoke(c)) {
      // "CPG:1014: Expecting Invoke"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// "++"
static inline int e157(ParserContext * c) {
   if (!ParserContext_match2(c,43,43)) {
      // "CPG:1107: Expecting \"++\""
      return 0;
   }
   return 1;
}
// "--"
static inline int e158(ParserContext * c) {
   if (!ParserContext_match2(c,45,45)) {
      // "CPG:1107: Expecting \"--\""
      return 0;
   }
   return 1;
}
// ~whitespace ("++" / "--")
static inline int e156(ParserContext * c) {
   if (!p_whitespace(c)) {
      // "CPG:1014: Expecting ~whitespace"
      return 0;
   }
   int temp = 1;
   switch(_index34[ParserContext_prefetch(c)]) {
      case 0: 
      // "CPG:1175: Expecting not being here"
      return 0;
      case 1: 
      // "++"
      temp = e157(c);
      break;
      case 2: 
      // "--"
      temp = e158(c);
      break;
   }
   if (!temp) {
      // "CPG:1194: Expecting \"++\" / \"--\""
      return 0;
   }
   return 1;
}
// $(Iterable) / $(({ (($(NumLiteral) / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(NumLiteral)) }))
static inline int e153(ParserContext * c) {
   int temp = 1;
   if (temp) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(Iterable)
      if (e146(c)) {
         temp = 0;
      } else {
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
      }
   }
   if (temp) {
      const unsigned char * pos4 = c->pos;
      size_t left5 = ParserContext_saveTree(c);
      size_t log6 = ParserContext_saveLog(c);
      // $(({ (($(NumLiteral) / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~whitespace ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~whitespace)) / $(NumLiteral)) }))
      if (e147(c)) {
         temp = 0;
      } else {
         c->pos = pos4;
         ParserContext_backTree(c,left5);
         ParserContext_backLog(c,log6);
      }
   }
   if (temp) {
      // "CPG:1158: Expecting one of $(Iterable) / $(({ (($(NumLiteral) / $((~whitespace ['\\\\] [\\x01-[\\]-\\xff] ['\\\\] { #CharLiteral } ~whitespace)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~whitespace ['\\\\] [\\x01-[\\]-\\xff] ['\\\\] { #CharLiteral } ~whitespace)) / $(NumLiteral)) }))"
      return 0;
   }
   return 1;
}
// $(QualifiedName)
static inline int e145(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pQualifiedName(c)) {
      // "CPG:1014: Expecting QualifiedName"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// { ($(TypeName))? $(CallParameters) #MultiparamLambda } ~Arrow
static inline int pMultiparamLambda(ParserContext * c) {
   ParserContext_beginTree(c,0);
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(TypeName)
   if (!e25(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   {
      size_t left3 = ParserContext_saveTree(c);
      if (!pCallParameters(c)) {
         // "CPG:1014: Expecting CallParameters"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left3);
   }
   ParserContext_endTree(c,0,_TMultiparamLambda,NULL, 0);
   if (!p_Arrow(c)) {
      // "CPG:1014: Expecting ~Arrow"
      return 0;
   }
   return 1;
}
// $(MultiparamLambda)
static inline int e144(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pMultiparamLambda(c)) {
      // "CPG:1014: Expecting MultiparamLambda"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// { $(QualifiedName) ($(Invoke))* #ValueReference } ~BitOr
static inline int pValueReference(ParserContext * c) {
   ParserContext_beginTree(c,0);
   {
      size_t left = ParserContext_saveTree(c);
      if (!pQualifiedName(c)) {
         // "CPG:1014: Expecting QualifiedName"
         return 0;
      }
      ParserContext_linkTree(c,_L);
      ParserContext_backTree(c,left);
   }
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left2 = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // $(Invoke)
      if (!e85(c)) {
         c->pos = pos;
         ParserContext_backTree(c,left2);
         ParserContext_backLog(c,log);
         break;
      }
      if (pos == c->pos) {
         break;
      }
   }
   ParserContext_endTree(c,0,_TValueReference,NULL, 0);
   if (!p_BitOr(c)) {
      // "CPG:1014: Expecting ~BitOr"
      return 0;
   }
   return 1;
}
// $(ValueReference)
static inline int e84(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   if (!pValueReference(c)) {
      // "CPG:1014: Expecting ValueReference"
      return 0;
   }
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}
// ~BlockStart { ($(ValueReference))? $(NamedAssignmentList) #ObjConstructionBody } ~BlockEnd
static inline int pObjConstructionBody(ParserContext * c) {
   if (!p_BlockStart(c)) {
      // "CPG:1014: Expecting ~BlockStart"
      return 0;
   }
   ParserContext_beginTree(c,0);
   const unsigned char * pos = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(ValueReference)
   if (!e84(c)) {
      c->pos = pos;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
