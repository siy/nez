package nez.lang;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import nez.ast.Source;
import nez.ast.SourceLocation;
import nez.ast.Symbol;
import nez.lang.Nez.Byte;
import nez.lang.Nez.Detree;
import nez.lang.Nez.Dispatch;
import nez.lang.Nez.EndTree;
import nez.lang.Nez.FoldTree;
import nez.lang.Nez.Function;
import nez.lang.Nez.Label;
import nez.lang.Nez.Repeat;
import nez.lang.Nez.Replace;
import nez.lang.Nez.Scan;
import nez.util.StringUtils;
import nez.util.UList;

public abstract class Expression extends AbstractList<Expression> implements SourceLocation {

	public abstract Object visit(Expression.Visitor v, Object a);

	private SourceLocation s;

	public final void setSourceLocation(SourceLocation s) {
		if (s instanceof Expression) {
			s = ((Expression) s).getSourceLocation();
		}
		this.s = s;
	}

	public final SourceLocation getSourceLocation() {
		return s;
	}

	@Override
	public Source getSource() {
		if (s != null) {
			return s.getSource();
		}
		return null;
	}

	@Override
	public long getSourcePosition() {
		if (s != null) {
			s.getSourcePosition();
		}
		return 0L;
	}

	@Override
	public int getLineNum() {
		if (s != null) {
			s.getLineNum();
		}
		return 0;
	}

	@Override
	public int getColumn() {
		if (s != null) {
			s.getColumn();
		}
		return 0;
	}

	@Override
	public String formatSourceMessage(String type, String msg) {
		if (s != null) {
			return s.formatSourceMessage(type, msg);
		}
		return "(" + type + ") " + msg;
	}

	// test

	public static boolean isByteConsumed(Expression e) {
		return (e instanceof Nez.Byte || e instanceof Nez.ByteSet || e instanceof Nez.Any);
	}

	public static boolean isPositionIndependentOperation(Expression e) {
		return (e instanceof Nez.Tag || e instanceof Nez.Replace);
	}

	// convinient interface

	public final Expression newEmpty() {
		return Expressions.newEmpty(getSourceLocation());
	}

	public final Expression newFailure() {
		return Expressions.newFail(getSourceLocation());
	}

	public final Expression newByteSet(boolean isBinary, boolean[] byteMap) {
		return Expressions.newByteSet(getSourceLocation(), byteMap);
	}

	public final Expression newPair(Expression e, Expression e2) {
		return Expressions.newPair(getSourceLocation(), e, e2);
	}

	public final Expression newPair(List<Expression> l) {
		return Expressions.newPair(l);
	}

	public final Expression newChoice(Expression e, Expression e2) {
		return Expressions.newChoice(e, e2);
	}

	public final Expression newChoice(UList<Expression> l) {
		return Expressions.newChoice(l);
	}

	/* static class */

	@Override
	public final String toString() {
		StringBuilder sb = new StringBuilder();
		defaultFormatter.format(sb, this);
		return sb.toString();
	}

	public abstract static class Visitor {

		protected HashMap<String, Object> visited;

		public Object lookup(String uname) {
			if (visited != null) {
				return visited.get(uname);
			}
			return null;
		}

		public final void memo(String uname, Object o) {
			if (visited == null) {
				visited = new HashMap<>();
			}
			visited.put(uname, o);
		}

		public final boolean isVisited(String uname) {
			if (visited != null) {
				return visited.containsKey(uname);
			}
			return false;
		}

		public final void visited(String uname) {
			memo(uname, uname);
		}

		public final void clear() {
			if (visited != null) {
				visited.clear();
			}
		}

		public abstract Object visitNonTerminal(NonTerminal e, Object a);

		public abstract Object visitEmpty(Nez.Empty e, Object a);

		public abstract Object visitFail(Nez.Fail e, Object a);

		public abstract Object visitByte(Nez.Byte e, Object a);

		public abstract Object visitByteSet(Nez.ByteSet e, Object a);

		public abstract Object visitAny(Nez.Any e, Object a);

		public abstract Object visitMultiByte(Nez.MultiByte e, Object a);

		public abstract Object visitPair(Nez.Pair e, Object a);

		public abstract Object visitSequence(Nez.Sequence e, Object a);

		public abstract Object visitChoice(Nez.Choice e, Object a);

