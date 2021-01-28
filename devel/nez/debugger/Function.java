package nez.debugger;

import java.util.ArrayList;
import java.util.List;

import nez.lang.Expression;
import nez.lang.Production;

public class Function {
	String funcName;
	Production rule;
	List<BasicBlock> bbList;
	List<Function> callers;

	public Function(Production rule) {
		this.funcName = rule.getLocalName();
		this.rule = rule;
		this.bbList = new ArrayList<>();
		this.callers = new ArrayList<>();
	}

	public BasicBlock get(int index) {
		return bbList.get(index);
	}

	public void setCaller(Function func) {
		if(!callers.contains(func)) {
			callers.add(func);
		}
	}

	public DebugVMInstruction getStartInstruction() {
		BasicBlock bb = get(0);
		while(bb.size() == 0) {
			bb = bb.getSingleSuccessor();
		}
		return bb.get(0);
	}

	public Function append(BasicBlock bb) {
		bbList.add(bb);
		return this;
	}

	public Function add(int index, BasicBlock bb) {
		bbList.add(index, bb);
		return this;
	}

	public BasicBlock remove(int index) {
		return bbList.remove(index);
	}

	public List<DebugVMInstruction> serchInst(Expression e) {
		List<DebugVMInstruction> ilist = new ArrayList<>();
		for(int i = 0; i < size(); i++) {
			BasicBlock bb = get(i);
			for(int j = 0; j < bb.size(); j++) {
				DebugVMInstruction inst = bb.get(j);
				if(inst.expr.equals(e)) {
					ilist.add(inst);
				}
			}
		}
		return ilist;
	}

	public int size() {
		return bbList.size();
	}

	public int instSize() {
		int size = 0;
		for(int i = 0; i < size(); i++) {
			size += get(i).size();
		}
		return size;
	}

	public int indexOf(BasicBlock bb) {
		return bbList.indexOf(bb);
	}

	public void stringfy(StringBuilder sb) {
		sb.append(funcName).append(":\n");
		for(int i = 0; i < size(); i++) {
			BasicBlock bb = get(i);
			sb.append(bb.name).append(" {\n");
			bb.stringfy(sb);
			sb.append("}\n");
		}
		sb.append("\n");
	}
}
