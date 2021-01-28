package nez.debugger;

import nez.junks.ParserGrammar;
import nez.lang.Expression;
import nez.lang.Nez;
import nez.lang.Production;

public class IRBuilder {
	private BasicBlock curBB;
	private final Module module;
	private Function func;

	public IRBuilder(Module m) {
		this.module = m;
	}

	public Module buildInstructionSequence() {
		int codeIndex = 0;
		for (int i = 0; i < module.size(); i++) {
			Function func = module.get(i);
			for (int j = 0; j < func.size(); j++) {
				BasicBlock bb = func.get(j);
				bb.codePoint = codeIndex;
				codeIndex += bb.size();
			}
		}
		for (Function f : module.funcList) {
			DebugVMInstruction prev = null;
			for (BasicBlock bb : f.bbList) {
				for (DebugVMInstruction inst : bb.insts) {
					if (prev != null && prev.next == null) {
						prev.next = inst;
					}
					if (inst.op.equals(Opcode.Icall)) {
						Icall call = (Icall) inst;
						Function callFunc = module.get(call.ne.getLocalName());
						callFunc.setCaller(f);
						call.setJump(callFunc.get(0).codePoint);
						call.next = callFunc.getStartInstruction();
						bb.setSingleSuccessor(call.jumpBB);
						call.jump = call.jumpBB.getStartInstruction();
						bb.setFailSuccessor(call.failBB);
						call.failjump = call.failBB.getStartInstruction();
					} else if (inst instanceof JumpInstruction) {
						JumpInstruction jinst = (JumpInstruction) inst;
						BasicBlock jbb = jinst.jumpBB;
						jinst.jump = jbb.getStartInstruction();
					}
					prev = inst;
				}
			}
		}
		// this.dumpLastestCode();
		return module;
	}

	private void dumpLastestCode() {
		int codeIndex = 0;
		for (int i = 0; i < module.size(); i++) {
			Function func = module.get(i);
			for (int j = 0; j < func.size(); j++) {
				BasicBlock bb = func.get(j);
				for (int k = 0; k < bb.size(); k++) {
					DebugVMInstruction inst = bb.get(k);
					System.out.println("[" + codeIndex + "] " + inst);
					codeIndex++;
				}
			}
		}
	}

	public Module getModule() {
		return module;
	}

	public void setGrammar(ParserGrammar g) {
		module.setGrammar(g);
	}

	public Function getFunction() {
		return func;
	}

	public void setFunction(Function func) {
		module.append(func);
		this.func = func;
	}

	public void setInsertPoint(BasicBlock bb) {
		func.append(bb);
		bb.setName("bb" + func.size());
		if (curBB != null) {
			if (bb.size() != 0) {
				DebugVMInstruction last = curBB.get(curBB.size() - 1);
				if (!(last.op.equals(Opcode.Ijump) || last.op.equals(Opcode.Icall))) {
					curBB.setSingleSuccessor(bb);
				}
			} else {
				curBB.setSingleSuccessor(bb);
			}
		}
		this.curBB = bb;
	}

	public void setCurrentBB(BasicBlock bb) {
		this.curBB = bb;
	}

	public BasicBlock getCurrentBB() {
		return curBB;
	}

	static class FailureBB {
		BasicBlock fbb;
		FailureBB prev;

		public FailureBB(BasicBlock bb, FailureBB prev) {
			this.fbb = bb;
			this.prev = prev;
		}
	}

	FailureBB fLabel;

	public void pushFailureJumpPoint(BasicBlock bb) {
		this.fLabel = new FailureBB(bb, fLabel);
	}

	public BasicBlock popFailureJumpPoint() {
		BasicBlock fbb = fLabel.fbb;
		this.fLabel = fLabel.prev;
		return fbb;
	}

	public BasicBlock jumpFailureJump() {
		return fLabel.fbb;
	}

	public BasicBlock jumpPrevFailureJump() {
		return fLabel.prev.fbb;
	}

	public DebugVMInstruction createIexit(Expression e) {
		return curBB.append(new Iexit(e));
	}

	public DebugVMInstruction createInop(Production e) {
		return curBB.append(new Inop(e));
	}

