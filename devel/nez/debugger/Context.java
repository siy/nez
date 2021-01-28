package nez.debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import nez.ast.Source;
import nez.ast.Symbol;
import nez.lang.Bytes;
import nez.lang.Expression;
import nez.parser.vm.SymbolTable;

public abstract class Context implements Source {
	long pos;
	long longest_pos;
	boolean result;
	StackEntry[] stack;
	StackEntry[] callStack;
	StackEntry[] longestTrace;
	int StackTop;
	int callStackTop;
	int longestStackTop;
	private static final int StackSize = 128;
	boolean checkAlternativeMode;

	public final void initContext() {
		this.result = true;
		this.lastAppendedLog = new ASTLog();
		this.symbolTable = new SymbolTable();
		this.stack = new StackEntry[StackSize];
		this.callStack = new StackEntry[StackSize];
		this.longestTrace = new StackEntry[StackSize];
		for (int i = 0; i < stack.length; i++) {
			stack[i] = new StackEntry();
			callStack[i] = new StackEntry();
			longestTrace[i] = new StackEntry();
		}
		callStack[0].jump = new Iexit(null);
		callStack[0].failjump = new Iexit(null);
		stack[0].mark = lastAppendedLog;
		this.StackTop = 0;
		this.callStackTop = 0;
		this.treeTransducer = new CommonTreeTransducer();
		this.WS = Bytes.newMap(false);
		WS[9] = true;
		WS[10] = true;
		WS[13] = true;
		WS[32] = true;
	}

	public final long getPosition() {
		return pos;
	}

	final void setPosition(long pos) {
		this.pos = pos;
	}

	public boolean hasUnconsumed() {
		return pos != length();
	}

	public final boolean consume(int length) {
		this.pos += length;
		return true;
	}

	public final void rollback(long pos) {
		if (longest_pos <= this.pos) {
			this.longest_pos = this.pos;
			for (int i = 0; i < callStack.length; i++) {
				longestTrace[i].pos = callStack[i].pos;
				longestTrace[i].val = callStack[i].val;
			}
			this.longestStackTop = callStackTop;
		}
		this.pos = pos;
		if (failOverList.size() > 0) {
			ArrayList<FailOverInfo> list = new ArrayList<>();
			for (FailOverInfo fover : failOverList) {
				if (fover.fail_pos <= this.pos) {
					list.add(fover);
				}
			}
			this.failOverList = list;
		}
	}

	public final StackEntry newStackEntry() {
		this.StackTop++;
		if (StackTop == stack.length) {
			StackEntry[] newStack = new StackEntry[stack.length * 2];
			System.arraycopy(stack, 0, newStack, 0, stack.length);
			for (int i = stack.length; i < newStack.length; i++) {
				newStack[i] = new StackEntry();
			}
			this.stack = newStack;
		}
		return stack[StackTop];
	}

	public final StackEntry newCallStackEntry() {
		this.callStackTop++;
		if (callStackTop == callStack.length) {
			StackEntry[] newStack = new StackEntry[callStack.length * 2];
			StackEntry[] newTrace = new StackEntry[longestTrace.length * 2];
			System.arraycopy(callStack, 0, newStack, 0, callStack.length);
			System.arraycopy(longestTrace, 0, newTrace, 0, longestTrace.length);
			for (int i = callStack.length; i < newStack.length; i++) {
				newStack[i] = new StackEntry();
				newTrace[i] = new StackEntry();
			}
			this.callStack = newStack;
			this.longestTrace = newTrace;
		}
		return callStack[callStackTop];
	}

	public final StackEntry popStack() {
		return stack[this.StackTop--];
	}

	public final StackEntry popCallStack() {
		return callStack[this.callStackTop--];
	}

	public final StackEntry peekStack() {
		return stack[StackTop];
	}

	public final String getSyntaxErrorMessage() {
		return formatPositionLine("error", longest_pos, "syntax error");
	}

	public final String getUnconsumedMessage() {
		return formatPositionLine("unconsumed", pos, "");
	}

	public final DebugVMInstruction opIexit(Iexit inst) throws MachineExitException {
		throw new MachineExitException(result);
	}

	public final DebugVMInstruction opIcall(Icall inst) {
		StackEntry top = newCallStackEntry();
		top.jump = inst.jump;
		top.failjump = inst.failjump;
		top.val = inst.ne;
		top.pos = pos;
		// top.stackTop = this.StackTop;
		return inst.next;
	}

