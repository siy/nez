package nez.main;

import java.io.IOException;

import nez.lang.Grammar;
import nez.lang.ast.GrammarExample;
import nez.util.ConsoleUtils;

public class Cexample extends Ctest {

	@Override
	public void exec() throws IOException {
		Grammar grammar = getSpecifiedGrammar();
		GrammarExample example = (GrammarExample) grammar.getMetaData("example");
		if (example == null) {
			ConsoleUtils.println("No example is present");
			return;
		}
		testAll(grammar, example.getExampleList(), strategy);
		if (!testAll(grammar, example.getExampleList(), strategy)) {
			System.exit(1);
		}
	}
}