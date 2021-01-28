package nez.lang.ast;

import nez.ast.SourceLocation;
import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import nez.lang.Grammar;
import nez.parser.ParserStrategy;

public class GrammarVisitorMap<T> extends TreeVisitorMap<T> {
	protected final Grammar grammar;
	protected final ParserStrategy strategy;

	public GrammarVisitorMap(Grammar grammar, ParserStrategy strategy) {
		this.grammar = grammar;
		this.strategy = strategy;
	}

	public Grammar getGrammar() {
		return grammar;
	}

	public ParserStrategy getStrategy() {
		return strategy;
	}

	public final String key(Tree<?> node) {
		return node.getTag().getSymbol();
	}

	public final void reportError(SourceLocation s, String fmt, Object... args) {
		if (strategy != null) {
			strategy.reportError(s, fmt, args);
		}
	}

	public final void reportWarning(SourceLocation s, String fmt, Object... args) {
		if (strategy != null) {
			strategy.reportWarning(s, fmt, args);
		}
	}

	public final void reportNotice(SourceLocation s, String fmt, Object... args) {
		if (strategy != null) {
			strategy.reportNotice(s, fmt, args);
		}
	}

}
