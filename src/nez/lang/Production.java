package nez.lang;

import nez.ast.SourceLocation;
import nez.util.ConsoleUtils;
import nez.util.UList;

public class Production {

	private final Grammar grammar;
	private final String name;
	private final String uname;
	private Expression body;

	Production(SourceLocation s, Grammar grammar, String name, Expression body) {
		this.grammar = grammar;
		this.name = name;
		this.uname = grammar.uniqueName(name);
		this.body = (body == null) ? Expressions.newEmpty(s) : body;
	}

	public final Grammar getGrammar() {
		return grammar;
	}

	public final String getLocalName() {
		return name;
	}

	public final String getUniqueName() {
		return uname;
	}

	public final Expression getExpression() {
		return body;
	}

	public final void setExpression(Expression e) {
		this.body = e;
	}

	public final boolean isPublic() {
		return true;
	}

	public final boolean isTerminal() {
		return name.startsWith("\"");
	}

	public final void dump() {
		UList<String> l = new UList<>(new String[4]);
		if (isPublic()) {
			l.add("public");
		}
		ConsoleUtils.println(l + "\n" + getLocalName() + " = " + getExpression());
	}

}
