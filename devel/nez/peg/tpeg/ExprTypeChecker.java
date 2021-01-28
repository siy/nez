package nez.peg.tpeg;

import static nez.peg.tpeg.SemanticException.semanticError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nez.peg.tpeg.TypedPEG.AnyExpr;
import nez.peg.tpeg.TypedPEG.CharClassExpr;
import nez.peg.tpeg.TypedPEG.ChoiceExpr;
import nez.peg.tpeg.TypedPEG.LabeledExpr;
import nez.peg.tpeg.TypedPEG.NonTerminalExpr;
import nez.peg.tpeg.TypedPEG.OptionalExpr;
import nez.peg.tpeg.TypedPEG.PredicateExpr;
import nez.peg.tpeg.TypedPEG.RepeatExpr;
import nez.peg.tpeg.TypedPEG.RootExpr;
import nez.peg.tpeg.TypedPEG.RuleExpr;
import nez.peg.tpeg.TypedPEG.SequenceExpr;
import nez.peg.tpeg.TypedPEG.StringExpr;
import nez.peg.tpeg.TypedPEG.TypedRuleExpr;
import nez.peg.tpeg.type.LType;
import nez.peg.tpeg.type.TypeEnv;
import nez.peg.tpeg.type.TypeException;

/**
 * Created by skgchxngsxyz-osx on 15/08/28.
 */
public class ExprTypeChecker extends ExpressionVisitor<LType, Void> {
	private final TypeEnv env;
	private final Map<String, RuleExpr> ruleMap = new HashMap<>();
	private final LabeledExprVerifier labeledExprVerifier = new LabeledExprVerifier();
	private final LabeledExprDetector labeledExprDetector = new LabeledExprDetector();
	private final Set<TypedPEG> visitedExprSet = new HashSet<>();

	public ExprTypeChecker(TypeEnv env) {
		this.env = Objects.requireNonNull(env);
	}

	public void checkType(List<RuleExpr> rules) { // entry point
		// register rule
		for (RuleExpr ruleExpr : rules) {
			// if (Objects.nonNull(this.ruleMap.put(ruleExpr.getRuleName(),
			// ruleExpr))) {
			if (ruleMap.put(ruleExpr.getRuleName(), ruleExpr) != null) {
				semanticError(ruleExpr.getRange(), "duplicated rule: " + ruleExpr.getRuleName());
			}
		}

		// verify and check type
		for (RuleExpr ruleExpr : rules) {
			visitedExprSet.clear();
			labeledExprVerifier.visit(ruleExpr);
			checkType(ruleExpr);
		}
	}

	public LType checkType(TypedPEG expr) {
		return checkType(null, expr);
	}

	public LType checkType(LType requiredType, TypedPEG expr) {
		LType type = expr.getType() != null ? expr.getType() : visit(expr);
		if (type == null) {
			semanticError(expr.getRange(), "broken visit" + expr.getClass().getSimpleName());
		}

		if (requiredType == null || requiredType.isSameOrBaseOf(type)) {
			return type;
		}

		semanticError(expr.getRange(), "require: " + requiredType + ", but is: " + type);
		return null;
	}

	@Override
	public LType visit(TypedPEG expr, Void param) {
		if (!visitedExprSet.add(Objects.requireNonNull(expr))) {
			semanticError(expr.getRange(), "detect circular reference");
		}
		return expr.accept(this, param);
	}

	@Override
	public LType visitAnyExpr(AnyExpr expr, Void param) {
		return expr.setType(env.getVoidType());
	}

	@Override
	public LType visitStringExpr(StringExpr expr, Void param) {
		return expr.setType(env.getVoidType());
	}

	@Override
	public LType visitCharClassExpr(CharClassExpr expr, Void param) {
		return expr.setType(env.getVoidType());
	}

	@Override
	public LType visitRepeatExpr(RepeatExpr expr, Void param) {
		LType exprType = checkType(expr.getExpr());
		try {
			return expr.setType(exprType.isVoid() ? env.getVoidType() : env.getArrayType(exprType));
		} catch (TypeException e) {
			throw new SemanticException(expr.getRange(), e);
		}
	}

	@Override
	public LType visitOptionalExpr(OptionalExpr expr, Void param) {
		LType exprType = checkType(expr.getExpr());
		try {
			return expr.setType(exprType.isVoid() ? env.getVoidType() : env.getOptionalType(exprType));
		} catch (TypeException e) {
			throw new SemanticException(expr.getRange(), e);
		}
	}