	public final DebugVMInstruction opIret(Iret inst) {
		StackEntry top = popCallStack();

		if (result) {
			return top.jump;
		}
		return top.failjump;
	}

	public final DebugVMInstruction opIjump(Ijump inst) {
		return inst.jump;
	}

	public final DebugVMInstruction opIiffail(Iiffail inst) {
		if (result) {
			return inst.next;
		}
		return inst.jump;
	}

	public final DebugVMInstruction opIpush(Ipush inst) {
		StackEntry top = newStackEntry();
		top.pos = pos;
		top.mark = lastAppendedLog;
		return inst.next;
	}

	public final DebugVMInstruction opIpop(Ipop inst) {
		popStack();
		return inst.next;
	}

	public final DebugVMInstruction opIpeek(Ipeek inst) {
		StackEntry top = peekStack();
		rollback(top.pos);
		if (top.mark != lastAppendedLog && !result) {
			logAbort(top.mark, true);
		}
		return inst.next;
	}

	public final DebugVMInstruction opIsucc(Isucc inst) {
		this.result = true;
		return inst.next;
	}

	public final DebugVMInstruction opIfail(Ifail inst) {
		this.result = false;
		return inst.next;
	}

	static class FailOverInfo {
		final long fail_pos;
		Expression e;

		public FailOverInfo(long pos, Expression e) {
			this.fail_pos = pos;
			this.e = e;
		}
	}

	boolean failOver;
	boolean[] WS;
	ArrayList<FailOverInfo> failOverList = new ArrayList<>();
	DebugVMInstruction matchInst;

	public final DebugVMInstruction opIchar(Ichar inst) {
		int ch = byteAt(pos);
		if (ch == inst.byteChar) {
			consume(1);
			this.matchInst = inst;
			return inst.next;
		}
		if (failOver && !matchInst.equals(inst)) {
			this.matchInst = inst;
			if (WS[ch]) {
				failOverList.add(new FailOverInfo(pos, inst.expr));
				consume(1);
				return inst.next;
			}
		}
		this.matchInst = inst;
		this.result = false;
		return inst.jump;
	}

	public final DebugVMInstruction opIstr(Istr inst) {
		if (match(pos, inst.utf8)) {
			this.matchInst = inst;
			consume(inst.utf8.length);
			return inst.next;
		}
		if (failOver && !matchInst.equals(inst)) {
			this.matchInst = inst;
			if (WS[byteAt(pos)]) {
				failOverList.add(new FailOverInfo(pos, inst.expr));
				consume(1);
				return inst.next;
			}
		}
		this.matchInst = inst;
		this.result = false;
		return inst.jump;
	}

	public final DebugVMInstruction opIcharclass(Icharclass inst) {
		int byteChar = byteAt(pos);
		if (inst.byteMap[byteChar]) {
			this.matchInst = inst;
			consume(1);
			return inst.next;
		}
		if (failOver && !matchInst.equals(inst)) {
			this.matchInst = inst;
			if (WS[byteChar]) {
				failOverList.add(new FailOverInfo(pos, inst.expr));
				consume(1);
				return inst.next;
			}
		}
		this.matchInst = inst;
		this.result = false;
		return inst.jump;
	}

	public final DebugVMInstruction opIany(Iany inst) {
		if (hasUnconsumed()) {
			consume(1);
			return inst.next;
		}
		this.result = false;
		return inst.jump;
	}

	/*
	 * AST Construction Part
	 */

	private TreeTransducer treeTransducer;
	private Object left;
	private ASTLog lastAppendedLog;
	private ASTLog unusedDataLog;

	public Object getLeftObject() {
		return left;
	}

	private void pushDataLog(int type, long pos, Object value) {
		ASTLog l;
		if (unusedDataLog == null) {
			l = new ASTLog();
		} else {
			l = unusedDataLog;
			this.unusedDataLog = l.next;
		}
		l.type = type;
		l.pos = pos;
		l.value = value;
		l.prev = lastAppendedLog;
		l.next = null;
		lastAppendedLog.next = l;
		lastAppendedLog = l;
	}

