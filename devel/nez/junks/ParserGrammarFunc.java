package nez.junks;

import nez.lang.Expression;
import nez.lang.Production;
import nez.parser.MemoPoint;
import nez.parser.ParserCode.ProductionCode;
import nez.parser.vm.MozInst;

public class ParserGrammarFunc extends ProductionCode<MozInst> {
	String name;
	// Production grammarProduction;
	Production parserProduction;

	int refcount;
	boolean inlining;
	boolean state;
	MemoPoint memoPoint;

	ParserGrammarFunc(String uname, Production p, Production pp, int init) {
		super(null);
		this.name = uname;
		this.refcount = 0;
		// this.grammarProduction = p;
		this.parserProduction = pp;
		this.refcount = init;
	}

	public Expression getExpression() {
		return parserProduction.getExpression();
	}

	public void incCount() {
		this.refcount++;
	}

	public void resetCount() {
		this.refcount = 0;
	}

	public final int getCount() {
		return refcount;
	}

	public final MemoPoint getMemoPoint() {
		return memoPoint;
	}

	public final boolean isStateful() {
		return state;
	}

	public final boolean isInlined() {
		return inlining;
	}
}