	@Override
	public LType visitPredicateExpr(PredicateExpr expr, Void param) {
		checkType(env.getVoidType(), expr.getExpr());
		return expr.setType(env.getVoidType());
	}

	@Override
	public LType visitSequenceExpr(SequenceExpr expr, Void param) {
		List<LType> types = new ArrayList<>();
		for (TypedPEG e : expr.getExprs()) {
			LType type = checkType(e);
			if (!type.isVoid()) {
				types.add(type);
			}
		}

		if (types.isEmpty()) {
			return expr.setType(env.getVoidType());
		} else if (types.size() == 1) {
			return expr.setType(types.get(0));
		} else {
			try {
				return expr.setType(env.getTupleType(types.toArray(new LType[0])));
			} catch (TypeException e) {
				throw new SemanticException(expr.getRange(), e);
			}
		}
	}

	@Override
	public LType visitChoiceExpr(ChoiceExpr expr, Void param) {
		List<LType> types = new ArrayList<>();
		for (TypedPEG e : expr.getExprs()) {
			LType type = checkType(e);
			if (!type.isVoid()) {
				types.add(type);
			}
		}
		if (!types.isEmpty() && types.size() < expr.getExprs().size()) {
			semanticError(expr.getRange(), "not allow void type");
		}

		if (types.isEmpty()) {
			return expr.setType(env.getVoidType());
		}

		boolean sameAll = true;
		int size = types.size();
		for (int i = 1; i < size; i++) {
			if (!types.get(0).equals(types.get(i))) {
				sameAll = false;
				break;
			}
		}
		if (sameAll) {
			return expr.setType(types.get(0));
		} else {
			try {
				return expr.setType(env.getUnionType(types.toArray(new LType[0])));
			} catch (TypeException e) {
				throw new SemanticException(expr.getRange(), e);
			}
		}
	}

	@Override
	public LType visitNonTerminalExpr(NonTerminalExpr expr, Void param) {
		TypedPEG targetExpr = ruleMap.get(expr.getName());
		if (targetExpr == null) {
			semanticError(expr.getRange(), "undefined rule: " + expr.getName());
		}
		return expr.setType(checkType(targetExpr));
	}

	@Override
	public LType visitLabeledExpr(LabeledExpr expr, Void param) {
		checkType(env.getAnyType(), expr.getExpr());
		return expr.setType(env.getVoidType()); // actual type is
														// expr.getExprType()
	}

	@Override
	public LType visitRuleExpr(RuleExpr expr, Void param) {
		if (labeledExprDetector.visit(expr.getExpr())) {
			semanticError(expr.getRange(), "not need label");
		}
		return expr.setType(checkType(expr.getExpr()));
	}

	@Override
	public LType visitTypedRuleExpr(TypedRuleExpr expr, Void param) {
		boolean primary = env.isPrimaryType(expr.getTypeName());
		boolean hasLabel = labeledExprDetector.visit(expr.getExpr());

		try {
			if (primary && !hasLabel) { // treat as primary type
				LType type = env.getBasicType(expr.getTypeName());
				expr.setType(type);

				checkType(env.getVoidType(), expr.getExpr());
				return type;
			} else if (!primary && hasLabel) { // treat as structure type
				LType.StructureType type = env.newStructureType(expr.getTypeName());
				expr.setType(type);

				checkType(env.getVoidType(), expr.getExpr());

				// define field
				TypedPEG rightHandSideExpr = expr.getExpr();
				if (rightHandSideExpr instanceof SequenceExpr) {
					for (TypedPEG e : ((SequenceExpr) rightHandSideExpr).getExprs()) {
						if (e instanceof LabeledExpr) {
							LabeledExpr l = (LabeledExpr) e;
							env.defineField(type, l.getLabelName(), l.getExprType());
						}
					}
				} else if (rightHandSideExpr instanceof LabeledExpr) {
					LabeledExpr l = (LabeledExpr) rightHandSideExpr;
					env.defineField(type, l.getLabelName(), l.getExprType());
				} else {
					semanticError(expr.getRange(), "broken right hand side expression");
				}
				return type;
			} else {
				semanticError(expr.getRange(), "illegal type annotation");
				return null;
			}
		} catch (TypeException e) {
			throw new SemanticException(expr.getRange(), e);
		}
	}

	@Override
	public LType visitRootExpr(RootExpr expr, Void param) {
		checkType(expr.getExprs());
		return expr.setType(env.getVoidType());
	}
}