	public final Object logCommit(ASTLog start) {
		assert(start.type == ASTLog.LazyNew);
		long spos = start.pos, epos = spos;
		Symbol tag = null;
		Object value = null;
		int objectSize = 0;
		Object left = null;
		for (ASTLog cur = start.next; cur != null; cur = cur.next) {
			switch (cur.type) {
			case ASTLog.LazyLink:
				int index = (int) cur.pos;
				if (index == -1) {
					cur.pos = objectSize;
					objectSize++;
				} else if (!(index < objectSize)) {
					objectSize = index + 1;
				}
				break;
			case ASTLog.LazyCapture:
				epos = cur.pos;
				break;
			case ASTLog.LazyTag:
				tag = (Symbol) cur.value;
				break;
			case ASTLog.LazyReplace:
				value = cur.value;
				break;
			case ASTLog.LazyLeftNew:
				left = commitNode(start, cur, spos, epos, objectSize, left, tag, value);
				start = cur;
				spos = cur.pos;
				epos = spos;
				tag = null;
				value = null;
				objectSize = 1;
				break;
			}
		}
		return commitNode(start, null, spos, epos, objectSize, left, tag, value);
	}

	private Object commitNode(ASTLog start, ASTLog end, long spos, long epos, int objectSize, Object left, Symbol tag, Object value) {
		Object newnode = treeTransducer.newNode(tag, this, spos, epos, objectSize, value);
		if (left != null) {
			treeTransducer.link(newnode, 0, tag, left); // FIXME
		}
		if (objectSize > 0) {
			for (ASTLog cur = start.next; cur != end; cur = cur.next) {
				if (cur.type == ASTLog.LazyLink) {
					// System.out.println("Link >> " + cur);
					treeTransducer.link(newnode, (int) cur.pos, tag, cur.value); // FIXME
				}
			}
		}
		// System.out.println("Commit >> " + newnode);
		return treeTransducer.commit(newnode);
	}

	public final void logAbort(ASTLog checkPoint, boolean isFail) {
		assert(checkPoint != null);
		// System.out.println("Abort >> " + checkPoint);
		lastAppendedLog.next = unusedDataLog;
		this.unusedDataLog = checkPoint.next;
		unusedDataLog.prev = null;
		this.lastAppendedLog = checkPoint;
		lastAppendedLog.next = null;
	}

	public final Object newTopLevelNode() {
		for (ASTLog cur = lastAppendedLog; cur != null; cur = cur.prev) {
			if (cur.type == ASTLog.LazyNew) {
				this.left = logCommit(cur);
				logAbort(cur.prev, false);
				return left;
			}
		}
		return null;
	}

	boolean ASTConstruction = true;

	public final DebugVMInstruction opInew(Inew inst) {
		if (ASTConstruction) {
			pushDataLog(ASTLog.LazyNew, pos, null);
		}
		return inst.next;
	}

	public final DebugVMInstruction opIleftnew(Ileftnew inst) {
		if (ASTConstruction) {
			pushDataLog(ASTLog.LazyLeftNew, pos + inst.index, null);
		}
		return inst.next;
	}

	public final DebugVMInstruction opIcapture(Icapture inst) {
		if (ASTConstruction) {
			pushDataLog(ASTLog.LazyCapture, pos, null);
		}
		return inst.next;
	}

	public final DebugVMInstruction opImark(Imark inst) {
		if (ASTConstruction) {
			StackEntry top = newStackEntry();
			top.mark = lastAppendedLog;
		}
		return inst.next;
	}

	public final DebugVMInstruction opItag(Itag inst) {
		if (ASTConstruction) {
			pushDataLog(ASTLog.LazyTag, 0, inst.tag);
		}
		return inst.next;
	}

	public final DebugVMInstruction opIreplace(Ireplace inst) {
		if (ASTConstruction) {
			pushDataLog(ASTLog.LazyReplace, 0, inst.value);
		}
		return inst.next;
	}

	public final DebugVMInstruction opIcommit(Icommit inst) {
		if (ASTConstruction) {
			StackEntry top = popStack();
			if (top.mark.next != null) {
				Object child = logCommit(top.mark.next);
				logAbort(top.mark, false);
				if (child != null) {
					pushDataLog(ASTLog.LazyLink, inst.index, child);
				}
				this.left = child;
			}
		}
		return inst.next;
	}

	public final DebugVMInstruction opIabort(Iabort inst) {
		if (ASTConstruction) {
			StackEntry top = popStack();
			if (top.mark != lastAppendedLog) {
				logAbort(top.mark, true);
			}
		}
		return inst.next;
	}

	/*
	 * Symbol Table Part
	 */

	private SymbolTable symbolTable;

	public final DebugVMInstruction opIdef(Idef inst) {
		StackEntry top = popStack();
		byte[] captured = subByte(top.pos, pos);
		symbolTable.addSymbol(inst.tableName, captured);
		return inst.next;
	}

