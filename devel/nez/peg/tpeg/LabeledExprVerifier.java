package nez.peg.tpeg;

import java.util.ArrayList;

/**
 * Created by skgchxngsxyz-opensuse on 15/09/03.
 */
public class LabeledExprVerifier extends BaseVisitor<Void, Void> {
	private final ArrayList<TypedPEG> exprStack = new ArrayList<>();

	@Override
	public Void visit(TypedPEG expr, Void param) {
		exprStack.add(expr);
		expr.accept(this, param);
		exprStack.remove(exprStack.size() - 1);
		return null;
	}

	@Override
	public Void visitDefault(TypedPEG expr, Void param) {
		return null;
	}

	@Override
	public Void visitRepeatExpr(TypedPEG.RepeatExpr expr, Void param) {
		return visit(expr.getExpr());
	}

	@Override
	public Void visitOptionalExpr(TypedPEG.OptionalExpr expr, Void param) {
		return visit(expr.getExpr());
	}

	@Override
	public Void visitPredicateExpr(TypedPEG.PredicateExpr expr, Void param) {
		return visit(expr.getExpr());
	}

	@Override
	public Void visitChoiceExpr(TypedPEG.ChoiceExpr expr, Void param) {
		for (TypedPEG e : expr.getExprs()) {
			visit(e);
		}
		return null;
	}

	@Override
	public Void visitSequenceExpr(TypedPEG.SequenceExpr expr, Void param) {
		for (TypedPEG e : expr.getExprs()) {
			visit(e);
		}
		return null;
	}

	@Override
	public Void visitRuleExpr(TypedPEG.RuleExpr expr, Void param) {
		return visit(expr.getExpr());
	}

	@Override
	public Void visitTypedRuleExpr(TypedPEG.TypedRuleExpr expr, Void param) {
		return visit(expr.getExpr());
	}

	@Override
	public Void visitLabeledExpr(TypedPEG.LabeledExpr expr, Void param) {
		if (exprStack.size() == 3 && exprStack.get(0) instanceof TypedPEG.RuleExpr && exprStack.get(1) instanceof TypedPEG.SequenceExpr) {
			return visit(expr.getExpr());
		}
		if (exprStack.size() == 2 && exprStack.get(0) instanceof TypedPEG.RuleExpr) {
			return visit(expr.getExpr());
		}
		exprStack.clear();
		throw new SemanticException(expr.getRange(), "not allowed label");
	}
}
