package nez.lang;

import nez.lang.Nez.Byte;
import nez.util.Verbose;

public enum Typestate {
	Unit, Tree, TreeMutation, Immutation, Undecided;

	public interface TypestateAnalyzer {
		Typestate inferTypestate(Production p);

		Typestate inferTypestate(Expression e);

		boolean isUnit(Production p);

		boolean isUnit(Expression e);

		boolean isTree(Production p);

		boolean isTree(Expression e);
	}

	public static TypestateAnalyzer newAnalyzer() {
		return new Analyzer();
	}

	static final class Analyzer extends Expression.Visitor implements TypestateAnalyzer {

		@Override
		public boolean isUnit(Production p) {
			return inferTypestate(p) == Unit;
		}

		@Override
		public boolean isUnit(Expression e) {
			return inferTypestate(e) == Unit;
		}

		@Override
		public boolean isTree(Production p) {
			return inferTypestate(p) == Tree;
		}

		@Override
		public boolean isTree(Expression e) {
			return inferTypestate(e) == Tree;
		}

		@Override
		public Typestate inferTypestate(Expression e) {
			return (Typestate) e.visit(this, null);
		}

		@Override
		public Typestate inferTypestate(Production p) {
			String uname = p.getUniqueName();
			Object v = lookup(uname);
			if (v == null) {
				visited(uname);
				v = inferTypestate(p.getExpression());
				if (Undecided != v) {
					memo(uname, v);
				}
				return (Typestate) v;
			}
			return (v instanceof Typestate) ? (Typestate) v : Undecided;
		}

		@Override
		public Object visitNonTerminal(NonTerminal e, Object a) {
			Production p = e.getProduction();
			if (p == null) {
				if (!e.isTerminal()) {
					Verbose.debug("** unresolved name: " + e.getLocalName());
				}
				return Unit;
			}
			return inferTypestate(p);
		}

		@Override
		public Object visitEmpty(Nez.Empty e, Object a) {
			return Unit;
		}

		@Override
		public Object visitFail(Nez.Fail e, Object a) {
			return Unit;
		}

		@Override
		public Object visitByte(Byte e, Object a) {
			return Unit;
		}

		@Override
		public Object visitByteSet(Nez.ByteSet e, Object a) {
			return Unit;
		}

		@Override
		public Object visitAny(Nez.Any e, Object a) {
			return Unit;
		}

		@Override
		public Object visitMultiByte(Nez.MultiByte e, Object a) {
			return Unit;
		}

		@Override
		public Object visitPair(Nez.Pair e, Object a) {
			for (Expression s : e) {
				Typestate ts = inferTypestate(s);
				if (ts == Tree || ts == TreeMutation) {
					return ts;
				}
			}
			return Unit;
		}

		@Override
		public Object visitSequence(Nez.Sequence e, Object a) {
			for (Expression s : e) {
				Typestate ts = inferTypestate(s);
				if (ts == Tree || ts == TreeMutation) {
					return ts;
				}
			}
			return Unit;
		}

		@Override
		public Object visitChoice(Nez.Choice e, Object a) {
			Object t = Unit;
			for (Expression s : e) {
				t = inferTypestate(s);
				if (t == Tree || t == TreeMutation) {
					return t;
				}
			}
			return t;
		}

		@Override
		public Object visitDispatch(Nez.Dispatch e, Object a) {
			Object t = Unit;
			for (int i = 1; i < e.size(); i++) {
				t = inferTypestate(e.get(i));
				if (t == Tree || t == TreeMutation) {
					return t;
				}
			}
			return t;
		}

		@Override
		public Object visitOption(Nez.Option e, Object a) {
			Typestate ts = inferTypestate(e.get(0));
			if (ts == Tree) {
				return TreeMutation;
			}
			return ts;
		}

		@Override
		public Object visitZeroMore(Nez.ZeroMore e, Object a) {
			Typestate ts = inferTypestate(e.get(0));
			if (ts == Tree) {
				return TreeMutation;
			}
			return ts;
		}

		@Override
		public Object visitOneMore(Nez.OneMore e, Object a) {
			Typestate ts = inferTypestate(e.get(0));
			if (ts == Tree) {
				return TreeMutation;
			}
			return ts;
		}

		@Override
		public Object visitAnd(Nez.And e, Object a) {
			Typestate ts = inferTypestate(e.get(0));
			if (ts == Tree) { // typeCheck needs to report error
				return Unit;
			}
			return ts;
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			return Unit;
		}

		@Override
		public Object visitBeginTree(Nez.BeginTree e, Object a) {
			return Tree;
		}

		@Override
		public Object visitFoldTree(Nez.FoldTree e, Object a) {
			return TreeMutation;
		}

		@Override
		public Object visitLinkTree(Nez.LinkTree e, Object a) {
			return TreeMutation;
		}

		@Override
		public Object visitTag(Nez.Tag e, Object a) {
			return TreeMutation;
		}

		@Override
		public Object visitReplace(Nez.Replace e, Object a) {
			return TreeMutation;
		}

		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			return TreeMutation;
		}

		@Override
		public Object visitDetree(Nez.Detree e, Object a) {
			return Unit;
		}

		@Override
		public final Object visitBlockScope(Nez.BlockScope e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public final Object visitLocalScope(Nez.LocalScope e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public final Object visitSymbolAction(Nez.SymbolAction e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public final Object visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			return Unit;
		}

		@Override
		public final Object visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public final Object visitSymbolExists(Nez.SymbolExists e, Object a) {
			return Unit;
		}

		@Override
		public final Object visitScan(Nez.Scan e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public final Object visitRepeat(Nez.Repeat e, Object a) {
			Typestate ts = inferTypestate(e.get(0));
			if (ts == Tree) {
				return TreeMutation;
			}
			return ts;
		}

		@Override
		public final Object visitIf(Nez.IfCondition e, Object a) {
			return Unit;
		}

		@Override
		public final Object visitOn(Nez.OnCondition e, Object a) {
			return inferTypestate(e.get(0));
		}

		@Override
		public Object visitLabel(Nez.Label e, Object a) {
			return Unit;
		}

	}
}
