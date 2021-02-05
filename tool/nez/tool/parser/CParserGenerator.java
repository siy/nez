package nez.tool.parser;

import nez.lang.Grammar;
import nez.util.StringUtils;

import java.util.Locale;

public class CParserGenerator extends CommonParserGenerator {

	public CParserGenerator() {
		super(".c");
	}

	@Override
	protected void initLanguageSpec() {
		SupportedRange = true;
		SupportedMatch2 = true;
		SupportedMatch3 = true;
		SupportedMatch4 = true;
		SupportedMatch5 = true;
		SupportedMatch6 = true;
		SupportedMatch7 = true;
		SupportedMatch8 = true;

		addType("$parse", "int");
		addType("$tag", "int");
		addType("$label", "int");
		addType("$table", "int");
		addType("$arity", "int");
		addType("$text", "const unsigned char");
		addType("$index", "const unsigned char");
		if (UsingBitmap) {
			addType("$set", "int");
		} else {
			addType("$set", "const unsigned char");
		}
		addType("$range", "const unsigned char __attribute__((aligned(16)))");
		addType("$string", "const char *");

		addType("memo", "int");
		if (UsingBitmap) {
			addType(_set(), "int");
		} else {
			addType(_set(), "const unsigned char *");/* boolean */
		}
		addType(_index(), "const unsigned char *");
		addType(_temp(), "int");/* boolean */
		addType(_pos(), "const unsigned char *");
		addType(_tree(), "size_t");
		addType(_log(), "size_t");
		addType(_table(), "size_t");
		addType(_state(), "ParserContext *");
	}

	@Override
	protected String _True() {
		return "1";
	}

	@Override
	protected String _False() {
		return "0";
	}

	@Override
	protected String _Null() {
		return "NULL";
	}

	/* Expression */

	@Override
	protected String _Field(String o, String name) {
		return o + "->" + name;
	}

	@Override
	protected String _Func(String name, String... args) {
		StringBuilder sb = new StringBuilder();
		sb.append("ParserContext_");
		sb.append(name);
		sb.append("(");
		sb.append(_state());
		for (String arg : args) {
			sb.append(",");
			sb.append(arg);
		}
		sb.append(")");
		return sb.toString();
	}

	@Override
	protected String _text(byte[] text) {
		return super._text(text) + ", " + _int(text.length);
	}

	@Override
	protected String _text(String key) {
		if (key == null) {
			return _Null() + ", 0";
		}
		return nameMap.get(key) + ", " + _int(StringUtils.utf8(key).length);
	}

	@Override
	protected String _defun(String type, String name) {
		if (crossRefNames.contains(name)) {
			return type + " " + name;
		}
		return "static inline " + type + " " + name;
	}

	/* Statement */

	@Override
	protected void DeclConst(String type, String name, String expr) {
		Statement("static " + type + " " + name + " = " + expr);
	}

	// Grammar Generator

	@Override
	protected void generateHeader(Grammar g) {
		Line("// ");
		Line("// " + _basename() + " grammar parser.");
		Line("//");
		NewLine();
		importFileContent("cnez-runtime.txt");
	}

	@Override
	protected void generatePrototypes() {
		LineComment("Prototypes");
		for (String name : crossRefNames) {
			Statement(_defun("int", name) + "(ParserContext *c)");
		}
	}

