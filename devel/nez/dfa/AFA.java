package nez.dfa;

import java.util.HashSet;
import java.util.TreeSet;

public class AFA {
	// final public static char epsilon = ' ';
	public static final int theOthers = -3;
	public static final int anyCharacter = -2;
	public static final int epsilon = -1;
	private HashSet<State> S;
	private TreeSet<Transition> tau;
	private final State f;
	private HashSet<State> F;
	private HashSet<State> L;

	public AFA(HashSet<State> S, TreeSet<Transition> tau, State f, HashSet<State> F, HashSet<State> L) {
		this.S = S;
		this.tau = tau;
		this.f = f;
		this.F = F;
		this.L = L;
	}

	public void setS(HashSet<State> S) {
		this.S = S;
	}

	public void setF(HashSet<State> F) {
		this.F = F;
	}

	public void setL(HashSet<State> L) {
		this.L = L;
	}

	public void setTau(TreeSet<Transition> tau) {
		this.tau = tau;
	}

	public HashSet<State> getS() {
		return S;
	}

	public State getf() {
		return f;
	}

	public HashSet<State> getF() {
		return F;
	}

	public HashSet<State> getL() {
		return L;
	}

	public TreeSet<Transition> getTau() {
		return tau;
	}
}
