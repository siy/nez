package nez.lang;

import nez.ast.SourcePosition;
import nez.util.UList;
import nez.util.UMap;
import nez.vm.Instruction;
import nez.vm.NezCompiler;

public class Match extends Unary {
	Match(SourcePosition s, Expression inner) {
		super(s, inner);
	}
	
	@Override
	public String getPredicate() { 
		return "~";
	}
	
	@Override
	public String key() { 
		return "~";
	}
	
	@Override
	protected final void format(StringBuilder sb) {
		this.formatUnary(sb, "~", inner);
	}

	@Override
	public Expression reshape(GrammarReshaper m) {
		return m.reshapeMatch(this);
	}

	@Override
	public boolean isConsumed(Stacker stacker) {
		return this.inner.isConsumed(stacker);
	}

	@Override
	public boolean checkAlwaysConsumed(GrammarChecker checker, String startNonTerminal, UList<String> stack) {
		return this.inner.checkAlwaysConsumed(checker, startNonTerminal, stack);
	}
	@Override
	public int inferTypestate(UMap<String> visited) {
		return Typestate.BooleanType;
	}
	@Override
	public short acceptByte(int ch, int option) {
		return this.inner.acceptByte(ch, option);
	}
	@Override
	public Instruction encode(NezCompiler bc, Instruction next, Instruction failjump) {
		return this.inner.encode(bc, next, failjump);
	}

	@Override
	protected int pattern(GEP gep) {
		return inner.pattern(gep);
	}

	@Override
	protected void examplfy(GEP gep, StringBuilder sb, int p) {
		this.inner.examplfy(gep, sb, p);
	}

}