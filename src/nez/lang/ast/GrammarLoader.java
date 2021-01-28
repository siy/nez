package nez.lang.ast;

import nez.ast.Source;
import nez.ast.Tree;
import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.Production;
import nez.parser.ParserStrategy;
import nez.util.Verbose;

public final class GrammarLoader extends GrammarVisitorMap<GrammarLoaderVisitor> {

	public GrammarLoader(Grammar grammar, ParserStrategy strategy) {
		super(grammar, strategy);
		init(GrammarLoader.class, new Undefined());
	}

	public void load(Tree<?> node) {
		try {
			find(node.getTag().getSymbol()).accept(node);
		} catch (nez.ast.TreeVisitorMap.UndefinedException e) {
			Verbose.traceException(e);
		}
	}

	public class Undefined implements GrammarLoaderVisitor {
		@Override
		public void accept(Tree<?> node) {
			undefined(node);
		}
	}

	public class _Source extends Undefined {
		@Override
		public void accept(Tree<?> node) {
			for (Tree<?> sub : node) {
				load(sub);
			}
		}
	}

	public class _Production extends Undefined implements NezSymbols {
		ExpressionConstructor transducer = new NezExpressionConstructor(getGrammar(), getStrategy());

		@Override
		public void accept(Tree<?> node) {
			Tree<?> nameNode = node.get(_name);
			String localName = nameNode.toText();
			if (nameNode.is(_String)) {
				localName = Grammar.nameTerminalProduction(localName);
				// productionFlag |= Production.TerminalProduction;
			}
			Production rule = getGrammar().getProduction(localName);
			if (rule != null) {
				reportWarning(node, "duplicated rule name: " + localName);
			}
			Expression e = transducer.newInstance(node.get(_expr));
			getGrammar().addProduction(node.get(0), localName, e);
		}
	}

	public class _Example extends Undefined implements NezSymbols {
		@Override
		public void accept(Tree<?> node) {
			String hash = node.getText(_hash, null);
			Tree<?> textNode = node.get(_text);
			Tree<?> nameNode = node.get(_name2, null);
			if (nameNode != null) {
				GrammarExample.add(getGrammar(), true, nameNode, hash, textNode);
				nameNode = node.get(_name);
				GrammarExample.add(getGrammar(), false, nameNode, hash, textNode);
			} else {
				nameNode = node.get(_name);
				GrammarExample.add(getGrammar(), true, nameNode, hash, textNode);
			}
		}
	}

	public class Format extends Undefined implements NezSymbols {
		@Override
		public void accept(Tree<?> node) {

		}
	}

	/**
	 * public class Import extends Undefined {
	 * 
	 * @Override public void accept(Tree<?> node) { String ns = null; String
	 *           name = node.getText(0, "*"); int loc = name.indexOf('.'); if
	 *           (loc >= 0) { ns = name.substring(0, loc); name =
	 *           name.substring(loc + 1); } String urn =
	 *           path(node.getSource().getResourceName(), node.getText(1, ""));
	 *           try { GrammarFile source = (GrammarFile)
	 *           GrammarFileLoader.loadGrammar(urn, strategy); if
	 *           (name.equals("*")) { int c = 0; for (Production p : source) {
	 *           if (p.isPublic()) { checkDuplicatedName(node.get(0));
	 *           getGrammarFile().importProduction(ns, p); c++; } } if (c == 0)
	 *           { reportError(node.get(0),
	 *           "nothing imported (no public production exisits)"); } } else {
	 *           Production p = source.getProduction(name); if (p == null) {
	 *           reportError(node.get(0), "undefined production: " + name); }
	 *           getGrammar().importProduction(ns, p); } } catch (IOException e)
	 *           { reportError(node.get(1), "unfound: " + urn); } catch
	 *           (NullPointerException e) { reportError(node.get(1), "unfound: "
	 *           + urn); } } }
	 * 
	 *           private void checkDuplicatedName(Tree<?> errorNode) { String
	 *           name = errorNode.toText(); if
	 *           (this.getGrammar().hasProduction(name)) {
	 *           this.reportWarning(errorNode, "duplicated production: " +
	 *           name); } }
	 * 
	 *           private String path(String path, String path2) { if (path !=
	 *           null) { int loc = path.lastIndexOf('/'); if (loc > 0) { return
	 *           path.substring(0, loc + 1) + path2; } } return path2; }
	 **/

	public static String parseGrammarDescription(Source sc) {
		StringBuilder sb = new StringBuilder();
		long pos = 0;
		boolean found = false;
		for (; pos < sc.length(); pos++) {
			int ch = sc.byteAt(pos);
			if (Character.isAlphabetic(ch)) {
				found = true;
				break;
			}
		}
		if (found) {
			for (; pos < sc.length(); pos++) {
				int ch = sc.byteAt(pos);
				if (ch == '\n' || ch == '\r' || ch == '-' || ch == '*') {
					break;
				}
				sb.append((char) ch);
			}
		}
		return sb.toString().trim();
	}

}