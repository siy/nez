package nez.debugger;

import java.io.IOException;

import nez.parser.Parser;
import nez.parser.ParserStrategy;
import nez.util.ConsoleUtils;
import nez.util.UList;

public class DebugManager {
	private int index;
	public UList<String> inputFileLists;
	public String text;

	public DebugManager(UList<String> inputFileLists) {
		this.inputFileLists = inputFileLists;
		this.index = 0;
	}

	public DebugManager(String text) {
		this.text = text;
		this.index = 0;
	}

	public void exec(Parser peg, ParserStrategy option) {
		if (text != null) {
			DebugSourceContext sc = DebugSourceContext.newStringContext(text);
			parse(sc, peg, option);
			return;
		}
		while (index < inputFileLists.size()) {
			DebugSourceContext sc = nextInputSource();
			parse(sc, peg, option);
		}
	}

	public final DebugSourceContext nextInputSource() {
		if (index < inputFileLists.size()) {
			String f = inputFileLists.ArrayValues[index];
			this.index++;
			try {
				return DebugSourceContext.newDebugFileContext(f);
			} catch (IOException e) {
				ConsoleUtils.exit(1, "cannot open: " + f);
			}
		}
		ConsoleUtils.exit(1, "error: input file list is empty");
		return null;
	}

	//FIXME: not implemented
	private void parse(DebugSourceContext sc, Parser peg, ParserStrategy option) {
		throw new RuntimeException("FIXME");
	}
}
