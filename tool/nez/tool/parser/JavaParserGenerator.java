package nez.tool.parser;

import nez.lang.Grammar;

public class JavaParserGenerator extends CommonParserGenerator {

	public JavaParserGenerator() {
		super(".java");
	}

	@Override
	protected void initLanguageSpec() {
		// this.UniqueNumberingSymbol = false;
		addType("$parse", "boolean");
		addType("$tag", "int");
		addType("$label", "int");
		addType("$table", "int");
		addType("$text", "byte[]");
		addType("$index", "byte[]");
		addType("$set", "boolean[]");
		addType("$string", "String[]");

		addType("memo", "int");
		addType(_set(), "boolean[]");
		addType(_index(), "byte[]");
		addType(_temp(), "boolean");
		addType(_pos(), "int");
		addType(_tree(), "T");
		addType(_log(), "int");
		addType(_table(), "int");
		addType(_state(), "ParserContext<T>");
	}

	@Override
	protected void generateHeader(Grammar g) {
		BeginDecl("public class " + _basename());
		{
			BeginDecl("private static <T> boolean start(ParserContext<T> c)");
			{
				Return(_funccall(_funcname(g.getStartProduction())));
			}
			EndDecl();
			importFileContent("java-parser-runtime.txt");
		}
	}

	@Override
	protected void generateFooter(Grammar g) {
		BeginDecl("public final static void main(String[] a)");
		{
			if (code.getMemoPointSize() > 0) {
				VarDecl("int", "w", _int(strategy.SlidingWindow));
				VarDecl("int", "n", _int(code.getMemoPointSize()));
				Statement("SimpleTree t = parse(a[0], w, n)");
			} else {
				Statement("SimpleTree t = parse(a[0], 0, 0)");
			}

			Statement("System.out.println(t)");
		}
		EndDecl();

		EndDecl(); // end of class
		file.writeIndent("/*EOF*/");
	}

	@Override
	protected String _defun(String type, String name) {
		return "private static <T> " + type + " " + name;
	}

}
