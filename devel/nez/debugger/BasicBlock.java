package nez.debugger;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
	String name;
	int codePoint;
	List<DebugVMInstruction> insts;
	List<BasicBlock> preds;
	List<BasicBlock> succs;

	public BasicBlock() {
		this.insts = new ArrayList<>();
		this.preds = new ArrayList<>();
		this.succs = new ArrayList<>();
	}

	public DebugVMInstruction get(int index) {
		return insts.get(index);
	}

	public DebugVMInstruction getStartInstruction() {
		BasicBlock bb = this;
		while (bb.size() == 0) {
			bb = bb.getSingleSuccessor();
		}
		return bb.get(0);
	}

	public DebugVMInstruction append(DebugVMInstruction inst) {
		insts.add(inst);
		return inst;
	}

	public BasicBlock add(int index, DebugVMInstruction inst) {
		insts.add(index, inst);
		return this;
	}

	public int size() {
		return insts.size();
	}

	public void stringfy(StringBuilder sb) {
		for (int i = 0; i < size(); i++) {
			sb.append("  ");
			get(i).stringfy(sb);
			sb.append("\n");
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BasicBlock getSingleSuccessor() {
		return succs.get(0);
	}

	public void setSingleSuccessor(BasicBlock bb) {
		succs.add(0, bb);
	}

	public void setFailSuccessor(BasicBlock bb) {
		succs.add(1, bb);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (DebugVMInstruction inst : insts) {
			builder.append(inst);
			builder.append("\n");
		}
		return builder.toString();
	}
}