		public abstract Object visitDispatch(Nez.Dispatch e, Object a);

		public abstract Object visitOption(Nez.Option e, Object a);

		public abstract Object visitZeroMore(Nez.ZeroMore e, Object a);

		public abstract Object visitOneMore(Nez.OneMore e, Object a);

		public abstract Object visitAnd(Nez.And e, Object a);

		public abstract Object visitNot(Nez.Not e, Object a);

		public abstract Object visitBeginTree(Nez.BeginTree e, Object a);

		public abstract Object visitFoldTree(Nez.FoldTree e, Object a);

		public abstract Object visitLinkTree(Nez.LinkTree e, Object a);

		public abstract Object visitTag(Nez.Tag e, Object a);

		public abstract Object visitReplace(Nez.Replace e, Object a);

		public abstract Object visitEndTree(Nez.EndTree e, Object a);

		public abstract Object visitDetree(Nez.Detree e, Object a);

		public abstract Object visitBlockScope(Nez.BlockScope e, Object a);

		public abstract Object visitLocalScope(Nez.LocalScope e, Object a);

		public abstract Object visitSymbolAction(Nez.SymbolAction e, Object a);

		public abstract Object visitSymbolPredicate(Nez.SymbolPredicate e, Object a);

		public abstract Object visitSymbolMatch(Nez.SymbolMatch e, Object a);

		public abstract Object visitSymbolExists(Nez.SymbolExists e, Object a);

		public abstract Object visitIf(Nez.IfCondition e, Object a);

		public abstract Object visitOn(Nez.OnCondition e, Object a);

		public abstract Object visitScan(Nez.Scan scanf, Object a);

		public abstract Object visitRepeat(Nez.Repeat e, Object a);

		public abstract Object visitLabel(Nez.Label e, Object a);

		public Object visitExtended(Expression e, Object a) {
			return a;
		}

	}

	private static final ExpressionFormatter defaultFormatter = new ExpressionFormatter();

	public static void format(Expression e, StringBuilder sb) {
		defaultFormatter.format(sb, e);
	}

	public static String Stringfy(Expression e) {
		StringBuilder sb = new StringBuilder();
		defaultFormatter.format(sb, e);
		return sb.toString();
	}

	public static class ExpressionFormatter extends Expression.Visitor {

		public final void format(StringBuilder sb, Expression e) {
			e.visit(this, sb);
		}

		protected void format(StringBuilder sb, String pre, Expression e, String post) {
			sb.append(pre);
			e.visit(this, sb);
			sb.append(post);
		}

		@Override
		public Object visitNonTerminal(NonTerminal e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append(e.getLocalName());
			return null;
		}

		@Override
		public Object visitEmpty(Nez.Empty e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("''");
			return null;
		}

		@Override
		public Object visitFail(Nez.Fail e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("!''");
			return null;
		}

		@Override
		public Object visitByte(Nez.Byte e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			StringUtils.formatByte(sb, e.byteChar);
			return null;
		}

		@Override
		public Object visitByteSet(Nez.ByteSet e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append(StringUtils.stringfyByteSet(e.byteset));
			return null;
		}

		@Override
		public Object visitAny(Nez.Any e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append(".");
			return null;
		}

		@Override
		public Object visitMultiByte(Nez.MultiByte e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			StringUtils.formatUTF8(sb, e.byteseq);
			return null;
		}

		@Override
		public Object visitPair(Nez.Pair e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatSequence(sb, e, " ");
			return null;
		}

		@Override
		public Object visitSequence(Nez.Sequence e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatSequence(sb, e, " ");
			return null;
		}

		@Override
		public Object visitChoice(Nez.Choice e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatSequence(sb, e, " / ");
			return null;
		}

		private void formatSequence(StringBuilder sb, Expression e, String delim) {
			for (int i = 0; i < e.size(); i++) {
				if (i > 0) {
					sb.append(delim);
				}
				Expression inner = e.get(i);
				if (inner instanceof Nez.Choice) {
					format(sb, "(", inner, ")");
				} else {
					format(sb, inner);
				}
			}
		}

		private void formatUnary(StringBuilder sb, String prefix, Expression inner, String suffix) {
			if (prefix != null) {
				sb.append(prefix);
			}
			String pre = "(";
			String post = ")";
			if (inner instanceof NonTerminal || inner instanceof Nez.SingleCharacter) {
				pre = "";
				post = "";
			}
			format(sb, pre, inner, post);
			if (suffix != null) {
				sb.append(suffix);
			}
		}

