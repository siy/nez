package nez.lang;

import java.util.Objects;

import nez.ast.Symbol;

public class Nez {

	/**
	 * SingleCharacter represents the single character property.
	 * 
	 * @author kiki
	 *
	 */

	public interface SingleCharacter {
	}

	public interface TreeConstruction {
	}

	abstract static class Terminal extends Expression {
		@Override
		public final int size() {
			return 0;
		}

		@Override
		public final Expression get(int index) {
			return null;
		}
	}

	public abstract static class Unary extends Expression {
		public Expression inner;

		protected Unary(Expression e) {
			this.inner = e;
		}

		@Override
		public final int size() {
			return 1;
		}

		@Override
		public final Expression get(int index) {
			return inner;
		}

		@Override
		public final Expression set(int index, Expression e) {
			Expression old = inner;
			this.inner = e;
			return old;
		}
	}

	/**
	 * The Nez.Empty represents an empty expression, denoted '' in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class Empty extends Terminal {

		Empty() {
		}

		@Override
		public final boolean equals(Object o) {
			return (o instanceof Nez.Empty);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitEmpty(this, a);
		}
	}

	/**
	 * The Nez.Fail represents a failure expression, denoted !'' in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class Fail extends Terminal {
		Fail() {
		}

		@Override
		public final boolean equals(Object o) {
			return (o instanceof Nez.Fail);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitFail(this, a);
		}
	}

	/**
	 * Nez.Byte represents a single-byte string literal, denoted as 'a' in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class Byte extends Terminal implements SingleCharacter {
		/**
		 * byteChar
		 */
		public final int byteChar;

