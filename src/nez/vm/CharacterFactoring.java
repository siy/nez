package nez.vm;

import nez.lang.Expression;
import nez.lang.GrammarReshaper;
import nez.lang.expr.AnyChar;
import nez.lang.expr.ByteChar;
import nez.lang.expr.ByteMap;
import nez.lang.expr.Choice;
import nez.lang.expr.GrammarFactory;
import nez.lang.expr.Sequence;
import nez.util.UList;

public class CharacterFactoring extends GrammarReshaper {
	public final static CharacterFactoring s = new CharacterFactoring();

	/**
	 * try factoring character
	 * 
	 * @param e
	 * @return
	 */

	public Expression tryFactoringCharacter(Expression e) {
		Expression r = e.reshape(this);
		return r == e ? null : r;
	}

	public Expression reshapeByteChar(ByteChar e) {
		return empty(e);
	}

	public Expression reshapeByteMap(ByteMap e) {
		return empty(e);
	}

	public Expression reshapeAnyChar(AnyChar e) {
		return empty(e);
	}

	public Expression reshapeSequence(Sequence e) {
		Expression first = e.get(0).reshape(this);
		if (first == e.get(0)) {
			return e;
		}
		UList<Expression> l = new UList<Expression>(new Expression[e.size()]);
		GrammarFactory.addSequence(l, first);
		for (int i = 1; i < e.size(); i++) {
			l.add(e.get(i));
		}
		return GrammarFactory.newSequence(e.getSourcePosition(), l);
	}

	public Expression reshapeChoice(Choice e) {
		UList<Expression> l = new UList<Expression>(new Expression[e.size()]);
		for (Expression sub : e) {
			Expression p = sub.reshape(this);
			if (p == sub) {
				return e;
			}
		}
		return GrammarFactory.newChoice(e.getSourcePosition(), l);
	}

}
