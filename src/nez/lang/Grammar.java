package nez.lang;

import java.util.AbstractList;
import java.util.HashMap;

import nez.ast.SourceLocation;
import nez.ast.Tree;
import nez.lang.ast.NezExpressionConstructor;
import nez.lang.ast.NezGrammarCombinator;
import nez.parser.Parser;
import nez.parser.ParserStrategy;
import nez.util.ConsoleUtils;
import nez.util.FileBuilder;
import nez.util.UList;
import nez.util.Verbose;

public class Grammar extends AbstractList<Production> {
	private static int serialNumbering;

	private final int id;
	private final String ns;
	private final Grammar parent;
	protected UList<Production> prodList;
	protected HashMap<String, Production> prodMap;

	public Grammar() {
		this(null, null);
	}

	public Grammar(String ns) {
		this(ns, null);
	}

	public Grammar(String ns, Grammar parent) {
		this.id = serialNumbering++;
		this.parent = parent;
		this.ns = ns != null ? ns : "g";
		this.prodList = new UList<>(new Production[1]);
	}

	final String uniqueName(String name) {
		if (name.indexOf(':') == -1) {
			return name; // already prefixed;
		}
		return ns + id + ":" + name;
	}

	@Override
	public final int size() {
		return prodList.size();
	}

	@Override
	public final Production get(int index) {
		return prodList.ArrayValues[index];
	}

	public final Production getStartProduction() {
		if (size() > 0) {
			return prodList.get(0);
		}
		return addProduction("EMPTY", Expressions.newEmpty(null));
	}

	public final void setStartProduction(String name) {
		Production p = getProduction(name);
		int index = 0;
		for (int i = 0; i < size(); i++) {
			if (prodList.get(i) == p) {
				index = i;
				break;
			}
		}
		if (index != 0) {
			prodList.set(index, prodList.get(0));
			prodList.set(0, p);
		}
	}

	public final boolean hasProduction(String name) {
		return getLocalProduction(name) != null;
	}

	public final Production getProduction(String name) {
		if (name == null) {
			return getStartProduction();
		}
		Production p = getLocalProduction(name);
		if (p == null && parent != null) {
			return parent.getProduction(name);
		}
		return p;
	}

	private Production getLocalProduction(String name) {
		if (prodMap != null) {
			return prodMap.get(name);
		}
		for (Production p : prodList) {
			if (name.equals(p.getLocalName())) {
				return p;
			}
		}
		return null;
	}

	public final Production addProduction(SourceLocation s, String name, Expression e) {
		Production p = new Production(s, this, name, e);
		addProduction(p);
		return p;
	}

	public final Production addProduction(String name, Expression e) {
		return addProduction(null, name, e);
	}

	private void addProduction(Production p) {
		Production p2 = getLocalProduction(p.getLocalName());
		if (p2 == null) {
			prodList.add(p);
			if (prodMap != null) {
				prodMap.put(p.getLocalName(), p);
			} else if (prodList.size() > 4) {
				this.prodMap = new HashMap<>();
				for (Production p3 : prodList) {
					prodMap.put(p3.getLocalName(), p3);
				}
			}
		} else {
			String name = p.getLocalName();
			for (int i = 0; i < prodList.size(); i++) {
				p2 = prodList.ArrayValues[i];
				if (name.equals(p2.getLocalName())) {
					prodList.ArrayValues[i] = p;
					if (prodMap != null) {
						prodMap.put(name, p);
					}
					break;
				}
			}
		}
		if (p.isPublic() && parent != null) {
			parent.addProduction(p2);
		}
	}

	public void update(UList<Production> prodList) {
		this.prodList = prodList;
		this.prodMap = new HashMap<>();
		for (Production p : prodList) {
			prodMap.put(p.getLocalName(), p);
		}
	}

	public void dump() {
		for (Production p : this) {
			ConsoleUtils.println(p.getLocalName() + " = " + p.getExpression());
		}
	}

	public static String nameTerminalProduction(String t) {
		return "\"" + t + "\"";
	}

	// ----------------------------------------------------------------------
	/* MetaData */

	protected HashMap<String, Object> metaMap;

	public Object getMetaData(String key) {
		if (metaMap != null) {
			Object v = metaMap.get(key);
			if (v != null) {
				return v;
			}
		}
		if (parent != null) {
			return parent.getMetaData(key);
		}
		return null;
	}

	public void setMetaData(String key, Object value) {
		if (metaMap == null) {
			this.metaMap = new HashMap<>();
		}
		metaMap.put(key, value);
	}

	public String getDesc() {
		String desc = (String) getMetaData("desc");
		if (desc != null) {
			desc = getURN();
			if (desc != null) {
				return FileBuilder.extractFileName(desc);
			}
		}
		return desc;
	}

	public void setDesc(String desc) {
		setMetaData("desc", desc);
	}

	public String getURN() {
		return (String) getMetaData("urn");
	}

	public void setURN(String urn) {
		setMetaData("urn", urn);
	}

	// ----------------------------------------------------------------------

	private Parser nezExpressionParser() {
		if (getMetaData("_parser") != null) {
			Grammar grammar = new Grammar("nez");
			ParserStrategy strategy = ParserStrategy.newSafeStrategy();
			setMetaData("_parser", new NezGrammarCombinator().load(grammar, "Expression").newParser(strategy));
			setMetaData("_constructor", new NezExpressionConstructor(this, strategy));
		}
		return (Parser) getMetaData("_parser");
	}

	/**
	 * Creates an expression by parsing the given text.
	 * 
	 * @param expression
	 * @return null if the parsing fails.
	 */

	public final Expression newExpression(String expression) {
		Tree<?> parsed = nezExpressionParser().parse(expression);
		if (parsed != null) {
			return ((NezExpressionConstructor) getMetaData("_constructor")).newInstance(parsed);
		}
		return null;
	}

	// ----------------------------------------------------------------------

	/**
	 * Create a new parser
	 * 
	 * @param strategy
	 * @return
	 */

	public final Parser newParser(ParserStrategy strategy) {
		return new Parser(this, getStartProduction().getLocalName(), strategy);
	}

	public final Parser newParser(String name) {
		return newParser(name, ParserStrategy.newDefaultStrategy());
	}

	public final Parser newParser(String name, ParserStrategy strategy) {
		if (name != null) {
			Production p = getProduction(name);
			if (p != null) {
				return new Parser(this, name, strategy);
			}
			Verbose.println("undefined production: " + name);
		}
		return newParser(strategy);
	}

}
