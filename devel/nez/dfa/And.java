package nez.dfa;

import java.util.HashSet;

public class And extends BooleanExpression {
	public BooleanExpression left, right;

	public And() {

	}

	public And(BooleanExpression left, BooleanExpression right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public int traverse() {
		// System.out.println("And");
		int F = left.traverse();
		int G = right.traverse();
		return BDD.apply('&', F, G);
	}

	@Override
	public And deepCopy() {
		return new And(left.deepCopy(), right.deepCopy());
	}

	@Override
	public And recoverPredicate() {
		return new And(left.recoverPredicate(), right.recoverPredicate());
	}

	@Override
	public BooleanExpression assignBooleanValueToLogicVariable(boolean booleanValue, LogicVariable logicVariable) {
		BooleanExpression tmp_left = left.assignBooleanValueToLogicVariable(booleanValue, logicVariable);
		BooleanExpression tmp_right = right.assignBooleanValueToLogicVariable(booleanValue, logicVariable);
		if ((tmp_left instanceof LogicVariable) && (tmp_right instanceof LogicVariable)) {
			LogicVariable logic_left = (LogicVariable) tmp_left;
			LogicVariable logic_right = (LogicVariable) tmp_right;

			boolean leftHasValue = logic_left.hasValue();
			boolean rightHasValue = logic_right.hasValue();
			boolean left_value = logic_left.getValue();
			boolean right_value = logic_right.getValue();

			if (leftHasValue && rightHasValue) {
				return new LogicVariable(-1, left_value && right_value);
			} else if (leftHasValue) {
				return left_value ? tmp_right : new LogicVariable(-1, false);
			} else if (rightHasValue) {
				return right_value ? tmp_left : new LogicVariable(-1, false);
			} else {
				return new And(tmp_left, tmp_right);
			}
		} else if (tmp_left instanceof LogicVariable) {
			LogicVariable logic_left = (LogicVariable) tmp_left;

			if (logic_left.hasValue()) {
				return logic_left.getValue() ? tmp_right : new LogicVariable(-1, false);
			} else {
				return new And(tmp_left, tmp_right);
			}
		} else if (tmp_right instanceof LogicVariable) {
			LogicVariable logic_right = (LogicVariable) tmp_right;
			if (logic_right.hasValue()) {
				return logic_right.getValue() ? tmp_left : new LogicVariable(-1, false);
			} else {
				return new And(tmp_left, tmp_right);
			}
		} else {
			return new And(tmp_left, tmp_right);
		}
	}

	@Override
	public boolean eval(HashSet<State> F, HashSet<State> L) {
		return left.eval(F, L) && right.eval(F, L);
	}

	@Override
	public String toString() {
		return "(" + left + "&" + right + ")";
	}

}