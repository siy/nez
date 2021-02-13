
// 
// email grammar parser.
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
static const unsigned char _set0[256] = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
static int _TEmail = 1;
static int _TList = 2;
static const char * _tags[3] = {"","Email","List"};
static const char * _labels[1] = {""};
static const char * _tables[1] = {""};
// Prototypes
int p_LOCAL(ParserContext *c);

// 
// Function which matches following rule: '.' ~LOCAL
// 
static inline int e2(ParserContext * c) {
   if (ParserContext_read(c) != 46) {
      // "CPG:1037: Expecting '.'"
      ParserContext_reportError(c,"CPG:1037: Expecting '.'");
      return 0;
   }
   if (!p_LOCAL(c)) {
      // "CPG:1014: Expecting ~LOCAL"
      ParserContext_reportError(c,"CPG:1014: Expecting ~LOCAL");
      return 0;
   }
   return 1;
}

// 
// Function which matches following rule: [\-0-9A-Za-z]+ ('.' ~LOCAL)?
// 
int p_LOCAL(ParserContext * c) {
   if (!_set0[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [\\-0-9A-Za-z]"
      ParserContext_reportError(c,"CPG:1049: Expecting [\\-0-9A-Za-z]");
      return 0;
   }
   while (_set0[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   const unsigned char * pos = c->pos;
   // '.' ~LOCAL
   if (!e2(c)) {
      c->pos = pos;
   }
   return 1;
}

// 
// Function which matches following rule: '.' [\-0-9A-Za-z]+
// 
static inline int e3(ParserContext * c) {
   if (ParserContext_read(c) != 46) {
      // "CPG:1037: Expecting '.'"
      ParserContext_reportError(c,"CPG:1037: Expecting '.'");
      return 0;
   }
   if (!_set0[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [\\-0-9A-Za-z]"
      ParserContext_reportError(c,"CPG:1049: Expecting [\\-0-9A-Za-z]");
      return 0;
   }
   while (_set0[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   return 1;
}

// 
// Function which matches following rule: ~LOCAL '@' [\-0-9A-Za-z]+ ('.' [\-0-9A-Za-z]+)+
// 
static inline int p_EMAIL(ParserContext * c) {
   if (!p_LOCAL(c)) {
      // "CPG:1014: Expecting ~LOCAL"
      ParserContext_reportError(c,"CPG:1014: Expecting ~LOCAL");
      return 0;
   }
   if (ParserContext_read(c) != 64) {
      // "CPG:1037: Expecting '@'"
      ParserContext_reportError(c,"CPG:1037: Expecting '@'");
      return 0;
   }
   if (!_set0[ParserContext_read(c)]) {
      // "CPG:1049: Expecting [\\-0-9A-Za-z]"
      ParserContext_reportError(c,"CPG:1049: Expecting [\\-0-9A-Za-z]");
      return 0;
   }
   while (_set0[ParserContext_prefetch(c)]) {
      ParserContext_move(c,1);
   }
   if (!e3(c)) {
      // "CPG:1276: Expecting ('.' [\\-0-9A-Za-z]+)+"
      ParserContext_reportError(c,"CPG:1276: Expecting ('.' [\\-0-9A-Za-z]+)+");
      return 0;
   }
   int temp = 1;
   while (1) {
      const unsigned char * pos = c->pos;
      // '.' [\-0-9A-Za-z]+
      if (!e3(c)) {
         // from CPG:1233, from visitOneMore
         if (temp) {
            // "CPG:1251: Expecting  at least one e3(c) ('.' [\\-0-9A-Za-z]+)"
            ParserContext_reportError(c,"CPG:1251: Expecting  at least one e3(c) ('.' [\\-0-9A-Za-z]+)");
         }
         c->pos = pos;
         break;
      } else {
         temp = 0;
      }
   }
   return 1;
}

// 
// Function which matches following rule: !~EMAIL .
// 
static inline int e1(ParserContext * c) {
   {
      const unsigned char * pos = c->pos;
      // ~EMAIL
      if (p_EMAIL(c)) {
         // "CPG:1328: Expecting !~EMAIL"
         ParserContext_reportError(c,"CPG:1328: Expecting !~EMAIL");
         return 0;
      }
      // from CPG:1322
      c->pos = pos;
   }
   if (ParserContext_read(c) == 0) {
      // "CPG:1096: Expecting ."
      ParserContext_reportError(c,"CPG:1096: Expecting .");
      return 0;
   }
   return 1;
}

// 
// Function which matches following rule: $(({ ~EMAIL #Email }))
// 
static inline int e4(ParserContext * c) {
   size_t left = ParserContext_saveTree(c);
   ParserContext_beginTree(c,0);
   if (!p_EMAIL(c)) {
      // "CPG:1014: Expecting ~EMAIL"
      ParserContext_reportError(c,"CPG:1014: Expecting ~EMAIL");
      return 0;
   }
   ParserContext_endTree(c,0,_TEmail,NULL, 0);
   ParserContext_linkTree(c,_L);
   ParserContext_backTree(c,left);
   return 1;
}

// 
// Function which matches following rule: (!~EMAIL .)* ($(({ ~EMAIL #Email })))?
// 
static inline int e0(ParserContext * c) {
   int temp = 1;
   while (1) {
      const unsigned char * pos = c->pos;
      // !~EMAIL .
      if (!e1(c)) {
         // from CPG:1233, from visitZeroMore
         c->pos = pos;
         break;
      } else {
         temp = 0;
      }
   }
   const unsigned char * pos2 = c->pos;
   size_t left = ParserContext_saveTree(c);
   size_t log = ParserContext_saveLog(c);
   // $(({ ~EMAIL #Email }))
   if (!e4(c)) {
      c->pos = pos2;
      ParserContext_backTree(c,left);
      ParserContext_backLog(c,log);
   }
   return 1;
}

// 
// Function which matches following rule: { ((!~EMAIL .)* ($(({ ~EMAIL #Email })))?)* #List }
// 
static inline int pFile(ParserContext * c) {
   ParserContext_beginTree(c,0);
   int temp = 1;
   while (1) {
      const unsigned char * pos = c->pos;
      size_t left = ParserContext_saveTree(c);
      size_t log = ParserContext_saveLog(c);
      // (!~EMAIL .)* ($(({ ~EMAIL #Email })))?
      if (!e0(c)) {
         // from CPG:1233, from visitZeroMore
         c->pos = pos;
         ParserContext_backTree(c,left);
         ParserContext_backLog(c,log);
         break;
      } else {
         temp = 0;
      }
      if (pos == c->pos) {
         break;
      }
   }
   ParserContext_endTree(c,0,_TList,NULL, 0);
   return 1;
}
void cnez_dump(Tree* t, FILE *fp, int depth)
{
    size_t i;
    if(t == NULL) {
        fputs("null", fp);
        return;
    }
    /*
    if(t->refc != 1) {
        fprintf(fp, "@%ld", t->refc);
    }
    */
    fputs("\n", fp);
    for(i = 0; i < depth; i++) {
        fputs(" ", fp);
    }

    fputs("[#", fp);
    fputs(_tags[t->tag], fp);
    if(t->size == 0) {
        fputs(" '", fp);
        for(i = 0; i < t->len; i++) {
            fputc(t->text[i], fp);
        }
        fputs("'", fp);
    }
    else {
        for(i = 0; i < t->size; i++) {
            fputs(" ", fp);
            if(t->labels[i] != 0) {
                fputs("$", fp);
                fputs(_labels[t->labels[i]], fp);
                fputs("=", fp);
            }
            cnez_dump(t->childs[i], fp, depth + 1);
        }
    }
    fputs("]", fp);
}

#ifndef UNUSE_MAIN
#include<sys/time.h> // for using gettimeofday

#define NUM_BENCH_RUNS 1000

static char *get_input(const char *path, size_t *size)
{
    FILE *fp = fopen(path, "rb");

    if(fp != NULL) {
        size_t len;
        fseek(fp, 0, SEEK_END);
        len = (size_t) ftell(fp);
        fseek(fp, 0, SEEK_SET);
        char *buf = (char *) calloc(1, len + 1);
        size_t readed = fread(buf, 1, len, fp);
        if(readed != len) {
            fprintf(stderr, "read error: %s\n", path);
            exit(1);
        }
        fclose(fp);
        *size = len;
        return buf;
    }

    return NULL;
}

/* calculate time difference in us */
static double timediff(struct timeval *s, struct timeval *e)
{
    double t1 = (e->tv_sec - s->tv_sec) * 1000000.0;
    double t2 = (e->tv_usec - s->tv_usec);
    return t1 + t2;
}

int run_bench(const char* name, const char *input, int len, void* (*parse)(const char *input, size_t len)) {
    double tsum = 0.0;
    double t[NUM_BENCH_RUNS];

    int i = 0;
    for(i = 0; i < NUM_BENCH_RUNS; i++) {
        struct timeval s, e;
        gettimeofday(&s, NULL);
        void *data = parse(input, len);
        gettimeofday(&e, NULL);
        if(data == NULL) {
            fprintf(stdout, "%s - FAIL %f[us]\n", name, timediff(&s, &e));
            return 0;
        }
        t[i] = timediff(&s, &e);
        tsum += t[i];
        cnez_free(data);
    }
    fprintf(stdout, "%s - PASS %0.4f[us]\n", name, tsum / NUM_BENCH_RUNS);

    return 1;
}

int cnez_main(int ac, const char **av, void* (*parse)(const char *input, size_t len))
{
    int j;
    size_t len;
    int verbose = 0;
    int bench = 0;
    int mem = 0;

    if(ac == 1) {
        fprintf(stdout,
                "Usage: %s [-v] [-b] [-m] file1 file2 ...\n"
                "\t-v - Verbose (print AST)\n"
                "\t-m - Dump memory\n"
                "\t-b - Run benchmark\n",
                av[0]);
        return 1;
    }

    for(j = 1; j < ac; j++) {
        if (av[j][0] != '-') {
            break;
        }

        if(!strcmp(av[j], "-v")) {
            verbose = 1;
        }

        if(!strcmp(av[j], "-b")) {
            bench = 1;
        }

        if(!strcmp(av[j], "-m")) {
            mem = 1;
        }
    }

    for(; j < ac; j++) {
        char *input = get_input(av[j], &len);

        if (input == NULL) {
            fprintf(stdout, "Unable to open file '%s'\n", av[j]);
            continue;
        }

        if(bench) {
            if (!run_bench(av[j], (const char*)input, len, parse)) {
                return -1;
            }
        } else {
            struct timeval s, e;
            gettimeofday(&s, NULL);
            void *data = parse((const char*)input, len);
            gettimeofday(&e, NULL);

            if (verbose) {
                cnez_dump(data, stdout, 0);
                fprintf(stdout, "\n");
            } else {
                fprintf(stdout, "%s - %s in %0.4f[us]\n", av[j], data ? "PASS" : "FAIL", timediff(&s, &e));
            }

            if(mem) {
                cnez_dump_memory("Memory Usage", data);
            }
            cnez_free(data);
        }

        free((void*) input);
    }
    return 0;
}
