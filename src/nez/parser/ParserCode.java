package nez.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import nez.ast.Tree;
import nez.lang.Grammar;
import nez.lang.Production;
import nez.lang.Productions;
import nez.lang.Productions.NonterminalReference;
import nez.lang.Typestate;
import nez.lang.Typestate.TypestateAnalyzer;
import nez.parser.vm.Moz86;
import nez.parser.vm.MozInst;
import nez.parser.vm.ParserMachineContext;
import nez.util.UList;
import nez.util.Verbose;

public abstract class ParserCode<T extends Instruction> {

	protected final Grammar grammar;
	protected UList<T> codeList;
	protected final boolean RecognitionMode;

	protected ParserCode(Grammar grammar, T[] initArray) {
		this.grammar = grammar;
		this.funcMap = new HashMap<>();
		this.codeList = initArray != null ? new UList<>(initArray) : null;
		TypestateAnalyzer typestate = Typestate.newAnalyzer();
		this.RecognitionMode = typestate.inferTypestate(grammar.getStartProduction()) == Typestate.Unit;
	}

	public final Grammar getCompiledGrammar() {
		return grammar;
	}

	public abstract void layoutCode(T inst);

	public final T getStartInstruction() {
		return codeList.get(0);
	}

	public final int getInstructionSize() {
		return codeList.size();
	}

	/* dump */

	public void dump() {
		for (T inst : codeList) {
			MozInst in = (MozInst) inst;
			if (in instanceof Moz86.Nop) {
				System.out.println(((Moz86.Nop) in).name);
				continue;
			}
			if (in.joinPoint) {
				System.out.println("L" + in.id);
			}
			System.out.println("\t" + inst);
			if (!in.isIncrementedNext()) {
				System.out.println("\tjump L" + in.next.id);
			}
		}
	}

	public final <E extends Tree<E>> E exec(ParserMachineContext<E> ctx) {
		int ppos = (int) ctx.getPosition();
		MozInst code = (MozInst) getStartInstruction();
		boolean result = exec(ctx, code);
		if (RecognitionMode && result) {
			ctx.left = ctx.newTree(null, ppos, (int) ctx.getPosition(), 0, null);
		}
		return result ? ctx.left : null;
	}

	private <E extends Tree<E>> boolean exec(ParserMachineContext<E> ctx, MozInst inst) {
		MozInst cur = inst;
		try {
			while (true) {
				cur = cur.exec(ctx);
			}
		} catch (TerminationException e) {
			return e.status;
		}
	}

	public abstract Object exec(ParserInstance context);

	/* ProductionCode */

	protected final HashMap<String, ProductionCode<T>> funcMap;

	public static class ProductionCode<T extends Instruction> {
		private T compiled;

		public ProductionCode(T inst) {
			this.compiled = inst;
		}

		public void setCompiled(T inst) {
			this.compiled = inst;
		}

		public final T getCompiled() {
			return compiled;
		}
	}

	protected int getCompiledProductionSize() {
		return funcMap.size();
	}

	public ProductionCode<T> getProductionCode(Production p) {
		return funcMap.get(p.getUniqueName());
	}

	public void setProductionCode(Production p, ProductionCode<T> f) {
		funcMap.put(p.getUniqueName(), f);
	}

	/* MemoPoint */

	protected Map<String, MemoPoint> memoPointMap;

	private static class Score {
		Production p;
		Typestate ts;
		double score;

		Score(Production p, Typestate ts, int score, double factor) {
			this.p = p;
			this.ts = ts;
			this.score = score * factor;
		}
	}

	public void initMemoPoint(ParserStrategy strategy) {
		TypestateAnalyzer typestate = Typestate.newAnalyzer();
		memoPointMap = new HashMap<>();
		NonterminalReference refs = Productions.countNonterminalReference(grammar);
		ArrayList<Score> l = new ArrayList<>();
		for (Production p : grammar) {
			String uname = p.getUniqueName();
			Typestate ts = typestate.inferTypestate(p);
			if (ts != Typestate.TreeMutation) {
				l.add(new Score(p, ts, refs.count(uname), ts == Typestate.Unit ? 1 : 1 * strategy.TreeFactor));
			}
		}
		int c = 0;
		int limits = (int) (l.size() * strategy.MemoLimit);
		l.sort((s, s2) -> (int) (s2.score - s.score));
		for (Score s : l) {
			c++;
			if (c < limits && s.score >= 3 * strategy.TreeFactor) {
				Production p = s.p;
				String uname = p.getUniqueName();
				MemoPoint memoPoint = new MemoPoint(memoPointMap.size(), uname, p.getExpression(), s.ts, false);
				memoPointMap.put(uname, memoPoint);
				Verbose.println("MomoPoint(%d): %s score=%f", memoPoint.id, uname, s.score);
			}
		}

	}

	public final MemoPoint getMemoPoint(String uname) {
		if (memoPointMap != null) {
			return memoPointMap.get(uname);
		}
		return null;
	}

	public final int getMemoPointSize() {
		return memoPointMap != null ? memoPointMap.size() : 0;
	}

	public final void dumpMemoPoints() {
		if (memoPointMap != null) {
			Verbose.println("ID\tPEG\tCount\tHit\tFail\tMean");
			for (String key : memoPointMap.keySet()) {
				MemoPoint p = memoPointMap.get(key);
				String s = String.format("%d\t%s\t%d\t%f\t%f\t%f", p.id, p.label, p.count(), p.hitRatio(), p.failHitRatio(), p.meanLength());
				Verbose.println(s);
			}
			Verbose.println("");
		}
	}

	/* Coverage */
	private CoverageProfiler prof;

	public void initCoverage(ParserStrategy strategy) {
		prof = strategy.getCoverageProfiler();
	}

	public MozInst compileCoverage(String label, boolean start, MozInst next) {
		if (prof != null) {
			return prof.compileCoverage(label, start, next);
		}
		return next;
	}

}
