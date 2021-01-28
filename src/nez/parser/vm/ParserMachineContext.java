package nez.parser.vm;

import nez.ast.Source;
import nez.ast.Symbol;
import nez.ast.Tree;
import nez.parser.ParserContext;

public class ParserMachineContext<T extends Tree<T>> extends ParserContext<T> {

	public ParserMachineContext(String s, T proto) {
		super(s, proto);
	}

	public ParserMachineContext(Source source, T proto) {
		super(source, proto);
		initVM();
	}

	@Override
	public final boolean eof() {
		return source.eof(pos);
	}

	@Override
	public final int read() {
		return source.byteAt(pos++);
	}

	@Override
	public final int prefetch() {
		return source.byteAt(pos);
	}

	@Override
	public final boolean match(byte[] utf8) {
		if (source.match(pos, utf8)) {
			move(utf8.length);
			return true;
		}
		return false;
	}

	@Override
	public final byte[] subByte(int start, int end) {
		return source.subByte(start, end);
	}

	@Override
	public final byte byteAt(int pos) {
		return (byte) source.byteAt(pos);
	}

	private int head_pos;

	@Override
	public final void back(int pos) {
		if (head_pos < this.pos) {
			this.head_pos = this.pos;
		}
		this.pos = pos;
	}

	public final long getPosition() {
		return pos;
	}

	public final long getMaximumPosition() {
		return head_pos;
	}

	public final void setPosition(long pos) {
		this.pos = (int) pos;
	}

	// ----------------------------------------------------------------------

	public static class StackData {
		public Object ref;
		public int value;
	}

	private static final int StackSize = 64;
	private StackData[] stacks;
	private int usedStackTop;
	private int catchStackTop;

	public final void initVM() {
		this.stacks = new StackData[StackSize];
		for (int i = 0; i < StackSize; i++) {
			stacks[i] = new StackData();
		}
		stacks[0].ref = null;
		stacks[0].value = 0;
		stacks[1].ref = new Moz86.Exit(false);
		stacks[1].value = pos;
		stacks[2].ref = saveLog();
		stacks[2].value = saveSymbolPoint();
		stacks[3].ref = new Moz86.Exit(true);
		stacks[3].value = 0;
		this.catchStackTop = 0;
		this.usedStackTop = 3;
	}

	public final StackData getUsedStackTop() {
		return stacks[usedStackTop];
	}

	public final StackData newUnusedStack() {
		usedStackTop++;
		if (stacks.length == usedStackTop) {
			StackData[] newstack = new StackData[stacks.length * 2];
			System.arraycopy(stacks, 0, newstack, 0, stacks.length);
			for (int i = stacks.length; i < newstack.length; i++) {
				newstack[i] = new StackData();
			}
			stacks = newstack;
		}
		return stacks[usedStackTop];
	}

	public final StackData popStack() {
		StackData s = stacks[usedStackTop];
		usedStackTop--;
		// assert(this.catchStackTop <= this.usedStackTop);
		return s;
	}

	// Instruction

	public final void xPos() {
		StackData s = newUnusedStack();
		s.value = pos;
	}

	public final int xPPos() {
		StackData s = popStack();
		return s.value;
	}

	public final void xBack() {
		StackData s = popStack();
		back(s.value);
	}

	public final void xCall(String name, MozInst jump) {
		StackData s = newUnusedStack();
		s.ref = jump;
	}

	public final MozInst xRet() {
		StackData s = popStack();
		return (MozInst) s.ref;
	}

	public final void xAlt(MozInst failjump/* op.failjump */) {
		StackData s0 = newUnusedStack();
		StackData s1 = newUnusedStack();
		StackData s2 = newUnusedStack();
		s0.value = catchStackTop;
		catchStackTop = usedStackTop - 2;
		s1.ref = failjump;
		s1.value = pos;
		s2.value = saveLog();
		s2.ref = saveSymbolPoint();
	}

	public final void xSucc() {
		StackData s0 = stacks[catchStackTop];
		// StackData s1 = stacks[catchStackTop + 1];
		usedStackTop = catchStackTop - 1;
		catchStackTop = s0.value;
	}

	public final int xSuccPos() {
		StackData s0 = stacks[catchStackTop];
		StackData s1 = stacks[catchStackTop + 1];
		usedStackTop = catchStackTop - 1;
		catchStackTop = s0.value;
		return s1.value;
	}

	public final MozInst xFail() {
		StackData s0 = stacks[catchStackTop];
		StackData s1 = stacks[catchStackTop + 1];
		StackData s2 = stacks[catchStackTop + 2];
		usedStackTop = catchStackTop - 1;
		catchStackTop = s0.value;
		if (s1.value < pos) {
			back(s1.value);
		}
		backLog(s2.value);
		backSymbolPoint((Integer) s2.ref); // FIXME slow
		assert (s1.ref != null);
		return (MozInst) s1.ref;
	}

	public final MozInst xStep(MozInst next) {
		StackData s1 = stacks[catchStackTop + 1];
		if (s1.value == pos) {
			return xFail();
		}
		s1.value = pos;
		StackData s2 = stacks[catchStackTop + 2];
		s2.value = saveLog();
		s2.ref = saveSymbolPoint(); // FIXME slow
		return next;
	}

	public final void xTPush() {
		StackData s = newUnusedStack();
		s.ref = left;
		s.value = saveLog();
	}

	@SuppressWarnings("unchecked")
	public final void xTLink(Symbol label) {
		StackData s = popStack();
		backLog(s.value);
		linkTree((T) s.ref, label);
		this.left = (T) s.ref;
	}

	@SuppressWarnings("unchecked")
	public final void xTPop() {
		StackData s = popStack();
		backLog(s.value);
		this.left = (T) s.ref;
	}

	public final void xSOpen() {
		StackData s = newUnusedStack();
		s.value = saveSymbolPoint();
	}

	public final void xSClose() {
		StackData s = popStack();
		backSymbolPoint(s.value);
	}

	/* ----------------------------------------------------------------- */
	/* Trap */

	public final void trap(int uid) {

	}

}
