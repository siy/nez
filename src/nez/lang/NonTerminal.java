package nez.lang;

public class NonTerminal extends Expression {
	private final Grammar grammar;
	private final String localName;
	private final String uniqueName;
	private final Production deref = null;

	public NonTerminal(Grammar g, String ruleName) {
		this.grammar = g;
		this.localName = ruleName;
		this.uniqueName = grammar.uniqueName(localName);
	}

	@Override
	public final boolean equals(Object o) {
		if (o instanceof NonTerminal) {
			return localName.equals(((NonTerminal) o).getLocalName());
		}
		return false;
	}

	public final Grammar getGrammar() {
		return grammar;
	}

	public final String getLocalName() {
		return localName;
	}

	public final boolean isTerminal() {
		return localName.startsWith("\"");
	}

	public final String getUniqueName() {
		return uniqueName;
	}

	public final Production getProduction() {
		if (deref != null) {
			return deref;
		}
		return grammar.getProduction(localName);
	}

	public final Expression deReference() {
		Production r = grammar.getProduction(localName);
		return (r != null) ? r.getExpression() : null;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public Expression get(int index) {
		return null;
	}

	@Override
	public Object visit(Expression.Visitor v, Object a) {
		return v.visitNonTerminal(this, a);
	}

	public final NonTerminal newNonTerminal(String localName) {
		return Expressions.newNonTerminal(getSourceLocation(), getGrammar(), localName);
	}

}
