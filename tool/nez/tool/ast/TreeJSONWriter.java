package nez.tool.ast;

import nez.ast.Tree;
import nez.util.StringUtils;

public class TreeJSONWriter extends TreeWriter {

	public TreeJSONWriter() {
		super(".json");
	}

	@Override
	public void writeTree(Tree<?> node) {
		writeJSON(node);
		file.writeNewLine();
	}

	private void writeJSON(Tree<?> node) {
		if (node.size() == 0) {
			String text = node.toText();
			if (dataOption) {
				try {
					double v = Double.parseDouble(text);
					file.write(Double.toString(v));
					return;
				} catch (NumberFormatException e) {
					//Ignored
				}
				try {
					long v = Long.parseLong(text);
					file.write(Long.toString(v));
					return;
				} catch (NumberFormatException e) {
					//Ignored
				}
				file.write(StringUtils.quoteString('"', text, '"'));
			} else {
				file.write("{");
				file.write("\"type\":");
				file.write(StringUtils.quoteString('"', node.getTag().toString(), '"'));
				file.write(",\"pos\":");
				file.write("" + node.getSourcePosition());
				file.write(",\"line\":");
				file.write("" + node.getLineNum());
				file.write(",\"column\":");
				file.write("" + node.getColumn());
				file.write(",\"text\":");
				file.write(StringUtils.quoteString('"', text, '"'));
				file.write("}");
			}
			return;
		}
		if (node.isAllLabeled()) {
			file.write("{");
			if (!dataOption) {
				file.write("\"type\":");
				file.write(StringUtils.quoteString('"', node.getTag().toString(), '"'));
				file.write(",");
			}
			for (int i = 0; i < node.size(); i++) {
				if (i > 0) {
					file.write(",");
				}
				file.write(StringUtils.quoteString('"', node.getLabel(i).toString(), '"'));
				file.write(":");
				writeJSON(node.get(i));
			}
			file.write("}");
			return;
		}
		file.write("[");
		for (int i = 0; i < node.size(); i++) {
			if (i > 0) {
				file.write(",");
			}
			writeJSON(node.get(i));
		}
		file.write("]");
	}

}
