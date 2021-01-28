package nez.parser;

import java.util.ArrayList;
import java.util.List;

import nez.ast.CommonTree;
import nez.ast.Source;
import nez.ast.SourceError;
import nez.ast.Tree;
import nez.lang.Grammar;
import nez.parser.io.CommonSource;
import nez.parser.vm.ParserMachineContext;
import nez.util.ConsoleUtils;
import nez.util.UList;

public final class Parser {
	private final Grammar grammar;
	private final ParserStrategy strategy;
	private final String start;

	public Parser(Grammar grammar, String start, ParserStrategy strategy) {
		this.grammar = grammar;
		this.start = start;
		this.strategy = strategy;
	}

	public final Grammar getGrammar() {
		return grammar;
	}

	public final ParserStrategy getParserStrategy() {
		return strategy;
	}

	private Grammar compiledGrammar;
	private ParserCode<?> pcode;

	public final Grammar getCompiledGrammar() {
		if (compiledGrammar == null) {
			compiledGrammar = new ParserOptimizer().optimize(grammar.getProduction(start), strategy, null);
		}
		return compiledGrammar;
	}

	public final ParserCode<?> getParserCode() {
		if (pcode == null) {
			pcode = strategy.newParserCode(getCompiledGrammar());
		}
		return pcode;
	}

	public final ParserCode<?> compile() {
		this.pcode = strategy.newParserCode(getCompiledGrammar());
		return pcode;
	}

	public final ParserInstance newParserContext(Source source, Tree<?> prototype) {
		ParserCode<?> pcode = getParserCode();
		return strategy.newParserContext(source, pcode.getMemoPointSize(), prototype);
	}

	/* -------------------------------------------------------------------- */

	public final Object perform(ParserInstance context) {
		ParserCode<?> code = getParserCode();
		// context.init(newMemoTable(context), prototype);
		if (prof != null) {
			context.startProfiling(prof);
		}
		Object matched = code.exec(context);
		if (prof != null) {
			context.doneProfiling(prof);
		}
		if (matched == null) {
			perror(context.getSource(), context.getMaximumPosition(), "syntax error");
			return null;
		}
		if (disabledUncosumed && context.hasUnconsumed()) {
			perror(context.getSource(), context.getPosition(), "unconsumed");
		}
		return matched;
	}

	@SuppressWarnings("unchecked")
	public final <T extends Tree<T>> T perform(Source s, T proto) {
		if (strategy.Moz) {
			return (T) perform(newParserContext(s, proto));
		}

		ParserMachineContext<T> ctx = new ParserMachineContext<>(s, proto);
		ParserCode<?> code = getParserCode();
		ctx.initMemoTable(strategy.SlidingWindow, code.getMemoPointSize());

		T matched = code.exec(ctx);

		if (matched == null) {
			perror(s, ctx.getMaximumPosition(), "syntax error");
			return null;
		}
		if (disabledUncosumed && !ctx.eof()) {
			perror(s, ctx.getPosition(), "unconsumed");
		}
		return matched;
	}

	protected ParserProfiler prof;

	public void setProfiler(ParserProfiler prof) {
		this.prof = prof;
		if (prof != null) {
			compile();
			// prof.setFile("G.File", this.start.getGrammarFile().getURN());
			prof.setCount("G.Production", grammar.size());
			prof.setCount("G.Instruction", pcode.getInstructionSize());
			prof.setCount("G.MemoPoint", pcode.getMemoPointSize());
		}
	}

	public ParserProfiler getProfiler() {
		return prof;
	}

	public void logProfiler() {
		if (prof != null) {
			prof.log();
		}
	}

	/* --------------------------------------------------------------------- */

	public final boolean match(Source s) {
		return perform(newParserContext(s, null)) != null;
	}

	public final boolean match(String str) {
		return match(CommonSource.newStringSource(str));
	}

	public <T extends Tree<T>> T parse(Source source, T proto) {
		return perform(source, proto);
	}

	public final CommonTree parse(Source sc) {
		return parse(sc, new CommonTree());
	}

	public final CommonTree parse(String str) {
		Source sc = CommonSource.newStringSource(str);
		return parse(sc, new CommonTree());
	}

	/* Errors */

	private boolean disabledUncosumed;
	private UList<SourceError> errors;

	public final void setDisabledUnconsumed(boolean disabled) {
		this.disabledUncosumed = disabled;
	}

	private void perror(Source source, long pos, String message) {
		if (errors == null) {
			this.errors = new UList<>(new SourceError[4]);
		}
		errors.add(new SourceError(source, pos, message));
	}

	public final boolean hasErrors() {
		return errors != null;
	}

	public final void clearErrors() {
		errors = null;
	}

	public final List<SourceError> getErrors() {
		return errors == null ? new ArrayList<>() : errors;
	}

	public final boolean showErrors() {
		if (errors != null) {
			for (SourceError e : errors) {
				ConsoleUtils.println(e.toString());
			}
			clearErrors();
			return true;
		}
		return false;
	}

	public final void ensureNoErrors() throws ParserException {
		if (errors != null) {
			throw new ParserException(errors.ArrayValues[0].toString());
		}
	}

}
