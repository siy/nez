package nez.tool.ast;

import java.util.ArrayList;

import nez.ast.Symbol;
import nez.ast.Tree;
import nez.junks.TreeVisitor;
import nez.util.FileBuilder;

public class NezFileFormatter extends TreeVisitor {
	private final FileBuilder f;

	public NezFileFormatter() {
		f = new FileBuilder(null);
	}

	boolean isBeforeComment = true;

	void writeIndent(String s) {
		if (s.startsWith("/*") || s.startsWith("//")) {
			if (!isBeforeComment) {
				f.writeNewLine();
			}
			isBeforeComment = true;
		} else {
			isBeforeComment = false;
		}
		f.writeIndent(s);
	}

	void write(String s) {
		f.write(s);
	}

	public void writeMultiLine(long prev, String sub) {
		int start = 0;
		boolean empty = true;
		for (int i = 0; i < sub.length(); i++) {
			char ch = sub.charAt(i);
			if (ch == ' ' || ch == '\t') {
				continue;
			}
			if (ch == '\n') {
				if (!empty) {
					if (prev == 0) {
						write(sub.substring(start, i));
						prev = 1;
					} else {
						writeIndent(sub.substring(start, i));
					}
				}
				start = i + 1;
				empty = true;
				continue;
			}
			empty = false;
		}
	}

	public void parse(Tree<?> node) {
		visit("p", node);
	}

	public static final Symbol _name = Symbol.unique("name");
	public static final Symbol _expr = Symbol.unique("expr");
	public static final Symbol _symbol = Symbol.unique("symbol");

	public static final Symbol _Production = Symbol.unique("Production");
	public static final Symbol _Example = Symbol.unique("Example");
	public static final Symbol _Format = Symbol.unique("Format");

	public boolean pSource(Tree<?> node) {
		ArrayList<Tree<?>> l = new ArrayList<>(node.size() * 2);
		for (Tree<?> subnode : node) {
			analyze(subnode, l);
		}

		long prev = 0;
		boolean hasFormat = false;
		boolean hasExample = false;
		for (Tree<?> subnode : l) {
			prev = checkComment(prev, subnode);
			if (subnode.is(_Format)) {
				hasFormat = true;
			}
			if (subnode.is(_Example)) {
				hasExample = true;
			}
			if (subnode.is(_Production)) {
				parse(subnode);
			}
		}
		if (hasExample) {
			writeIndent("");
			for (Tree<?> subnode : l) {
				if (subnode.is(_Example)) {
					parse(subnode);
				}
			}
		}
		if (hasFormat) {
			writeIndent("");
			for (Tree<?> subnode : l) {
				if (subnode.is(_Format)) {
					parse(subnode);
				}
			}
		}
		f.writeNewLine();
		f.writeIndent("// formatted by $ nez format");
		f.writeNewLine();
		return true;
	}

	private int prodLength = 8;

	private void analyze(Tree<?> node, ArrayList<Tree<?>> l) {
		l.add(node);
		if (node.is(_Production)) {
			Tree<?> name = node.get(_name);
			int len = name.toText().length() + 2;
			if (!name.is(_NonTerminal)) {
				len += 2;
			}
			if (len > prodLength) {
				prodLength = len;
			}
		}
	}

	private long checkComment(long prev, Tree<?> node) {
		long start = node.getSourcePosition();
		if (prev < start) {
			String sub = node.getSource().subString(prev, start);
			writeMultiLine(prev, sub);
		}
		return start + node.getLength();
	}

	public static final Symbol _NonTerminal = Symbol.unique("NonTerminal");

	public static final Symbol _Choice = Symbol.unique("Choice");
	public static final Symbol _Sequence = Symbol.unique("Sequence");
	public static final Symbol _List = Symbol.unique("List");
	public static final Symbol _Class = Symbol.unique("Class");

	//
	// public final static Tag _anno = Tag.tag("anno");

