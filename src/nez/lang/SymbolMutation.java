package nez.lang;

public enum SymbolMutation {
	Immutated, Mutated, Undecided;

	public interface SymbolMutationAnalyzer extends PropertyAnalyzer<SymbolMutation> {
		boolean isMutated(Expression e);
	}

	public static SymbolMutationAnalyzer newAnalyzer() {
		return new SymbolMutationVisitor();
	}

	static final class SymbolMutationVisitor extends Expression.AnalyzeVisitor<SymbolMutation> implements SymbolMutationAnalyzer {

		protected SymbolMutationVisitor() {
			super(Immutated, Undecided);
		}

		@Override
		public boolean isMutated(Expression e) {
			SymbolMutation s = (SymbolMutation) e.visit(this, null);
			return (s != Immutated);
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitBlockScope(Nez.BlockScope e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitLocalScope(Nez.LocalScope e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitSymbolAction(Nez.SymbolAction e, Object a) {
			return Mutated;
		}

		@Override
		public final Object visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public final Object visitSymbolExists(Nez.SymbolExists e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitIf(Nez.IfCondition e, Object a) {
			return Immutated;
		}

		@Override
		public final Object visitOn(Nez.OnCondition e, Object a) {
			return Immutated; // removed
		}

	}
}