		Byte(int byteChar) {
			this.byteChar = byteChar;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Byte) {
				return byteChar == ((Nez.Byte) o).byteChar;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitByte(this, a);
		}
	}

	/**
	 * Nez.Any represents an any character, denoted as . in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class Any extends Terminal implements SingleCharacter {

		Any() {
		}

		@Override
		public final boolean equals(Object o) {
			return (o instanceof Any);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitAny(this, a);
		}
	}

	/**
	 * Nez.ByteSet is a bitmap-based representation of the character class [X-y]
	 * 
	 * @author kiki
	 *
	 */

	public static class ByteSet extends Terminal implements SingleCharacter {
		/**
		 * a 256-length bitmap array, represeting a character acceptance
		 */
		public final boolean[] byteset; // Immutable

		ByteSet(boolean[] b) {
			this.byteset = b;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof ByteSet) {
				ByteSet e = (ByteSet) o;
				for (int i = 0; i < byteset.length; i++) {
					if (byteset[i] != e.byteset[i]) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitByteSet(this, a);
		}
	}

	/**
	 * Nez.MultiByte represents a byte-encoded string expression, such as 'abc'
	 * in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class MultiByte extends Terminal implements SingleCharacter {
		public final byte[] byteseq;

		MultiByte(byte[] byteSeq) {
			this.byteseq = byteSeq;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.MultiByte) {
				Nez.MultiByte mb = (Nez.MultiByte) o;
				if (mb.byteseq.length == byteseq.length) {
					for (int i = 0; i < byteseq.length; i++) {
						if (byteseq[i] != mb.byteseq[i]) {
							return false;
						}
					}
					return true;
				}
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitMultiByte(this, a);
		}
	}

	/* Unary */

	/**
	 * Nez.Option represents an optional expression e?
	 * 
	 * @author kiki
	 *
	 */

	public static class Option extends Nez.Unary {
		Option(Expression e) {
			super(e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Option) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitOption(this, a);
		}

	}

	/**
	 * Nez.Repetition is used to identify a common property of ZeroMore and
	 * OneMore expressions.
	 * 
	 * @author kiki
	 *
	 */

	public interface Repetition {
		Expression get(int index);
	}

	/**
	 * Nez.ZeroMore represents a zero or more repetition e*.
	 * 
	 * @author kiki
	 *
	 */

	public static class ZeroMore extends Unary implements Repetition {
		ZeroMore(Expression e) {
			super(e);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Nez.ZeroMore) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitZeroMore(this, a);
		}

	}

	/**
	 * Nez.OneMore represents a one or more repetition e+.
	 * 
	 * @author kiki
	 *
	 */

	public static class OneMore extends Unary implements Repetition {
		OneMore(Expression e) {
			super(e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.OneMore) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitOneMore(this, a);
		}
	}

	/**
	 * Nez.And represents an and-predicate &e.
	 * 
	 * @author kiki
	 *
	 */

	public static class And extends Unary {
		And(Expression e) {
			super(e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.And) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitAnd(this, a);
		}
	}

	/**
	 * Nez.Not represents a not-predicate !e.
	 * 
	 * @author kiki
	 *
	 */

	public static class Not extends Unary {
		Not(Expression e) {
			super(e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Not) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitNot(this, a);
		}
	}

	/**
	 * Nez.Pair is a pair representation of Nez.Sequence.
	 * 
	 * @author kiki
	 *
	 */

	public static class Pair extends Expression {
		public Expression first;
		public Expression next;

		Pair(Expression first, Expression next) {
			this.first = first;
			this.next = next;
		}

		@Override
		public final int size() {
			return 2;
		}

		@Override
		public final Expression get(int index) {
			assert (index < 2);
			return index == 0 ? first : next;
		}

		@Override
		public final Expression set(int index, Expression e) {
			assert (index < 2);
			Expression p;
			if (index == 0) {
				p = first;
				this.first = e;
			} else {
				p = next;
				this.next = e;
			}
			return p;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Pair) {
				return get(0).equals(((Expression) o).get(0)) && get(1).equals(((Expression) o).get(1));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitPair(this, a);
		}

	}

	abstract static class List extends Expression {
		public Expression[] inners;

		protected List(Expression[] inners) {
			this.inners = inners;
		}

		@Override
		public final int size() {
			return inners.length;
		}

		@Override
		public final Expression get(int index) {
			return inners[index];
		}

		@Override
		public Expression set(int index, Expression e) {
			Expression oldExpresion = inners[index];
			inners[index] = e;
			return oldExpresion;
		}

		protected final boolean equalsList(Nez.List l) {
			if (size() == l.size()) {
				for (int i = 0; i < size(); i++) {
					if (!get(i).equals(l.get(i))) {
						return false;
					}
				}
				return true;
			}
			return false;
		}
	}

	/**
	 * Nez.Sequence is a standard representation of the sequence e e e .
	 * 
	 * @author kiki
	 *
	 */

	public static class Sequence extends Nez.List {

		Sequence(Expression[] inners) {
			super(inners);
			setSourceLocation(inners[0].getSourceLocation());
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Sequence) {
				return equalsList((Nez.List) o);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitSequence(this, a);
		}
	}

	/**
	 * Nez.Choice represents an ordered choice e / ... / e_n in Nez.
	 * 
	 * @author kiki
	 *
	 */

	public static class Choice extends List {
		public boolean visited;
		public ChoicePrediction predicted;

		Choice(Expression[] inners) {
			super(inners);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Choice) {
				return equalsList((Nez.List) o);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitChoice(this, a);
		}
	}

	public static class ChoicePrediction {
		public byte[] indexMap;
		public boolean isTrieTree;
		public boolean[] striped;
		public float reduced;
	}

	public static class Dispatch extends List {
		public final byte[] indexMap;

		Dispatch(Expression[] inners, byte[] indexMap) {
			super(inners);
			this.indexMap = indexMap;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Dispatch) {
				Nez.Dispatch d = (Nez.Dispatch) o;
				if (d.indexMap.length != indexMap.length) {
					return false;
				}
				for (int i = 0; i < indexMap.length; i++) {
					if (indexMap[i] != d.indexMap[i]) {
						return false;
					}
				}
				return equalsList((Nez.List) o);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitDispatch(this, a);
		}

	}

	/* AST */

	public static class BeginTree extends Terminal implements TreeConstruction {
		public int shift;

		public BeginTree(int shift) {
			this.shift = shift;
		}

		@Override
		public final boolean equals(Object o) {
			return (o instanceof Nez.BeginTree && shift == ((Nez.BeginTree) o).shift);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitBeginTree(this, a);
		}
	}

	public static class EndTree extends Terminal implements TreeConstruction {
		public int shift; // optimization parameter
		public Symbol tag; // optimization parameter
		public String value; // optimization parameter

		public EndTree(int shift) {
			this.shift = shift;
			this.tag = null;
			this.value = null;
		}

		@Override
		public final boolean equals(Object o) {
			return o instanceof Nez.EndTree //
					&& shift == ((Nez.EndTree) o).shift //
					&& tag == ((Nez.EndTree) o).tag //
					&& Objects.equals(value, ((Nez.EndTree) o).value);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitEndTree(this, a);
		}
	}

	public static class FoldTree extends Terminal implements TreeConstruction {
		public int shift;
		public final Symbol label;

		public FoldTree(int shift, Symbol label) {
			this.label = label;
			this.shift = shift;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.FoldTree) {
				Nez.FoldTree s = (Nez.FoldTree) o;
				return (label == s.label && shift == s.shift);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitFoldTree(this, a);
		}
	}

	public static class Tag extends Terminal implements TreeConstruction {
		public final Symbol tag;

		public Tag(Symbol tag) {
			this.tag = tag;
		}

		public final Symbol symbol() {
			return tag;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Tag) {
				return tag == ((Nez.Tag) o).tag;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitTag(this, a);
		}
	}

	public static class Replace extends Terminal implements TreeConstruction {
		public String value;

		public Replace(String value) {
			this.value = value;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Replace) {
				return value.equals(((Nez.Replace) o).value);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitReplace(this, a);
		}
	}

	public abstract static class Action extends Terminal implements TreeConstruction {
		Object value;
	}

	public static class LinkTree extends Unary implements TreeConstruction {
		public Symbol label;

		public LinkTree(Symbol label, Expression e) {
			super(e);
			this.label = label;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.LinkTree && label == ((Nez.LinkTree) o).label) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitLinkTree(this, a);
		}
	}

	public static class Detree extends Unary implements TreeConstruction {
		Detree(Expression e) {
			super(e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Detree) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitDetree(this, a);
		}

	}

	/* Symbol */
	private static final Expression empty = Expressions.newEmpty(null);

	public abstract static class Function extends Unary {
		public final FunctionName op;

		protected Function(FunctionName op, Expression e) {
			super(e);
			this.op = op;
		}

		public boolean hasInnerExpression() {
			return get(0) != empty;
		}
	}

	public abstract static class SymbolFunction extends Function {
		public final Symbol tableName;

		SymbolFunction(FunctionName op, Expression e, Symbol symbol) {
			super(op, e);
			tableName = symbol;
		}
	}

	static Symbol TableName(NonTerminal n, Symbol table) {
		if (table == null) {
			String u = n.getLocalName().replace("~", "");
			return Symbol.unique(u);
		}
		return table;
	}

	public static class SymbolAction extends SymbolFunction {
		SymbolAction(FunctionName op, NonTerminal e) {
			super(op, e, TableName(e, null));
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof SymbolAction) {
				SymbolAction e = (SymbolAction) o;
				if (tableName == e.tableName) {
					return get(0).equals(e.get(0));
				}
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitSymbolAction(this, a);
		}

	}

	public static class SymbolPredicate extends SymbolFunction {
		SymbolPredicate(FunctionName op, NonTerminal pat, Symbol table) {
			super(op, pat, TableName(pat, table));
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof SymbolPredicate) {
				SymbolPredicate e = (SymbolPredicate) o;
				return e.op == op && tableName == e.tableName;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitSymbolPredicate(this, a);
		}
	}

	public static class SymbolMatch extends SymbolFunction {

		SymbolMatch(FunctionName op, NonTerminal pat, Symbol table) {
			super(op, pat, TableName(pat, table));
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof SymbolMatch) {
				SymbolMatch e = (SymbolMatch) o;
				return e.op == op && tableName == e.tableName;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitSymbolMatch(this, a);
		}

	}

	public static class SymbolExists extends SymbolFunction {
		public final String symbol;

		SymbolExists(Symbol table, String symbol) {
			super(FunctionName.exists, empty, table);
			this.symbol = symbol;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof SymbolExists) {
				SymbolExists s = (SymbolExists) o;
				return tableName == s.tableName && equals(symbol, s.symbol);
			}
			return false;
		}

		private boolean equals(String s, String s2) {
			return Objects.equals(s, s2);
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitSymbolExists(this, a);
		}

	}

	public static class BlockScope extends Function {
		BlockScope(Expression e) {
			super(FunctionName.block, e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof BlockScope) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitBlockScope(this, a);
		}

	}

	public static class LocalScope extends SymbolFunction {

		LocalScope(Symbol table, Expression e) {
			super(FunctionName.local, e, table);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof LocalScope) {
				LocalScope s = (LocalScope) o;
				if (tableName == s.tableName) {
					return get(0).equals(s.get(0));
				}
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitLocalScope(this, a);
		}

	}

	public interface Conditional {

	}

	public static class OnCondition extends Function {
		public final boolean predicate;
		public final String flagName;

		OnCondition(boolean predicate, String c, Expression e) {
			super(FunctionName.on, e);
			this.predicate = predicate;
			this.flagName = c;
		}

		public final boolean isPositive() {
			return predicate;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.OnCondition) {
				Nez.OnCondition e = (Nez.OnCondition) o;
				if (predicate == e.predicate && flagName.equals(e.flagName)) {
					return get(0).equals(e.get(0));
				}
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitOn(this, a);
		}
	}

	public static class IfCondition extends Function implements Conditional {
		public final boolean predicate;
		public final String flagName;

		IfCondition(boolean predicate, String c) {
			super(FunctionName._if, empty);
			this.predicate = predicate;
			this.flagName = c;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.IfCondition) {
				Nez.IfCondition e = (Nez.IfCondition) o;
				return predicate == e.predicate && flagName.equals(e.flagName);
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitIf(this, a);
		}
	}

	public static class Scan extends Function {
		public final long mask;
		public final int shift;

		Scan(long mask, int shift, Expression e) {
			super(FunctionName.scanf, e);
			this.mask = mask;
			this.shift = shift;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Scan) {
				Nez.Scan e = (Nez.Scan) o;
				return mask == e.mask && shift == e.shift && get(0).equals(e.get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitScan(this, a);
		}
	}

	public static class Repeat extends Function {

		Repeat(Expression e) {
			super(FunctionName.repeat, e);
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Repeat) {
				return get(0).equals(((Expression) o).get(0));
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitRepeat(this, a);
		}
	}

	public static class Label extends Terminal {
		public final String label;
		public final boolean start;

		Label(String label, boolean start) {
			this.label = label;
			this.start = start;
		}

		@Override
		public final boolean equals(Object o) {
			if (o instanceof Nez.Label) {
				Nez.Label l = (Nez.Label) o;
				return label.equals(l.label) && start == l.start;
			}
			return false;
		}

		@Override
		public final Object visit(Expression.Visitor v, Object a) {
			return v.visitLabel(this, a);
		}
	}

}