	public boolean pProduction(Tree<?> node) {
		Tree<?> nameNode = node.get(_name);
		Tree<?> exprNode = node.get(_expr);
		String name = nameNode.is(_NonTerminal) ? nameNode.toText() : "\"" + nameNode.toText() + "\"";
		String format = "%-" + prodLength + "s";
		writeIndent(String.format(format, name));
		String delim = "= ";
		if (exprNode.is(_Choice)) {
			for (Tree<?> sub : exprNode) {
				if (!delim.startsWith("=")) {
					writeIndent(String.format(format, ""));
				}
				f.write(delim);
				pExpression(sub);
				delim = "/ ";
			}
		} else {
			f.write(delim);
			pExpression(exprNode);
		}
		return true;
	}

	public boolean pExpression(Tree<?> node) {
		return (Boolean) visit("p", node);
	}

	public boolean pNonTerminal(Tree<?> node) {
		f.write(node.toText());
		return true;
	}

	public boolean pString(Tree<?> node) {
		f.write("\"");
		f.write(node.toText());
		f.write("\"");
		return true;
	}

	public boolean pCharacter(Tree<?> node) {
		f.write("'");
		f.write(node.toText());
		f.write("'");
		return true;
	}

	public boolean pClass(Tree<?> node) {
		f.write("[");
		if (node.size() > 0) {
			for (Tree<?> o : node) {
				if (o.is(_List)) { // range
					f.write(o.getText(0, ""));
					f.write("-");
					f.write(o.getText(1, ""));
				}
				if (o.is(_Class)) { // single
					f.write(o.toText());
				}
			}
		}
		f.write("]");
		return true;
	}

	public boolean pByteChar(Tree<?> node) {
		String t = node.toText();
		f.write(t);
		return true;
	}

	public boolean pAnyChar(Tree<?> node) {
		f.write(".");
		return true;
	}

	public boolean pChoice(Tree<?> node) {
		boolean spacing = false;
		for (Tree<?> trees : node) {
			if (spacing) {
				f.write(" / ");
			}
			spacing = pExpression(trees);
		}
		return spacing;
	}

	public boolean pSequence(Tree<?> node) {
		boolean spacing = false;
		for (Tree<?> trees : node) {
			if (spacing) {
				f.write(" ");
			}
			Tree<?> sub = trees;
			if (sub.is(_Choice)) {
				f.write("(");
			}
			spacing = pExpression(sub);
			if (sub.is(_Choice)) {
				f.write(")");
			}
		}
		return spacing;
	}

	private boolean needsParenthesis(Tree<?> node) {
		return node.is(_Choice) || node.is(_Sequence);
	}

	private boolean pUnary(String prefix, Tree<?> node, String suffix) {
		if (prefix != null) {
			f.write(prefix);
		}
		Tree<?> exprNode = node.get(_expr);
		if (needsParenthesis(exprNode)) {
			f.write("( ");
			pExpression(exprNode);
			f.write(" )");
		} else {
			pExpression(exprNode);
		}
		if (suffix != null) {
			f.write(suffix);
		}
		return true;
	}

	public boolean pNot(Tree<?> node) {
		return pUnary("!", node, null);
	}

	public boolean pAnd(Tree<?> node) {
		return pUnary("&", node, null);
	}

	public boolean pMatch(Tree<?> node) {
		return pUnary("~", node, null);
	}

	public boolean pOption(Tree<?> node) {
		return pUnary(null, node, "?");
	}

	public boolean pRepetition1(Tree<?> node) {
		return pUnary(null, node, "+");
	}

	public boolean pRepetition(Tree<?> node) {
		return pUnary(null, node, "*");
	}

	// PEG4d TransCapturing

	public boolean pNew(Tree<?> node) {
		Tree<?> exprNode = node.get(_expr, null);
		f.write("{ ");
		pExpression(exprNode);
		f.write(" }");
		return true;
	}

