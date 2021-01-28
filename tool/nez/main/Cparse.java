package nez.main;

import java.io.IOException;

import nez.ast.Source;
import nez.ast.Tree;
import nez.parser.Parser;
import nez.tool.ast.TreeWriter;

public class Cparse extends Command {
	@Override
	public void exec() throws IOException {
		checkInputSource();
		Parser parser = newParser();
		TreeWriter tw = getTreeWriter("ast xml json", "line");
		while (hasInputSource()) {
			Source input = nextInputSource();
			Tree<?> node = parser.parse(input);
			if (node == null) {
				parser.showErrors();
				continue;
			}
			if (outputDirectory != null) {
				tw.init(getOutputFileName(input, tw.getFileExtension()));
			}
			tw.writeTree(node);
		}
	}
}