	public DebugVMInstruction createIcall(nez.lang.NonTerminal e, BasicBlock jump, BasicBlock failjump) {
		return curBB.append(new Icall(e, jump, failjump));
	}

	public DebugVMInstruction createIret(Production e) {
		return curBB.append(new Iret(e));
	}

	public DebugVMInstruction createIjump(Expression e, BasicBlock jump) {
		return curBB.append(new Ijump(e, jump));
	}

	public DebugVMInstruction createIiffail(Expression e, BasicBlock jump) {
		return curBB.append(new Iiffail(e, jump));
	}

	public DebugVMInstruction createIpush(Expression e) {
		return curBB.append(new Ipush(e));
	}

	public DebugVMInstruction createIpop(Expression e) {
		return curBB.append(new Ipop(e));
	}

	public DebugVMInstruction createIpeek(Expression e) {
		return curBB.append(new Ipeek(e));
	}

	public DebugVMInstruction createIsucc(Expression e) {
		return curBB.append(new Isucc(e));
	}

	public DebugVMInstruction createIfail(Expression e) {
		return curBB.append(new Ifail(e));
	}

	public DebugVMInstruction createIchar(Nez.Byte e, BasicBlock jump) {
		return curBB.append(new Ichar(e, jump));
	}

	public DebugVMInstruction createIstr(Expression e, BasicBlock jump, byte[] utf8) {
		return curBB.append(new Istr(e, jump, utf8));
	}

	public DebugVMInstruction createIcharclass(Nez.ByteSet e, BasicBlock jump) {
		return curBB.append(new Icharclass(e, jump));
	}

	public DebugVMInstruction createIcharclass(Expression e, BasicBlock jump, boolean[] byteMap) {
		return curBB.append(new Icharclass(e, jump, byteMap));
	}

	public DebugVMInstruction createIany(Nez.Any e, BasicBlock jump) {
		return curBB.append(new Iany(e, jump));
	}

	public DebugVMInstruction createInew(Expression e) {
		return curBB.append(new Inew(e));
	}

	public DebugVMInstruction createIleftnew(Nez.FoldTree e) {
		return curBB.append(new Ileftnew(e));
	}

	public DebugVMInstruction createIcapture(Expression e) {
		return curBB.append(new Icapture(e));
	}

	public DebugVMInstruction createImark(Expression e) {
		return curBB.append(new Imark(e));
	}

	public DebugVMInstruction createItag(Nez.Tag e) {
		return curBB.append(new Itag(e));
	}

	public DebugVMInstruction createIreplace(Nez.Replace e) {
		return curBB.append(new Ireplace(e));
	}

	public DebugVMInstruction createIcommit(Nez.LinkTree e) {
		return curBB.append(new Icommit(e));
	}

	public DebugVMInstruction createIabort(Expression e) {
		return curBB.append(new Iabort(e));
	}

	public DebugVMInstruction createIdef(Nez.SymbolAction e) {
		return curBB.append(new Idef(e));
	}

	public DebugVMInstruction createIis(Nez.SymbolPredicate e, BasicBlock jump) {
		return curBB.append(new Iis(e, jump));
	}

	public DebugVMInstruction createIisa(Nez.SymbolPredicate e, BasicBlock jump) {
		return curBB.append(new Iisa(e, jump));
	}

	public DebugVMInstruction createIexists(Nez.SymbolExists e, BasicBlock jump) {
		return curBB.append(new Iexists(e, jump));
	}

	public DebugVMInstruction createIbeginscope(Expression e) {
		return curBB.append(new Ibeginscope(e));
	}

	public DebugVMInstruction createIbeginlocalscope(Nez.LocalScope e) {
		return curBB.append(new Ibeginlocalscope(e));
	}

	public DebugVMInstruction createIendscope(Expression e) {
		return curBB.append(new Iendscope(e));
	}

	public DebugVMInstruction createIaltstart(Expression e) {
		return curBB.append(new Ialtstart(e));
	}

	public DebugVMInstruction createIalt(Expression e) {
		return curBB.append(new Ialt(e));
	}

	public DebugVMInstruction createIaltend(Nez.Choice e, boolean last, int index) {
		return curBB.append(new Ialtend(e, last, index));
	}

	public DebugVMInstruction createIaltfin(Expression e) {
		return curBB.append(new Ialtfin(e));
	}
}