		@Override
		public Object visitOption(Nez.Option e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, null, e.get(0), "?");
			return null;
		}

		@Override
		public Object visitZeroMore(Nez.ZeroMore e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, null, e.get(0), "*");
			return null;
		}

		@Override
		public Object visitOneMore(Nez.OneMore e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, null, e.get(0), "+");
			return null;
		}

		@Override
		public Object visitAnd(Nez.And e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, "&", e.get(0), null);
			return null;
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, "!", e.get(0), null);
			return null;
		}

		@Override
		public Object visitBeginTree(Nez.BeginTree e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("{");
			return null;
		}

		@Override
		public Object visitFoldTree(Nez.FoldTree e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("{$");
			if (e.label != null) {
				sb.append(e.label);
			}
			return null;
		}

		@Override
		public Object visitLinkTree(Nez.LinkTree e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, (e.label != null) ? "$" + e.label + "(" : "$(", e.get(0), ")");
			return null;
		}

		@Override
		public Object visitTag(Nez.Tag e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("#").append(e.tag);
			return null;
		}

		@Override
		public Object visitReplace(Nez.Replace e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append(StringUtils.quoteString('`', e.value, '`'));
			return null;
		}

		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			if (e.value != null) {
				sb.append(StringUtils.quoteString('`', e.value, '`'));
				sb.append(" ");
			}
			if (e.tag != null) {
				sb.append("#").append(e.tag);
				sb.append(" ");
			}
			sb.append("}");
			return null;
		}

		@Override
		public Object visitDetree(Nez.Detree e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatUnary(sb, "~", e.get(0), null);
			return null;
		}

		/* function */
		private void formatFunction(Function e, Object argument, StringBuilder sb) {
			sb.append("<");
			sb.append(e.op);
			if (argument != null) {
				sb.append(" ");
				sb.append(argument);
			}
			if (e.hasInnerExpression()) {
				sb.append(" ");
				sb.append(e.get(0));
			}
			sb.append(">");
		}

		@Override
		public Object visitBlockScope(Nez.BlockScope e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, null, sb);
			return null;
		}

		@Override
		public Object visitLocalScope(Nez.LocalScope e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, e.tableName, sb);
			return null;
		}

		@Override
		public Object visitSymbolAction(Nez.SymbolAction e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, null, sb);
			return null;
		}

		@Override
		public Object visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, e.tableName, sb);
			return null;
		}

		@Override
		public Object visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, e.tableName, sb);
			return null;
		}

		@Override
		public Object visitSymbolExists(Nez.SymbolExists e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, symbol(e.tableName, e.symbol), sb); // FIXME
			return null;
		}

		@Override
		public Object visitScan(Scan e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			String mask = null;
			if (e.mask != 0) {
				mask = Long.toBinaryString(e.mask);
			}
			formatFunction(e, mask, sb);
			return null;
		}

		@Override
		public Object visitRepeat(Repeat e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, null, sb);
			return null;
		}

		@Override
		public Object visitIf(Nez.IfCondition e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, condition(e.predicate, e.flagName), sb);
			return null;
		}

		@Override
		public Object visitOn(Nez.OnCondition e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			formatFunction(e, condition(e.predicate, e.flagName), sb);
			return null;
		}

		@Override
		public Object visitLabel(Nez.Label e, Object a) {
			return null;
		}

		private String symbol(Symbol table, String name) {
			return name == null ? table.toString() : table + " " + name;
		}

		private String condition(boolean predicate, String name) {
			return predicate ? name : "!" + name;
		}

		@Override
		public Object visitDispatch(Dispatch e, Object a) {
			StringBuilder sb = (StringBuilder) a;
			sb.append("<dfa ");
			boolean[] b = Bytes.newMap(false);
			for (int i = 1; i < e.inners.length; i++) {
				Arrays.fill(b, false);
				if (i > 2) {
					sb.append("|");
				}
				for (int j = 0; i < e.indexMap.length; j++) {
					if (e.indexMap[j] == i) {
						b[j] = true;
					}
				}
				Expression c = Expressions.newByteSet(b);
				c.visit(this, a);
				sb.append(":");
				e.inners[i].visit(this, a);
			}
			sb.append(">");
			return null;
		}
	}

	public abstract static class DuplicateVisitor extends Visitor {

		protected boolean enforcedSequence;
		protected boolean enforcedPair;

		protected boolean enableImmutableDuplication;

		public Expression visit(Expression e) {
			SourceLocation s = e.getSourceLocation();
			e = (Expression) e.visit(this, null);
			if (s != null) {
				e.setSourceLocation(s);
			}
			return e;
		}

		private Expression inner(Expression e) {
			return visit(e.get(0));
		}

		private Expression sub(Expression e, int i) {
			return visit(e.get(i));
		}

		@Override
		public Expression visitEmpty(Nez.Empty e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Empty();
		}

		@Override
		public Expression visitFail(Nez.Fail e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Fail();
		}

		@Override
		public Expression visitByte(Nez.Byte e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Byte(e.byteChar);
		}

		@Override
		public Expression visitByteSet(Nez.ByteSet e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.ByteSet(e.byteset);
		}

		@Override
		public Expression visitAny(Nez.Any e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Any();
		}

		@Override
		public Expression visitMultiByte(Nez.MultiByte e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.MultiByte(e.byteseq);
		}

		@Override
		public Expression visitPair(Nez.Pair e, Object a) {
			if (enforcedSequence) {
				List<Expression> l = Expressions.flatten(e);
				UList<Expression> l2 = Expressions.newUList(l.size());
				for (Expression expression : l) {
					Expressions.addSequence(l2, visit(expression));
				}
				return new Nez.Sequence(l2.compactArray());
			}
			return new Nez.Pair(sub(e, 0), sub(e, 1));
		}

		@Override
		public Expression visitSequence(Nez.Sequence e, Object a) {
			if (enforcedPair) {
				List<Expression> l = Expressions.flatten(e);
				UList<Expression> l2 = Expressions.newUList(l.size());
				for (Expression expression : l) {
					Expressions.addSequence(l2, visit(expression));
				}
				return Expressions.newPair(l2);
			}
			UList<Expression> l = Expressions.newUList(e.size());
			for (Expression sub : e) {
				Expressions.addSequence(l, visit(sub));
			}
			return new Nez.Sequence(l.compactArray());

		}

		@Override
		public Expression visitChoice(Nez.Choice e, Object a) {
			UList<Expression> l = Expressions.newUList(e.size());
			for (Expression sub : e) {
				Expressions.addChoice(l, visit(sub));
			}
			return new Nez.Choice(l.compactArray());
		}

		@Override
		public Expression visitDispatch(Nez.Dispatch e, Object a) {
			Expression[] inners = new Expression[e.size()];
			inners[0] = e.get(0);
			for (int i = 1; i < e.size(); i++) {
				inners[i] = e.get(i);
			}
			return new Nez.Dispatch(inners, e.indexMap);
		}

		@Override
		public Expression visitOption(Nez.Option e, Object a) {
			return new Nez.Option(inner(e));
		}

		@Override
		public Expression visitZeroMore(Nez.ZeroMore e, Object a) {
			return new Nez.ZeroMore(inner(e));
		}

		@Override
		public Expression visitOneMore(Nez.OneMore e, Object a) {
			return new Nez.OneMore(inner(e));
		}

		@Override
		public Expression visitAnd(Nez.And e, Object a) {
			return new Nez.And(inner(e));
		}

		@Override
		public Expression visitNot(Nez.Not e, Object a) {
			return new Nez.Not(inner(e));
		}

		@Override
		public Expression visitBeginTree(Nez.BeginTree e, Object a) {
			return new Nez.BeginTree(e.shift);
		}

		@Override
		public Expression visitEndTree(EndTree e, Object a) {
			Nez.EndTree n = new Nez.EndTree(e.shift);
			n.tag = e.tag;
			n.value = e.value;
			return n;
		}

		@Override
		public Expression visitFoldTree(Nez.FoldTree e, Object a) {
			return new Nez.FoldTree(e.shift, e.label);
		}

		@Override
		public Expression visitLinkTree(Nez.LinkTree e, Object a) {
			return new Nez.LinkTree(e.label, inner(e));
		}

		@Override
		public Expression visitTag(Nez.Tag e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Tag(e.tag);
		}

		@Override
		public Expression visitReplace(Replace e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Replace(e.value);
		}

		@Override
		public Expression visitDetree(Detree e, Object a) {
			return new Nez.Detree(inner(e));
		}

		@Override
		public Expression visitBlockScope(Nez.BlockScope e, Object a) {
			return new Nez.BlockScope(inner(e));
		}

		@Override
		public Expression visitLocalScope(Nez.LocalScope e, Object a) {
			return new Nez.LocalScope(e.tableName, inner(e));
		}

		@Override
		public Expression visitSymbolAction(Nez.SymbolAction e, Object a) {
			return new Nez.SymbolAction(e.op, (NonTerminal) inner(e));
		}

		@Override
		public Expression visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			return new Nez.SymbolPredicate(e.op, (NonTerminal) inner(e), e.tableName);
		}

		@Override
		public Expression visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			return new Nez.SymbolMatch(e.op, (NonTerminal) e.get(0), e.tableName);
		}

		@Override
		public Expression visitSymbolExists(Nez.SymbolExists e, Object a) {
			return new Nez.SymbolExists(e.tableName, e.symbol);
		}

		@Override
		public Expression visitScan(Nez.Scan e, Object a) {
			return new Nez.Scan(e.mask, e.shift, inner(e));
		}

		@Override
		public Expression visitRepeat(Nez.Repeat e, Object a) {
			return new Nez.Repeat(inner(e));
		}

		@Override
		public Expression visitIf(Nez.IfCondition e, Object a) {
			return new Nez.IfCondition(e.predicate, e.flagName);
		}

		@Override
		public Expression visitOn(Nez.OnCondition e, Object a) {
			return new Nez.OnCondition(e.predicate, e.flagName, inner(e));
		}

		@Override
		public Expression visitLabel(Nez.Label e, Object a) {
			if (!enableImmutableDuplication && e.getSourceLocation() == null) {
				return e;
			}
			return new Nez.Label(e.label, e.start);
		}

	}

	public static class TransformVisitor extends Visitor {

		protected Expression visitInner(Expression e, Object a) {
			return (Expression) e.visit(this, null);
		}

		@Override
		public Expression visitEmpty(Nez.Empty e, Object a) {
			return e;
		}

		@Override
		public Expression visitFail(Nez.Fail e, Object a) {
			return e;
		}

		@Override
		public Expression visitByte(Byte e, Object a) {
			return e;
		}

		@Override
		public Expression visitByteSet(Nez.ByteSet e, Object a) {
			return e;
		}

		@Override
		public Expression visitAny(Nez.Any e, Object a) {
			return e;
		}

		@Override
		public Expression visitMultiByte(Nez.MultiByte e, Object a) {
			return e;
		}

		@Override
		public Expression visitNonTerminal(NonTerminal e, Object a) {
			return e;
		}

		@Override
		public Expression visitPair(Nez.Pair e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			e.set(1, visitInner(e.get(1), a));
			return e;
		}

		@Override
		public Expression visitSequence(Nez.Sequence e, Object a) {
			for (int i = 0; i < e.size(); i++) {
				Expression sub = visitInner(e.get(i), a);
				e.set(i, sub);
			}
			return e;
		}

		@Override
		public Expression visitChoice(Nez.Choice e, Object a) {
			for (int i = 0; i < e.size(); i++) {
				Expression sub = visitInner(e.get(i), a);
				e.set(i, sub);
			}
			return e;
		}

		@Override
		public Expression visitOption(Nez.Option e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitZeroMore(Nez.ZeroMore e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitOneMore(Nez.OneMore e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitAnd(Nez.And e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitNot(Nez.Not e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitDetree(Nez.Detree e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitBeginTree(Nez.BeginTree e, Object a) {
			return e;
		}

		@Override
		public Expression visitEndTree(Nez.EndTree e, Object a) {
			return e;
		}

		@Override
		public Object visitFoldTree(FoldTree e, Object a) {
			return e;
		}

		@Override
		public Expression visitLinkTree(Nez.LinkTree e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitTag(Nez.Tag e, Object a) {
			return e;
		}

		@Override
		public Expression visitReplace(Nez.Replace e, Object a) {
			return e;
		}

		@Override
		public Expression visitBlockScope(Nez.BlockScope e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitLocalScope(Nez.LocalScope e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitSymbolAction(Nez.SymbolAction e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			return e;
		}

		@Override
		public Expression visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitSymbolExists(Nez.SymbolExists e, Object a) {
			return e;
		}

		@Override
		public Expression visitScan(Nez.Scan e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitRepeat(Nez.Repeat e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Expression visitIf(Nez.IfCondition e, Object a) {
			return e;
		}

		@Override
		public Expression visitOn(Nez.OnCondition e, Object a) {
			e.set(0, visitInner(e.get(0), a));
			return e;
		}

		@Override
		public Object visitLabel(Label e, Object a) {
			return e;
		}

		@Override
		public Object visitDispatch(Dispatch e, Object a) {
			for (int i = 1; i < e.inners.length; i++) {
				e.inners[i] = visitInner(e.inners[i], a);
			}
			return e;
		}
	}

	public abstract static class AnalyzeVisitor<T> extends Expression.Visitor implements PropertyAnalyzer<T> {
		protected T defaultResult;
		protected T undecided;

		protected AnalyzeVisitor(T defaultResult, T undecided) {
			this.defaultResult = defaultResult;
			this.undecided = undecided;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T analyze(Production p) {
			String uname = p.getUniqueName();
			Object v = lookup(uname);
			if (v == null) {
				visited(uname);
				v = p.getExpression().visit(this, null);
				if (undecided != v) {
					memo(uname, v);
				}
			}
			return (T) v;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T analyze(Expression e) {
			return (T) e.visit(this, null);
		}

		protected T analyzeInners(Expression e) {
			T result = defaultResult;
			for (Expression sub : e) {
				@SuppressWarnings("unchecked")
				T ts = (T) sub.visit(this, null);
				if (ts == defaultResult) {
					return ts;
				}
				if (ts == undecided) {
					result = ts;
				}
			}
			return result;
		}

		@Override
		public Object visitNonTerminal(NonTerminal e, Object a) {
			Production p = e.getProduction();
			return analyze(p);
		}

		@Override
		public Object visitEmpty(Nez.Empty e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitFail(Nez.Fail e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitByte(Byte e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitByteSet(Nez.ByteSet e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitAny(Nez.Any e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitMultiByte(Nez.MultiByte e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitPair(Nez.Pair e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitSequence(Nez.Sequence e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitChoice(Nez.Choice e, Object a) {
			T result = defaultResult;
			for (Expression sub : e) {
				@SuppressWarnings("unchecked")
				T ts = (T) sub.visit(this, null);
				if (ts == undecided) {
					result = ts;
					continue;
				}
				if (ts != defaultResult) {
					if (result != undecided) {
						result = ts;
					}
				}
			}
			return result;
		}

		@Override
		public Object visitDispatch(Nez.Dispatch e, Object a) {
			T result = defaultResult;
			for (int i = 1; i < e.size(); i++) {
				Expression sub = e.get(i);
				@SuppressWarnings("unchecked")
				T ts = (T) sub.visit(this, null);
				if (ts == undecided) {
					result = ts;
					continue;
				}
				if (ts != defaultResult) {
					if (result != undecided) {
						result = ts;
					}
				}
			}
			return result;
		}

		@Override
		public Object visitOption(Nez.Option e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitZeroMore(Nez.ZeroMore e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitOneMore(Nez.OneMore e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitAnd(Nez.And e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitBeginTree(Nez.BeginTree e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitFoldTree(Nez.FoldTree e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitLinkTree(Nez.LinkTree e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitTag(Nez.Tag e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitReplace(Nez.Replace e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitDetree(Nez.Detree e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitBlockScope(Nez.BlockScope e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitLocalScope(Nez.LocalScope e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitSymbolAction(Nez.SymbolAction e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitSymbolMatch(Nez.SymbolMatch e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitSymbolPredicate(Nez.SymbolPredicate e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitSymbolExists(Nez.SymbolExists e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitScan(Nez.Scan e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitRepeat(Nez.Repeat e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitIf(Nez.IfCondition e, Object a) {
			return defaultResult;
		}

		@Override
		public Object visitOn(Nez.OnCondition e, Object a) {
			return analyzeInners(e);
		}

		@Override
		public Object visitLabel(Nez.Label e, Object a) {
			return defaultResult;
		}
	}

}
