package nez.lang.expr;

import nez.ast.SourcePosition;
import nez.lang.Expression;
import nez.lang.ExpressionTransducer;
import nez.lang.PossibleAcceptance;
import nez.lang.Typestate;
import nez.lang.Visa;
import nez.vm.Instruction;
import nez.vm.NezEncoder;

public class Xdefindent extends Term {
	Xdefindent(SourcePosition s) {
		super(s);
	}

	@Override
	public final boolean equalsExpression(Expression o) {
		return (o instanceof Xdef);
	}

	@Override
	public boolean isConsumed() {
		return false;
	}

	@Override
	public int inferTypestate(Visa v) {
		return Typestate.BooleanType;
	}

	@Override
	public short acceptByte(int ch) {
		return PossibleAcceptance.Unconsumed;
	}

	@Override
	public Instruction encode(NezEncoder bc, Instruction next, Instruction failjump) {
		return bc.encodeXdefindent(this, next, failjump);
	}

	@Override
	public Expression reshape(ExpressionTransducer m) {
		return m.reshapeUndefined(this);
	}

}