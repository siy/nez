package nez.dfa;

import java.util.HashSet;

public class LogicVariable extends BooleanExpression {
	int ID;
	byte value;

	public LogicVariable() {
		this.value = -1;
	}

	public LogicVariable(int ID) {
		this.ID = ID;
		this.value = -1;
	}

	public LogicVariable(int ID, byte value) {
		this.ID = ID;
		this.value = value;
	}

	public LogicVariable(int ID, boolean value) {
		this.ID = ID;
		this.value = (byte) (value ? 1 : 0);
	}

	@Override
	public int traverse() {
		if (hasValue()) {
			return getValue() ? 1 : 0;
		}
		return BDD.getNode(new BDDNode(ID, 0, 1));
	}

	@Override
	public LogicVariable deepCopy() {
		return new LogicVariable(ID, value);
	}

	@Override
	public LogicVariable recoverPredicate() {
		return new LogicVariable(((ID < 0) ? -ID : ID), value);
	}

	@Override
	public BooleanExpression assignBooleanValueToLogicVariable(boolean booleanValue, LogicVariable logicVariable) {
		if (ID == logicVariable.getID()) {
			return new LogicVariable(-1, booleanValue);
		}
		return deepCopy();
	}

	public boolean hasValue() {
		return value != -1;
	}

	public boolean getValue() {
		return value == 1;
	}

	public boolean isFalse() {
		return hasValue() && !getValue();
	}

	public boolean isTrue() {
		return hasValue() && getValue();
	}

	public void setValue(boolean value) {
		this.value = (byte) (value ? 1 : 0);
	}

	public void reverseValue() {
		if (value != -1) {
			this.value = (byte) ((value == 1) ? 0 : 1);
		}
	}

	public int getID() {
		return ID;
	}

	@Override
	public boolean eval(HashSet<State> F, HashSet<State> L) {
		if (ID == -1) {
			if (value == -1) {
				System.out.println("What is this Logic Variable");
			}
			return value == 1;
		}
		return F.contains(new State(ID)) || L.contains(new State(ID));
	}

	@Override
	public String toString() {
		if (ID == -1) {
			if (value == -1) {
				System.out.println("What is this Logic Variable");
			}
			if (value == 1) {
				return "true";
			} else {
				return "false";
			}
		}
		return String.valueOf(ID);
	}

}