	public final DebugVMInstruction opIis(Iis inst) {
		byte[] t = symbolTable.getSymbol(inst.tableName);
		if (t != null && match(pos, t)) {
			consume(t.length);
			return inst.next;
		}
		return inst.jump;
	}

	public final DebugVMInstruction opIisa(Iisa inst) {
		StackEntry top = popStack();
		byte[] captured = subByte(top.pos, pos);
		if (symbolTable.contains(inst.tableName, captured)) {
			consume(captured.length);
			return inst.next;
		}
		return inst.jump;
	}

	public final DebugVMInstruction opIexists(Iexists inst) {
		byte[] t = symbolTable.getSymbol(inst.tableName);
		return t != null ? inst.next : inst.jump;
	}

	public final DebugVMInstruction opIbeginscope(Ibeginscope inst) {
		StackEntry top = newStackEntry();
		top.pos = symbolTable.saveSymbolPoint();
		return inst.next;
	}

	public final DebugVMInstruction opIbeginlocalscope(Ibeginlocalscope inst) {
		StackEntry top = newStackEntry();
		top.pos = symbolTable.saveSymbolPoint();
		return inst.next;
	}

	public final DebugVMInstruction opIendscope(Iendscope inst) {
		StackEntry top = popStack();
		symbolTable.backSymbolPoint((int) top.pos);
		return inst.next;
	}

	HashMap<Expression, Alt> altJumpMap = new HashMap<>();
	Stack<AltResult> altStack = new Stack<>();

	public final DebugVMInstruction opIaltstart(Ialtstart inst) {
		altStack.push(new AltResult());
		return inst.next;
	}

	public final DebugVMInstruction opIalt(Ialt inst) {
		if (altJumpMap.containsKey(inst.expr)) {
			AltResult r = altStack.peek();
			if (r.succ) {
				r.pos = pos;
				this.pos = peekStack().pos;
			}
		}

		return inst.next;
	}

	public final DebugVMInstruction opIaltend(Ialtend inst) {
		if (altJumpMap.containsKey(inst.c)) {
			Alt alt = altJumpMap.get(inst.c);
			AltResult r = altStack.peek();
			if (r.succ) {
				if (pos >= r.pos) {
					System.out.println("Unreachable Choice: accept(" + r.pos + ") cur(" + pos + ") \n" + inst.c + "\n\t-> " + inst.getExpression());
				}
			} else {
				r.succ = true;
				this.ASTConstruction = false;
				return alt.jump;
			}
		}

		return inst.next;
	}

	public final DebugVMInstruction opIaltfin(Ialtfin inst) {
		AltResult r = altStack.pop();
		if (altJumpMap.containsKey(inst.expr)) {
			if (r.succ) {
				this.pos = r.pos;
				this.ASTConstruction = true;
			}
		}
		return inst.next;
	}

}

class ASTLog {
	static final int LazyLink = 0;
	static final int LazyCapture = 1;
	static final int LazyTag = 2;
	static final int LazyReplace = 3;
	static final int LazyLeftNew = 4;
	static final int LazyNew = 5;

	int type;
	long pos;
	Object value;
	ASTLog prev;
	ASTLog next;

	int id() {
		if (prev == null) {
			return 0;
		}
		return prev.id() + 1;
	}

	@Override
	public String toString() {
		switch (type) {
		case LazyLink:
			return "[" + id() + "] link<" + pos + "," + value + ">";
		case LazyCapture:
			return "[" + id() + "] cap<pos=" + pos + ">";
		case LazyTag:
			return "[" + id() + "] tag<" + value + ">";
		case LazyReplace:
			return "[" + id() + "] replace<" + value + ">";
		case LazyNew:
			return "[" + id() + "] new<pos=" + pos + ">" + "   ## " + value;
		case LazyLeftNew:
			return "[" + id() + "] leftnew<pos=" + pos + "," + value + ">";
		}
		return "[" + id() + "] nop";
	}
}

class StackEntry implements Cloneable {
	DebugVMInstruction jump;
	DebugVMInstruction failjump;
	long pos;
	ASTLog mark;
	Object val;
	int stackTop;
}

class Alt {
	final DebugVMInstruction jump;
	final int index;

	public Alt(int index, DebugVMInstruction jump) {
		this.jump = jump;
		this.index = index;
	}
}

class AltResult {
	long pos;
	boolean succ;

	public AltResult() {
		this.pos = 0;
		this.succ = false;
	}
}