	@Override
	protected void generateFooter(Grammar g) {
		importFileContent("cnez-utils.txt");
		//
		BeginDecl("Tree* " + _ns()
					  + "parse(const char *text, size_t len, void *thunk, void* (*fnew)(symbol_t, const unsigned char *, size_t, size_t, void *), void  (*fset)(void *, size_t, symbol_t, void *, void *), void  (*fgc)(void *, int, void *))");
		{
			VarDecl("void*", "result", _Null());
			VarDecl(_state(), "ParserContext_new((const unsigned char*)text, len)");
			Statement(_Func("initTreeFunc", "thunk", "fnew", "fset", "fgc"));
			InitMemoPoint();
			If(_funccall(_funcname(g.getStartProduction())));
			{
				VarAssign("result", _Field(_state(), _tree()));
				If("result == NULL");
				{
					Statement("result = c->fnew(0, (const unsigned char*)text, (c->pos - (const unsigned char*)text), 0, c->thunk)");
				}
				EndIf();
			}
			EndIf();
			Statement(_Func("free"));
			Return("result");
		}
		EndDecl();
		BeginDecl("static void* cnez_parse(const char *text, size_t len)");
		{
			Return(_ns() + "parse(text, len, NULL, NULL, NULL, NULL)");
		}
		EndDecl();
		BeginDecl("long " + _ns() + "match(const char *text, size_t len)");
		{
			VarDecl("long", "result", "-1");
			VarDecl(_state(), "ParserContext_new((const unsigned char*)text, len)");
			Statement(_Func("initNoTreeFunc"));
			InitMemoPoint();
			If(_funccall(_funcname(g.getStartProduction())));
			{
				VarAssign("result", _cpos() + "-" + _Field(_state(), "inputs"));
			}
			EndIf();
			Statement(_Func("free"));
			Return("result");
		}
		EndDecl();
		BeginDecl("const char* " + _ns() + "tag(symbol_t n)");
		{
			Return("_tags[n]");
		}
		EndDecl();
		BeginDecl("const char* " + _ns() + "label(symbol_t n)");
		{
			Return("_labels[n]");
		}
		EndDecl();
		Line("#ifndef UNUSE_MAIN");
		BeginDecl("int main(int ac, const char **argv)");
		{
			Return("cnez_main(ac, argv, cnez_parse)");
		}
		EndDecl();
		Line("#endif/*MAIN*/");
		NewLine();
		Line("#ifdef __cplusplus");
		Line("}");
		Line("#endif");

		Line("// End of File");
		generateHeaderFile();
		showManual("cnez-man.txt", new String[]{"$cmd$", _basename()});
	}

	private void generateHeaderFile() {
		var upcaseName = _basename().toUpperCase(Locale.ROOT) + "_H";
		setFileBuilder(".h");
		Line("// ");
		Line("// Header file for " + _basename() + " grammar parser.");
		Line("//");
		NewLine();
		Line("#ifndef __" + upcaseName);
		Line("#define __" + upcaseName);
		NewLine();
		Line("#ifdef __cplusplus");
		Line("extern \"C\" {");
		Line("#endif");
		NewLine();
		Statement("typedef unsigned long int symbol_t");
		NewLine();

		int c = 1;
		for (String s : tagList) {
			if (s.equals("")) {
				continue;
			}
			Line("#define _" + s + " ((symbol_t)" + c + ")");
			c++;
		}
		Line("#define MAXTAG " + c);
		c = 1;
		for (String s : labelList) {
			if (s.equals("")) {
				continue;
			}
			Line("#define _" + s + " ((symbol_t)" + c + ")");
			c++;
		}
		Line("#define MAXLABEL " + c);
		NewLine();
		Line("typedef struct Tree {");
		Line("    long           refc;");
		Line("    symbol_t       tag;");
		Line("    const unsigned char    *text;");
		Line("    size_t         len;");
		Line("    size_t         size;");
		Line("    symbol_t      *labels;");
		Line("    struct Tree  **childs;");
		Line("} Tree;");
		NewLine();
		var space = " ".repeat(12 + _ns().length());
		Line("Tree* " + _ns() + "parse(const char *text,");
		Line(space + "size_t len,");
		Line(space + "void *,");
		Line(space + "void* (*fnew)(symbol_t, const char *, size_t, size_t, void *),");
		Line(space + "void  (*fset)(void *, size_t, symbol_t, void *, void *),");
		Statement(space + "void  (*fgc)(void *, int, void *))");
		NewLine();
		Statement("long " + _ns() + "match(const char *text, size_t len)");
		NewLine();
		Statement("const char* " + _ns() + "tag(symbol_t n)");
		NewLine();
		Statement("const char* " + _ns() + "label(symbol_t n)");
		NewLine();
		Statement("void cnez_dump(Tree* t, FILE *fp, int depth)");
		NewLine();
		Line("#ifdef __cplusplus");
		Line("}");
		Line("#endif");
		NewLine();
		Line("#endif /* __" + upcaseName + " */");
		file.close();
	}

}
