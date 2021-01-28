package nez.main;

import java.io.IOException;

import nez.parser.Parser;
import nez.tool.parser.ParserGrammarWriter;
import nez.tool.peg.LPegTranslator;
import nez.tool.peg.PEGTranslator;

public class Cpeg extends Command {

	@Override
	public void exec() throws IOException {
		ParserGrammarWriter pw = newGenerator();
		Parser p = newParser();
		pw.init(p);
		pw.generate();
	}

	protected ParserGrammarWriter newGenerator() {
		if (outputFormat == null) {
			outputFormat = "peg";
		}
		switch (outputFormat) {
		case "peg":
			return new PEGTranslator();
		case "lpeg":
		case "lua":
			return new LPegTranslator();
		default:
			return (ParserGrammarWriter) newExtendedOutputHandler("", "peg pegjs pegtl lpeg mouse nez");
		}
	}
}