	private Symbol parseLabelNode(Tree<?> node) {
		Symbol label = null;
		Tree<?> labelNode = node.get(_name, null);
		if (labelNode != null) {
			label = Symbol.unique(labelNode.toText());
		}
		return label;
	}

	public boolean pLeftFold(Tree<?> node) {
		Symbol tag = parseLabelNode(node);
		Tree<?> exprNode = node.get(_expr, null);
		String label = tag == null ? "$" : "$" + tag;
		if (exprNode != null) {
			f.write("{" + label + " ");
			pExpression(exprNode);
			f.write(" }");
		} else {
			f.write("{" + label + " }");
		}
		return true;
	}

	public boolean pLink(Tree<?> node) {
		Symbol tag = parseLabelNode(node);
		f.write("$");
		if (tag != null) {
			f.write(tag.toString());
		}
		f.write("(");
		pExpression(node.get(_expr));
		f.write(")");
		return true;
	}

	public boolean pTagging(Tree<?> node) {
		f.write("#");
		f.write(node.toText());
		return true;
	}

	public boolean pReplace(Tree<?> node) {
		f.write("`");
		f.write(node.toText());
		f.write("`");
		return true;
	}

	public boolean pIf(Tree<?> node) {
		f.write("<if " + node.getText(_name, "") + ">");
		return true;
	}

	public boolean pOn(Tree<?> node) {
		String p = "<on " + node.getText(_name, "") + " ";
		return pUnary(p, node, ">");
	}

	public boolean pBlock(Tree<?> node) {
		return pUnary("<block ", node, ">");
	}

	public boolean pDef(Tree<?> node) {
		String p = "<def " + node.getText(_name, "") + " ";
		return pUnary(p, node, ">");
	}

	public boolean pIs(Tree<?> node) {
		f.write("<is " + node.getText(_name, "") + ">");
		return true;
	}

	public boolean pIsa(Tree<?> node) {
		f.write("<isa " + node.getText(_name, "") + ">");
		return true;
	}

	public boolean pExists(Tree<?> node) {
		String symbol = node.getText(_symbol, null);
		if (symbol == null) {
			f.write("<exists " + node.getText(_name, "") + ">");
		} else {
			f.write("<exists " + node.getText(_name, "") + " " + symbol + ">");
		}
		return true;
	}

	public boolean pLocal(Tree<?> node) {
		String p = "<local " + node.getText(_name, "") + " ";
		return pUnary(p, node, ">");
	}

	public boolean pDefIndent(Tree<?> node) {
		f.write("<match indent>");
		return true;
	}

	public boolean pIndent(Tree<?> node) {
		f.write("<match indent>");
		return true;
	}

	public boolean pUndefined(Tree<?> node) {
		throw new RuntimeException("undefined node");
	}

	public static final Symbol _hash = Symbol.unique("hash"); // example
	public static final Symbol _name2 = Symbol.unique("name2"); // example
	public static final Symbol _text = Symbol.unique("text"); // example

	public boolean pExample(Tree<?> node) {
		Tree<?> nameNode = node.get(_name);
		Tree<?> name2Node = node.get(_name2, null);
		String hash = node.getText(_hash, null);
		Tree<?> textNode = node.get(_text);

		writeIndent("example " + nameNode.toText());
		if (name2Node != null) {
			f.write("&" + name2Node.toText());
		}
		if (hash != null) {
			f.write(" ~" + hash);
		}
		String s = "'''";
		f.write(" " + s);
		writeIndent(textNode.toText());
		writeIndent(s);
		return true;
	}

	public static final Symbol _size = Symbol.unique("hash"); // format
	public static final Symbol _format = Symbol.unique("format"); // format

	public boolean pFormat(Tree<?> node) {
		writeIndent("format #" + node.getText(_name, ""));
		f.write("[" + node.getText(_size, "*") + "] ");
		Tree<?> formatNode = node.get(_format);
		f.write("`" + formatNode.toText() + "`");
		return true;
	}

	/* import */
	public boolean pImport(Tree<?> node) {
		return true;
	}
}
