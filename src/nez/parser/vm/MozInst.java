package nez.parser.vm;

import nez.lang.Expression;
import nez.parser.Instruction;
import nez.parser.TerminationException;

public abstract class MozInst implements Instruction {
	public int id;
	public boolean joinPoint;
	public final byte opcode;
	// protected Expression e;
	public MozInst next;

	public MozInst(byte opcode, Expression e, MozInst next) {
		this.opcode = opcode;
		// this.e = e;
		this.id = -1;
		this.next = next;
	}

	public final boolean isIncrementedNext() {
		if (next != null) {
			return next.id == id + 1;
		}
		return true; // RET or instructions that are unnecessary to go next
	}

	MozInst branch() {
		return null;
	}

	public abstract MozInst execMoz(MozMachine sc) throws TerminationException;

	public abstract MozInst exec(ParserMachineContext<?> sc) throws TerminationException;

	protected static String label(MozInst inst) {
		return "L" + inst.id;
	}

	public final String getName() {
		return getClass().getSimpleName();
	}

	public Expression getExpression() {
		return null;// this.e;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Moz86.stringfy(this, sb);
		return sb.toString();
	}

	public abstract void visit(InstructionVisitor v);

}
