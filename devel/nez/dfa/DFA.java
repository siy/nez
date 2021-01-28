package nez.dfa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;

import nez.parser.io.StringSource;

public class DFA {
	private final HashSet<State> S;
	public TreeSet<Transition> tau;
	private State f;
	private final HashSet<State> F;

	public DFA() {
		S = new HashSet<>();
		tau = new TreeSet<>();
		f = new State();
		F = new HashSet<>();
	}

	public DFA(HashSet<State> S, TreeSet<Transition> tau, State f, HashSet<State> F) {
		this();
		for (State state : S) {
			this.S.add(new State(state.getID()));
		}
		for (Transition transition : tau) {
			this.tau.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate(), transition.getTheOthers()));
		}
		this.f = new State(f.getID());
		for (State state : F) {
			this.F.add(new State(state.getID()));
		}
	}

	public HashSet<State> getS() {
		return S;
	}

	public TreeSet<Transition> getTau() {
		return tau;
	}

	public State getf() {
		return f;
	}

	public HashSet<State> getF() {
		return F;
	}

	public ArrayList<ArrayList<Transition>> toAdjacencyList() {
		ArrayList<ArrayList<Transition>> adjacencyList = new ArrayList<>();
		for (int i = 0; i < S.size(); i++) {
			adjacencyList.add(new ArrayList<>());
		}
		for (Transition transition : tau) {
			int src = transition.getSrc();
			int dst = transition.getDst();
			adjacencyList.get(src).add(new Transition(src, dst, transition.getLabel(), -1));
		}
		return adjacencyList;
	}

	// DFA から逆向きのオートマトン(NFA)を生成する
	public NFA rev(DFA dfa) {
		HashSet<State> allStates = new HashSet<>();
		TreeSet<Transition> stateTransitionFunction = new TreeSet<>();
		HashSet<State> initialStates = new HashSet<>();
		HashSet<State> acceptingStates = new HashSet<>();

		for (State state : dfa.getS()) {
			allStates.add(new State(state.getID()));
		}

		for (Transition transition : dfa.getTau()) {
			stateTransitionFunction.add(new Transition(transition.getDst(), transition.getSrc(), transition.getLabel(), transition.getPredicate(), transition.getTheOthers()));
		}

		for (State state : dfa.getF()) {
			initialStates.add(new State(state.getID()));
		}

		acceptingStates.add(new State(dfa.getf().getID()));

		return new NFA(allStates, stateTransitionFunction, initialStates, acceptingStates);
	}

	// Brzozowski's algorithm
	// min(A) = det(rev(det(rev(A))))
	public DFA minimize() {
		return rev(rev(new DFA(S, tau, f, F)).det()).det();
	}

	public boolean exec(StringSource context) {
		ArrayList<ArrayList<Transition>> adjacencyList = toAdjacencyList();
		int stateID = getf().getID();
		for (int i = 0; i < context.length(); i++) {
			boolean found = false;
			for (Transition transition : adjacencyList.get(stateID)) {
				if (transition.getLabel() == context.byteAt(i)) { // FIXME
					stateID = transition.getDst();
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}

		return getF().contains(new State(stateID));
	}

	public String pexec(StringSource context) {
		return null;
	}

	public DFA minimize_Hopcroft() {
		return null;
	}

}
