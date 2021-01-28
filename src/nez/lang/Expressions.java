package nez.lang;

import java.util.Arrays;
import java.util.List;

import nez.ast.SourceLocation;
import nez.ast.Symbol;
import nez.util.StringUtils;
import nez.util.UList;

/**
 * This class consists of static utility methods operating an expression.
 * 
 * @author kiki
 *
 */

public abstract class Expressions {

	// ---------------------------------------------------------------------

	public static UList<Expression> newUList(int size) {
		return new UList<>(new Expression[size]);
	}

	public static List<Expression> newList(int size) {
		return new UList<>(new Expression[size]);
	}

	public static void addSequence(List<Expression> l, Expression e) {
		if (e instanceof Nez.Empty) {
			return;
		}
		if (e instanceof Nez.Sequence) {
			for (int i = 0; i < e.size(); i++) {
				addSequence(l, e.get(i));
			}
			return;
		}
		if (e instanceof Nez.Pair) {
			addSequence(l, e.get(0));
			addSequence(l, e.get(1));
			return;
		}
		if (l.size() > 0) {
			Expression prev = l.get(l.size() - 1);
			if (prev instanceof Nez.Fail) {
				return;
			}
		}
		l.add(e);
	}

	public static void addChoice(List<Expression> l, Expression e) {
		if (e instanceof Nez.Choice) {
			for (int i = 0; i < e.size(); i++) {
				addChoice(l, e.get(i));
			}
			return;
		}
		if (e instanceof Nez.Fail) {
			return;
		}
		if (l.size() > 0) {
			Expression prev = l.get(l.size() - 1);
			if (prev instanceof Nez.Empty) {
				return;
			}
		}
		l.add(e);
	}

	public static void swap(List<Expression> l, int i, int j) {
		Expression e = l.get(i);
		l.set(i, l.get(j));
		l.set(j, e);
	}

	// -----------------------------------------------------------------------

	public static NonTerminal newNonTerminal(Grammar g, String name) {
		return new NonTerminal(g, name);
	}

	public static NonTerminal newNonTerminal(SourceLocation s, Grammar g, String name) {
		NonTerminal e = new NonTerminal(g, name);
		e.setSourceLocation(s);
		return e;
	}

	// Immutable Expressions

	private static final Expression emptyExpression = new Nez.Empty();
	private static final Expression failExpression = new Nez.Fail();
	private static final Expression anyExpression = new Nez.Any();

	/**
	 * Creates an new empty expression, equals to '' in Nez.
	 * 
	 * @return
	 */

	public static Expression newEmpty() {
		return emptyExpression;
	}

	/**
	 * Creates an new empty expression, equals to '' in Nez.
	 * 
	 * @param s
	 * @return
	 */

