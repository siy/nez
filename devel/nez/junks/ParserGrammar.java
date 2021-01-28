package nez.junks;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import nez.lang.ByteConsumption;
import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.Production;
import nez.lang.Typestate;
import nez.lang.Typestate.TypestateAnalyzer;
import nez.parser.MemoPoint;
import nez.parser.ParserStrategy;
import nez.util.Verbose;

public class ParserGrammar extends Grammar {
	HashMap<String, ParserGrammarFunc> funcMap;
	public List<MemoPoint> memoPointList;

	ParserGrammar(Production start, ParserStrategy strategy, TreeMap<String, Boolean> boolMap) {
		this.funcMap = new HashMap<>();
		// new MozGrammarChecker(this, boolMap, start, strategy);
	}

	/* Consumed */

	private final ByteConsumption consumed = new ByteConsumption();

	public final boolean isConsumed(Production p) {
		return consumed.isConsumed(p);
	}

	public final boolean isConsumed(Expression e) {
		return consumed.isConsumed(e);
	}

	/* Typestate */

	private final TypestateAnalyzer typestate = Typestate.newAnalyzer();

	public final Typestate typeState(Production p) {
		return typestate.inferTypestate(p);
	}

	public final Typestate typeState(Expression e) {
		return typestate.inferTypestate(e);
	}

	/* Acceptance */

	void checkInlining(ParserGrammarFunc f) {
	}

	void checkMemoizing(ParserGrammarFunc f) {
		if (f.inlining || f.memoPoint != null) {
			return;
		}
		Production p = f.parserProduction;
		if (f.refcount > 1 && typeState(p) != Typestate.TreeMutation) {
			int memoId = memoPointList.size();
			f.memoPoint = new MemoPoint(memoId, p.getLocalName(), f.getExpression(), typeState(p), false); // FIXME
			// p.isContextual());
			memoPointList.add(f.memoPoint);
			if (Verbose.PackratParsing) {
				Verbose.println("MemoPoint: " + f.memoPoint + " ref=" + f.refcount + " typestate? " + typeState(p));
			}
		}
	}

}
