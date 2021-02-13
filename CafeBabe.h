
// 
// Header file for CafeBabe grammar parser.
//

#ifndef __CAFEBABE_H
#define __CAFEBABE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef unsigned long int symbol_t;

#define _QualifiedName ((symbol_t)1)
#define _Name ((symbol_t)2)
#define _NameWithAlias ((symbol_t)3)
#define _ImportList ((symbol_t)4)
#define _Use ((symbol_t)5)
#define _Program ((symbol_t)6)
#define MAXTAG 7
#define MAXLABEL 1

typedef struct Tree {
long           refc;
symbol_t       tag;
const unsigned char    *text;
size_t         len;
size_t         size;
symbol_t      *labels;
struct Tree  **childs;
} Tree;

void* CafeBabe_parse(const char *text,
                     size_t len,
                     void* thunk,
                     void (*ferr)(const char *, const unsigned char *, void *),
                     void* (*fnew)(symbol_t, const char *, size_t, size_t, void *),
                     void  (*fset)(void *, size_t, symbol_t, void *, void *),
                     void  (*fgc)(void *, int, void *));

long CafeBabe_match(const char *text, size_t len);

const char* CafeBabe_tag(symbol_t n);

const char* CafeBabe_label(symbol_t n);

void cnez_dump(Tree* t, FILE *fp, int depth);

void cnez_free(void *t);

#ifdef __cplusplus
}
#endif

#endif /* __CAFEBABE_H */