	public static Expression newEmpty(SourceLocation s) {
		Expression e = new Nez.Empty();
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an always-fail expression, equals to !'' in Nez.
	 * 
	 * @return
	 */

	public static Expression newFail() {
		return failExpression;
	}

	/**
	 * Creates an always-fail expression, equals to !'' in Nez.
	 * 
	 * @param s
	 * @return
	 */

	public static Expression newFail(SourceLocation s) {
		Expression e = new Nez.Fail();
		e.setSourceLocation(s);
		return e;
	}

	/* Terminal */

	/**
	 * Creates an any character expression, equals to . in Nez.
	 */

	public static Expression newAny() {
		return anyExpression;
	}

	/**
	 * Creates an any character expression, equals to . in Nez.
	 * 
	 * @param s
	 * @return
	 */

	public static Expression newAny(SourceLocation s) {
		Expression e = new Nez.Any();
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an byte-matching expression, equals to c in Nez
	 * 
	 * @param c
	 * @return
	 */

	public static Expression newByte(int c) {
		return new Nez.Byte(c & 0xff);
	}

	/**
	 * Creates an byte-matching expression, equals to c in Nez
	 * 
	 * @param s
	 * @param c
	 * @return
	 */

	public static Expression newByte(SourceLocation s, int c) {
		Expression e = new Nez.Byte(c & 0xff);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an byte-set expression, equals to [c] in Nez
	 * 
	 * @param byteMap
	 * @return
	 */

	public static Expression newByteSet(boolean[] byteMap) {
		int byteChar = checkUniqueByteChar(byteMap);
		if (byteChar != -1) {
			return newByte(byteChar);
		}
		return new Nez.ByteSet(byteMap);
	}

	/**
	 * Creates an byte-set expression, equals to [c] in Nez
	 * 
	 * @param s
	 * @param byteset
	 * @return
	 */

	public static Expression newByteSet(SourceLocation s, boolean[] byteset) {
		int byteChar = checkUniqueByteChar(byteset);
		if (byteChar != -1) {
			return newByte(s, byteChar);
		}
		if (checkAnyChar(byteset)) {
			return newAny(s);
		}
		Expression e = new Nez.ByteSet(byteset);
		e.setSourceLocation(s);
		return e;
	}

	private static int checkUniqueByteChar(boolean[] byteset) {
		int byteChar = -1;
		for (int i = 0; i < 256; i++) {
			if (byteset[i]) {
				if (byteChar != -1) {
					return -1;
				}
				byteChar = i;
			}
		}
		return byteChar;
	}

	private static boolean checkAnyChar(boolean[] byteset) {
		for (int i = 0; i < 256; i++) {
			if (!byteset[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates a multibyte matching, equals to 'abc' in Nez.
	 * 
	 * @param utf8
	 * @return
	 */

	public static Expression newMultiByte(byte[] utf8) {
		if (utf8.length == 0) {
			return newEmpty();
		}
		if (utf8.length == 1) {
			return newByte(utf8[0]);
		}
		return new Nez.MultiByte(utf8);
	}

	/**
	 * Creates a multibyte matching, equals to 'abc' in Nez.
	 * 
	 * @param s
	 * @param utf8
	 * @return
	 */

	public static Expression newMultiByte(SourceLocation s, byte[] utf8) {
		if (utf8.length == 0) {
			return newEmpty(s);
		}
		if (utf8.length == 1) {
			return newByte(s, utf8[0]);
		}
		Expression e = new Nez.MultiByte(utf8);
		e.setSourceLocation(s);
		return e;
	}

	public final boolean requireBinaryHandle(Nez.Byte e) {
		return e.byteChar == 0;
	}

	public final boolean requireBinaryHandle(Nez.ByteSet e) {
		return e.byteset[0];
	}

	public final boolean requireBinaryHandle(Nez.Any e) {
		return true;
	}

	/* Unary */

	/**
	 * Creates an option expression, equals to e?.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newOption(Expression p) {
		return new Nez.Option(p);
	}

	/**
	 * Creates an option expression, equals to e?.
	 * 
	 * @param s
	 * @param p
	 * @return
	 */

	public static Expression newOption(SourceLocation s, Expression p) {
		Expression e = new Nez.Option(p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a zero-and-more repetition, equals to e*.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newZeroMore(Expression p) {
		return new Nez.ZeroMore(p);
	}

	/**
	 * Creates a zero-and-more repetition, equals to e*.
	 * 
	 * @param s
	 * @param p
	 * @return
	 */
	public static Expression newZeroMore(SourceLocation s, Expression p) {
		Expression e = new Nez.ZeroMore(p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a one-and-more repetition, equals to e+.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newOneMore(Expression p) {
		return new Nez.OneMore(p);
	}

	/**
	 * Creates a one-and-more repetition, equals to e+.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newOneMore(SourceLocation s, Expression p) {
		Expression e = new Nez.OneMore(p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an and-predicate, equals to &e.
	 * 
	 * @param s
	 * @param p
	 * @return
	 */

	public static Expression newAnd(SourceLocation s, Expression p) {
		Expression e = new Nez.And(p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an and-predicate, equals to &e.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newAnd(Expression p) {
		return new Nez.And(p);
	}

	/**
	 * Creates an negation-predicate, equals to !e.
	 * 
	 * @param s
	 * @param p
	 * @return
	 */

	public static Expression newNot(SourceLocation s, Expression p) {
		Expression e = new Nez.Not(p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates an negation-predicate, equals to !e.
	 * 
	 * @param p
	 * @return
	 */

	public static Expression newNot(Expression p) {
		return new Nez.Not(p);
	}

	/* Pair */

	/**
	 * Creates a pair of two expressions, equals to e1 e2 in Nez.
	 * 
	 * @param s
	 * @param p
	 * @param p2
	 * @return
	 */

	public static Expression newPair(SourceLocation s, Expression p, Expression p2) {
		UList<Expression> l = new UList<>(new Expression[2]);
		addSequence(l, p);
		addSequence(l, p2);
		return newPair(l);
	}

	/**
	 * Creates a pair expression from a list of expressions
	 * 
	 * @param l
	 *            a list of expressions
	 * @return
	 */

	public static Expression newPair(List<Expression> l) {
		if (l.size() == 0) {
			return newEmpty();
		}
		if (l.size() == 1) {
			return l.get(0);
		}
		return newPair(0, l);
	}

	private static Expression newPair(int start, List<Expression> l) {
		Expression first = l.get(start);
		if (start + 1 == l.size()) {
			return first;
		}
		return pair(first, newPair(start + 1, l));
	}

	public static Expression pair(Expression first, Expression second) {
		if (first instanceof Nez.Fail || second instanceof Nez.Fail) {
			return first;
		}
		if (second instanceof Nez.Empty) {
			return first;
		}
		if (first instanceof Nez.Empty) {
			return second;
		}
		return new Nez.Pair(first, second);
	}

	/**
	 * Creates a pair expression from a list of expressions
	 * 
	 * @param expressions
	 * @return
	 */

	public static Expression newPair(Expression... expressions) {
		UList<Expression> l = new UList<>(new Expression[expressions.length]);
		for (Expression e : expressions) {
			addSequence(l, e);
		}
		return newPair(l);
	}

	/* Pair */

	public static Expression first(Expression e) {
		if (e instanceof Nez.Pair || e instanceof Nez.Sequence) {
			return e.get(0);
		}
		return e;
	}

	public static Expression next(Expression e) {
		if (e instanceof Nez.Pair) {
			return e.get(1);
		}
		if (e instanceof Nez.Sequence) {
			Nez.Sequence seq = (Nez.Sequence) e;
			if (seq.size() == 1) {
				return null;
			}
			Expression[] inners = new Expression[seq.size() - 1];
			for (int i = 0; i < inners.length; i++) {
				inners[i] = seq.get(i + 1);
			}
			return new Nez.Sequence(inners);
		}
		return null;
	}

	public static List<Expression> flatten(Expression e) {
		UList<Expression> l = newUList(4);
		flatten(e, l);
		return l;
	}

	private static void flatten(Expression e, List<Expression> l) {
		if (e instanceof Nez.Pair) {
			flatten(e.get(0), l);
			flatten(e.get(1), l);
			return;
		}
		if (e instanceof Nez.Sequence) {
			for (Expression sub : e) {
				flatten(sub, e);
			}
			return;
		}
		if (e instanceof Nez.Empty) {
			return;
		}
		l.add(e);
	}

	/* Sequence */

	/**
	 * Creates a sequence expression, equals to e1 e2 ... in Nez.
	 * 
	 * @param l
	 *            a list of expressions
	 * @return
	 */

	public static Expression newSequence(List<Expression> l) {
		if (l.size() == 0) {
			return newEmpty();
		}
		if (l.size() == 1) {
			return l.get(0);
		}
		return new Nez.Sequence(compact(l));
	}

	private static Expression[] compact(List<Expression> l) {
		Expression[] a = new Expression[l.size()];
		for (int i = 0; i < l.size(); i++) {
			a[i] = l.get(i);
		}
		return a;
	}

	/**
	 * Creates a sequence expression, equals to e1 e2 ... in Nez.
	 * 
	 * @param expressions
	 * @return
	 */

	public static Expression newSequence(Expression... expressions) {
		UList<Expression> l = new UList<>(new Expression[expressions.length]);
		for (Expression e : expressions) {
			addSequence(l, e);
		}
		return newSequence(l);
	}

	/* Choice */

	/**
	 * Creates a new choice from a list of expressions
	 * 
	 * @param l
	 * @return
	 */

	public static Expression newChoice(List<Expression> l) {
		int size = l.size();
		for (int i = 0; i < size; i++) {
			if (l.get(i) instanceof Nez.Empty) {
				size = i + 1;
				break;
			}
		}
		if (size == 1) {
			return l.get(0);
		}
		Expression[] inners = new Expression[size];
		for (int i = 0; i < size; i++) {
			inners[i] = l.get(i);
		}
		return new Nez.Choice(inners);
	}

	/**
	 * Creates a choice from two expressions
	 * 
	 * @param p
	 * @param p2
	 * @return
	 */

	public static Expression newChoice(Expression p, Expression p2) {
		if (p == null) {
			return p2;
		}
		if (p2 == null) {
			return p;
		}
		UList<Expression> l = new UList<>(new Expression[2]);
		addChoice(l, p);
		addChoice(l, p2);
		return newChoice(l);
	}

	public static Expression tryCommonFactoring(Nez.Choice choice) {
		List<Expression> l = newList(choice.size());
		int[] indexes = new int[256];
		Arrays.fill(indexes, -1);
		tryCommonFactoring(choice, l, indexes);
		for (int i = 0; i < l.size(); i++) {
			l.set(i, tryChoiceCommonFactring(l.get(i)));
		}
		return newChoice(l);
	}

	private static void tryCommonFactoring(Nez.Choice choice, List<Expression> l, int[] indexes) {
		for (Expression inner : choice) {
			if (inner instanceof Nez.Choice) {
				tryCommonFactoring((Nez.Choice) inner, l, indexes);
				continue;
			}
			Expression first = first(inner);
			if (first instanceof Nez.Byte) {
				int ch = ((Nez.Byte) first).byteChar;
				if (indexes[ch] == -1) {
					indexes[ch] = l.size();
					l.add(inner);
				} else {
					Expression prev = l.get(indexes[ch]);
					Expression second = newChoice(next(prev), next(inner));
					prev = newPair(prev.getSourceLocation(), first, second);
					l.set(indexes[ch], prev);
				}
			} else {
				addChoice(l, inner);
			}
		}
	}

	public static Expression tryChoiceCommonFactring(Expression e) {
		if (e instanceof Nez.Choice) {
			return tryCommonFactoring((Nez.Choice) e);
		}
		for (int i = 0; i < e.size(); i++) {
			Expression sub = e.get(i);
			if (sub instanceof Nez.Choice) {
				e.set(i, tryCommonFactoring((Nez.Choice) sub));
			}
		}
		return e;
	}

	public static Expression newDispatch(Expression[] inners, byte[] indexMap) {
		return new Nez.Dispatch(inners, indexMap);
	}

	// AST Construction

	public static Expression newDetree(Expression p) {
		return new Nez.Detree(p);
	}

	public static Expression newDetree(SourceLocation s, Expression p) {
		Expression e = new Nez.Detree(p);
		e.setSourceLocation(s);
		return e;
	}

	public static Expression newLinkTree(Expression p) {
		return newLinkTree(null, null, p);
	}

	public static Expression newLinkTree(SourceLocation s, Expression p) {
		return newLinkTree(s, null, p);
	}

	public static Expression newLinkTree(Symbol label, Expression p) {
		return new Nez.LinkTree(label, p);
	}

	public static Expression newLinkTree(SourceLocation s, Symbol label, Expression p) {
		Expression e = new Nez.LinkTree(label, p);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a { expression
	 * 
	 * @return
	 */
	public static Expression newBeginTree() {
		return new Nez.BeginTree(0);
	}

	/**
	 * Creates a { expression.
	 * 
	 * @param s
	 * @param shift
	 * @return
	 */

	public static Expression newBeginTree(SourceLocation s, int shift) {
		Expression e = new Nez.BeginTree(shift);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a } expression.
	 * 
	 * @return
	 */
	public static Expression newEndTree() {
		return new Nez.EndTree(0);
	}

	/**
	 * Creates a } expression.
	 * 
	 * @param s
	 * @param shift
	 * @return
	 */

	public static Expression newEndTree(SourceLocation s, int shift) {
		Expression e = new Nez.EndTree(shift);
		e.setSourceLocation(s);
		return e;
	}

	public static Expression newFoldTree(SourceLocation s, Symbol label, int shift) {
		Expression e = new Nez.FoldTree(shift, label);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a #tag expression.
	 * 
	 * @param tag
	 * @return
	 */

	public static Expression newTag(Symbol tag) {
		return new Nez.Tag(tag);
	}

	/**
	 * Creates a #tag expression.
	 * 
	 * @param s
	 * @param tag
	 * @return
	 */

	public static Expression newTag(SourceLocation s, Symbol tag) {
		Expression e = new Nez.Tag(tag);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates a `v` expression.
	 * 
	 * @param v
	 * @return
	 */

	public static Expression newReplace(String v) {
		return new Nez.Replace(v);
	}

	/**
	 * Creates a `v` expression.
	 * 
	 * @param s
	 * @param v
	 * @return
	 */

	public static Expression newReplace(SourceLocation s, String v) {
		Expression e = new Nez.Replace(v);
		e.setSourceLocation(s);
		return e;
	}

	/* Conditional Parsing */

	/**
	 * Creates <if C>
	 * 
	 * @param c
	 * @return
	 */
	public static Expression newIfCondition(String c) {
		return newIfCondition(null, c);
	}

	/**
	 * Creates <if C>
	 * 
	 * @param s
	 * @param c
	 * @return
	 */

	public static Expression newIfCondition(SourceLocation s, String c) {
		boolean predicate = true;
		if (c.startsWith("!")) {
			predicate = false;
			c = c.substring(1);
		}
		Expression e = new Nez.IfCondition(predicate, c);
		e.setSourceLocation(s);
		return e;
	}

	/**
	 * Creates <on C e>
	 * 
	 * @param c
	 * @param e
	 * @return
	 */

	public static Expression newOnCondition(String c, Expression e) {
		return newOnCondition(null, c, e);
	}

	/**
	 * Creates <on C e>
	 * 
	 * @param s
	 * @param c
	 * @param e
	 * @return
	 */

	public static Expression newOnCondition(SourceLocation s, String c, Expression e) {
		boolean predicate = true;
		if (c.startsWith("!")) {
			predicate = false;
			c = c.substring(1);
		}
		Expression p = new Nez.OnCondition(predicate, c, e);
		p.setSourceLocation(s);
		return p;
	}

	/* Symbol Table */

	/**
	 * Creates a <block e> expression
	 * 
	 * @param e
	 * @return
	 */
	public static Expression newBlockScope(Expression e) {
		return new Nez.BlockScope(e);
	}

	/**
	 * Creates a <block e> expression
	 * 
	 * @param s
	 * @param e
	 * @return
	 */

	public static Expression newBlockScope(SourceLocation s, Expression e) {
		Expression p = new Nez.BlockScope(e);
		p.setSourceLocation(s);
		return p;
	}

	/**
	 * Creates a <local Name e> expression
	 * 
	 * @param tableName
	 * @param e
	 * @return
	 */
	public static Expression newLocalScope(Symbol tableName, Expression e) {
		return new Nez.LocalScope(tableName, e);
	}

	/**
	 * Creates a <local A e> expression
	 * 
	 * @param s
	 * @param tableName
	 * @param e
	 * @return
	 */

	public static Expression newLocalScope(SourceLocation s, Symbol tableName, Expression e) {
		Expression p = new Nez.LocalScope(tableName, e);
		p.setSourceLocation(s);
		return p;
	}

	/**
	 * Creates a <symbol A> expression
	 * 
	 * @param pat
	 * @return
	 */

	public static Expression newSymbol(NonTerminal pat) {
		return new Nez.SymbolAction(FunctionName.symbol, pat);
	}

	/**
	 * Crates a <symbol A> expression
	 * 
	 * @param s
	 * @param pat
	 * @return
	 */

	public static Expression newSymbol(SourceLocation s, NonTerminal pat) {
		Expression p = new Nez.SymbolAction(FunctionName.symbol, pat);
		p.setSourceLocation(s);
		return p;

	}

	/**
	 * Creates a <exists A> expression.
	 * 
	 * @param tableName
	 * @return
	 */
	public static Expression newSymbolExists(Symbol tableName) {
		return new Nez.SymbolExists(tableName, null);
	}

	/**
	 * Creates a <exists A> expression.
	 * 
	 * @param s
	 * @param tableName
	 * @return
	 */

	public static Expression newSymbolExists(SourceLocation s, Symbol tableName) {
		Expression p = new Nez.SymbolExists(tableName, null);
		p.setSourceLocation(s);
		return p;

	}

	/**
	 * Creates a <exists A x> expression.
	 * 
	 * @param tableName
	 * @param symbol
	 * @return
	 */

	public static Expression newSymbolExists(Symbol tableName, String symbol) {
		return new Nez.SymbolExists(tableName, symbol);
	}

	/**
	 * Creates a <exists A x> expression.
	 * 
	 * @param s
	 * @param tableName
	 * @param symbol
	 * @return
	 */

	public static Expression newSymbolExists(SourceLocation s, Symbol tableName, String symbol) {
		Expression p = new Nez.SymbolExists(tableName, symbol);
		p.setSourceLocation(s);
		return p;
	}

	/**
	 * Creates a <match A> expression
	 * 
	 * @return
	 */

	public static Expression newSymbolMatch(NonTerminal pat) {
		return new Nez.SymbolMatch(FunctionName.match, pat, null);
	}

	/**
	 * Creates a <match A> expression
	 * 
	 * @param s
	 * @return
	 */
	public static Expression newSymbolMatch(SourceLocation s, NonTerminal pat) {
		Expression p = new Nez.SymbolMatch(FunctionName.match, pat, null);
		p.setSourceLocation(s);
		return p;
	}

	/**
	 * Creates a <is A> expression.
	 * 
	 * @param pat
	 * @return
	 */

	public static Expression newIsSymbol(NonTerminal pat) {
		return new Nez.SymbolPredicate(FunctionName.is, pat, null);
	}

	/**
	 * Creates a <is A> expression.
	 * 
	 * @param s
	 * @param pat
	 * @return
	 */

	public static Expression newIsSymbol(SourceLocation s, NonTerminal pat) {
		Expression p = new Nez.SymbolPredicate(FunctionName.is, pat, null);
		p.setSourceLocation(s);
		return p;
	}

	/**
	 * Creates a <isa A> expression.
	 * 
	 * @param pat
	 * @return
	 */

	public static Expression newIsaSymbol(NonTerminal pat) {
		return new Nez.SymbolPredicate(FunctionName.isa, pat, null);
	}

	/**
	 * Creates a <isa A> expression.
	 * 
	 * @param s
	 * @param pat
	 * @return
	 */

	public static Expression newIsaSymbol(SourceLocation s, NonTerminal pat) {
		Expression p = new Nez.SymbolPredicate(FunctionName.isa, pat, null);
		p.setSourceLocation(s);
		return p;
	}

	public static Expression newScanf(String mask, Expression e) {
		return newScanf(null, mask, e);
	}

	public static Expression newScanf(SourceLocation s, String mask, Expression e) {
		long bits = 0;
		int shift = 0;
		if (mask != null) {
			bits = Long.parseUnsignedLong(mask, 2);
			long m = bits;
			while ((m & 1L) == 0) {
				m >>= 1;
				shift++;
			}
			// Verbose.println("@@ mask=%s, shift=%d,%d", mask, bits, shift);
		}
		Expression p = new Nez.Scan(bits, shift, e);
		p.setSourceLocation(s);
		return p;
	}

	public static Expression newRepeat(Expression e) {
		return new Nez.Repeat(e);
	}

	public static Expression newRepeat(SourceLocation s, Expression e) {
		Expression p = new Nez.Repeat(e);
		p.setSourceLocation(s);
		return p;
	}

	// -----------------------------------------------------------------------

	public static Expression newExpression(String text) {
		return newExpression(null, text);
	}

	public static Expression newExpression(SourceLocation s, String text) {
		byte[] utf8 = StringUtils.utf8(text);
		if (utf8.length == 0) {
			return newEmpty(s);
		}
		if (utf8.length == 1) {
			return newByte(s, utf8[0]);
		}
		return newByteSequence(s, false, utf8);
	}

	public static Expression newByteSequence(SourceLocation s, boolean binary, byte[] utf8) {
		UList<Expression> l = new UList<>(new Expression[utf8.length]);
		for (byte b : utf8) {
			l.add(newByte(s, b));
		}
		return newPair(l);
	}

	public static Expression newCharSet(SourceLocation s, String text) {
		boolean[] b = StringUtils.parseByteMap(text);
		return newByteSet(s, b);
	}

	public static Expression newCharSet(SourceLocation s, String t, String t2) {
		int c = StringUtils.parseAscii(t);
		int c2 = StringUtils.parseAscii(t2);
		if (c != -1 && c2 != -1) {
			return newByteRange(s, false, c, c2);
		}
		c = StringUtils.parseUnicode(t);
		c2 = StringUtils.parseUnicode(t2);
		if (c < 128 && c2 < 128) {
			return newByteRange(s, false, c, c2);
		} else {
			return newUnicodeRange(s, c, c2);
		}
	}

	public static Expression newByteRange(SourceLocation s, boolean binary, int c, int c2) {
		if (c == c2) {
			return newByte(s, c);
		}
		boolean[] byteMap = Bytes.newMap(false);
		Bytes.appendRange(byteMap, c, c2);
		return newByteSet(s, byteMap);
	}

	private static Expression newUnicodeRange(SourceLocation s, int c, int c2) {
		byte[] b = StringUtils.utf8(String.valueOf((char) c));
		byte[] b2 = StringUtils.utf8(String.valueOf((char) c2));
		if (equalsBase(b, b2)) {
			return newUnicodeRange(s, b, b2);
		}
		UList<Expression> l = new UList<>(new Expression[b.length]);
		b2 = b;
		for (int pc = c + 1; pc <= c2; pc++) {
			byte[] b3 = StringUtils.utf8(String.valueOf((char) pc));
			if (equalsBase(b, b3)) {
				b2 = b3;
				continue;
			}
			l.add(newUnicodeRange(s, b, b2));
			b = b3;
			b2 = b3;
		}
		b2 = StringUtils.utf8(String.valueOf((char) c2));
		l.add(newUnicodeRange(s, b, b2));
		return newChoice(l);
	}

	private static boolean equalsBase(byte[] b, byte[] b2) {
		if (b.length == b2.length) {
			switch (b.length) {
			case 3:
				return b[0] == b2[0] && b[1] == b2[1];
			case 4:
				return b[0] == b2[0] && b[1] == b2[1] && b[2] == b2[2];
			}
			return b[0] == b2[0];
		}
		return false;
	}

	private static Expression newUnicodeRange(SourceLocation s, byte[] b, byte[] b2) {
		if (b[b.length - 1] == b2[b.length - 1]) {
			return newByteSequence(s, false, b);
		} else {
			UList<Expression> l = new UList<>(new Expression[b.length]);
			for (int i = 0; i < b.length - 1; i++) {
				l.add(newByte(s, b[i]));
			}
			l.add(newByteRange(s, false, b[b.length - 1] & 0xff, b2[b2.length - 1] & 0xff));
			return newPair(l);
		}
	}

	public static Expression newTree(SourceLocation s, Expression e) {
		return newTree(s, false, null, e);
	}

	public static Expression newTree(SourceLocation s, boolean lefted, Symbol label, Expression e) {
		UList<Expression> l = new UList<>(new Expression[e.size() + 3]);
		addSequence(l, lefted ? newFoldTree(s, label, 0) : newBeginTree(s, 0));
		addSequence(l, e);
		addSequence(l, newEndTree(s, 0));
		return newPair(l);
	}

	public static Expression newLeftFoldOption(SourceLocation s, Symbol label, Expression e) {
		UList<Expression> l = new UList<>(new Expression[e.size() + 3]);
		addSequence(l, newFoldTree(s, label, 0));
		addSequence(l, e);
		addSequence(l, newEndTree(s, 0));
		return newOption(s, newPair(l));
	}

	public static Expression newLeftFoldRepetition(SourceLocation s, Symbol label, Expression e) {
		UList<Expression> l = new UList<>(new Expression[e.size() + 3]);
		addSequence(l, newFoldTree(s, label, 0));
		addSequence(l, e);
		addSequence(l, newEndTree(s, 0));
		return newZeroMore(s, newPair(l));
	}

	public static Expression newLeftFoldRepetition1(SourceLocation s, Symbol label, Expression e) {
		UList<Expression> l = new UList<>(new Expression[e.size() + 3]);
		addSequence(l, newFoldTree(s, label, 0));
		addSequence(l, e);
		addSequence(l, newEndTree(s, 0));
		return newOneMore(s, newPair(l));
	}

	/* Optimization */

	public static Expression resolveNonTerminal(Expression e) {
		while (e instanceof NonTerminal) {
			NonTerminal nterm = (NonTerminal) e;
			e = nterm.deReference();
		}
		return e;
	}

	public static Expression tryConvertingByteSet(Nez.Choice choice) {
		boolean[] byteMap = Bytes.newMap(false);
		if (tryConvertingByteSet(choice, byteMap)) {
			return newByteSet(choice.getSourceLocation(), byteMap);
		}
		return choice;
	}

	private static boolean tryConvertingByteSet(Nez.Choice choice, boolean[] byteMap) {
		for (Expression e : choice) {
			e = resolveNonTerminal(e);
			if (e instanceof Nez.Choice) {
				if (!tryConvertingByteSet((Nez.Choice) e, byteMap)) {
					return false;
				}
				continue; // OK
			}
			if (e instanceof Nez.Byte) {
				byteMap[((Nez.Byte) e).byteChar] = true;
				continue; // OK
			}
			if (e instanceof Nez.ByteSet) {
				Bytes.appendBitMap(byteMap, ((Nez.ByteSet) e).byteset);
				continue; // OK
			}
			return false;
		}
		return true;
	}

	public static Expression tryConvertingMultiCharSequence(Nez.Pair e) {
		if (isFirstChar(e) && isSecondChar(e)) {
			UList<Byte> bytes = new UList<>(new Byte[8]);
			Expression first = first(e);
			bytes.add((byte) ((Nez.Byte) first).byteChar);
			Expression next = next(e);
			while (true) {
				if (next instanceof Nez.Byte) {
					bytes.add((byte) ((Nez.Byte) next).byteChar);
					next = null;
					break;
				}
				if (next instanceof Nez.Pair) {
					first = next.get(0);
					if (first instanceof Nez.Byte) {
						bytes.add((byte) ((Nez.Byte) first).byteChar);
						next = next.get(1);
						continue;
					}
				}
				break;
			}
			byte[] byteSeq = new byte[bytes.size()];
			for (int i = 0; i < bytes.size(); i++) {
				byteSeq[i] = bytes.get(i);
			}
			first = newMultiByte(e.getSourceLocation(), byteSeq);
			if (next == null) {
				return first;
			}
			return pair(first, next);
		}
		return e;
	}

	private static boolean isFirstChar(Expression e) {
		if (e instanceof Nez.Pair || e instanceof Nez.Sequence) {
			return e.get(0) instanceof Nez.Byte;
		}
		return false;
	}

	private static boolean isSecondChar(Expression e) {
		if (e instanceof Nez.Pair) {
			e = e.get(1);
			if (e instanceof Nez.Pair || e instanceof Nez.Sequence) {
				return e.get(0) instanceof Nez.Byte;
			}
			return e instanceof Nez.Byte;
		}
		if (e instanceof Nez.Sequence) {
			return e.get(1) instanceof Nez.Byte;
		}
		return false;
	}

	public static Expression newCoverage(String label, Expression e) {
		List<Expression> l = newList(e.size() + 3);
		l.add(new Nez.Label(label, true));
		addSequence(l, e);
		l.add(new Nez.Label(label, false));
		return newPair(l);
	}
}
