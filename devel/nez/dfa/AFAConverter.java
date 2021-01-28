package nez.dfa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import nez.ast.TreeVisitorMap;
import nez.dfa.AFAConverter.DefaultVisitor;
import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.Production;

/*
 * 
 * 非終端記号ー制約：
 * 	非終端記号の初期状態と受理状態は１つ
 * 	複数存在する場合は新たな状態を作成し、それを初期（受理）状態とし、それにε遷移をはるようにする
 * 	
 */
public class AFAConverter extends TreeVisitorMap<DefaultVisitor> {

	private final boolean showTrace = false;
	private Grammar grammar;
	private AFA afa;
	private final HashMap<String, State> initialStateOfNonTerminal; // 非終端記号の初期状態は１つ
	// 非終端記号の受理状態は１つ
	private int theNumberOfStates;
	private final String StartProduction = "Start";

	public AFAConverter() {
		this.initialStateOfNonTerminal = new HashMap<>();
		this.theNumberOfStates = 0;
		init(AFAConverter.class, new DefaultVisitor());
	}

	public AFAConverter(Grammar grammar) {
		this.grammar = grammar;
		this.initialStateOfNonTerminal = new HashMap<>();
		this.theNumberOfStates = 0;
		init(AFAConverter.class, new DefaultVisitor());
	}

	public void build() {
		System.out.println("This function may be out of date :: use build(Production)");
		Production p = grammar.getProduction(StartProduction);
		if (p == null) {
			System.out.println("ERROR : START PRODUCTION \"" + StartProduction + "\" ... NOT FOUND");
			System.out.println("BUILD FAILED");
			return;
		}

		HashSet<String> visitedNonTerminal = new HashSet<>();
		HashMap<String, Integer> nonTerminalToVertexID = new HashMap<>();
		HashSet<String> visitedNonTerminalInPredicate = new HashSet<>();
		HashMap<String, Integer> nonTerminalToVertexIDInPredicate = new HashMap<>();

		this.afa = visitProduction(p, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, false);
		TreeSet<Transition> tau = afa.getTau();
		for (State state : afa.getL()) {
			tau.add(new Transition(state.getID(), state.getID(), AFA.anyCharacter, -1));
		}
		afa.setTau(tau);

		this.afa = eliminateEpsilonCycle(afa);
		this.afa = relabel(afa);
	}

	public void buildForPartial(Production p) {
		if (p == null) {
			System.out.println("ERROR : START PRODUCTION \"" + StartProduction + "\" ... NOT FOUND");
			System.out.println("BUILD FAILED");
			return;
		}

		System.out.println(p.getLocalName() + " => " + p.getExpression());
		ProductionConverterOfPrioritizedChoice pconverter = new ProductionConverterOfPrioritizedChoice();
		p.setExpression(pconverter.convert(p));
		System.out.println(p.getLocalName() + " => " + p.getExpression());

		HashSet<String> visitedNonTerminal = new HashSet<>();
		HashMap<String, Integer> nonTerminalToVertexID = new HashMap<>();
		HashSet<String> visitedNonTerminalInPredicate = new HashSet<>();
		HashMap<String, Integer> nonTerminalToVertexIDInPredicate = new HashMap<>();
		System.out.println("AFA construction START : " + p.getExpression());
		this.afa = visitProduction(p, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, false);
		TreeSet<Transition> tau = afa.getTau();
		for (State state : afa.getL()) {
			tau.add(new Transition(state.getID(), state.getID(), AFA.anyCharacter, -1));
		}

		HashSet<State> tmpS = afa.getS();
		HashSet<State> tmpF = new HashSet<>();
		for (State state : afa.getF()) {
			int stateID = getNewStateID();
			tmpS.add(new State(stateID));
			tmpF.add(new State(stateID));
			tau.add(new Transition(state.getID(), stateID, AFA.epsilon, -1));
			tau.add(new Transition(stateID, stateID, AFA.theOthers, -1));
		}
		afa.setTau(tau);
		afa.setS(tmpS);
		afa.setF(tmpF);
		System.out.println("AFA construction END");
		this.afa = relabel(afa);
		System.out.println("=== AFA BUILD FINISHED ===");
	}

	// Relabel state IDs from 0 to n
	public AFA relabel(AFA tmpAfa) {
		if (tmpAfa == null) {
			System.out.println("WARNING :: AFAConverter :: relabel :: argument tmpAfa is null");
			return null;
		}

		// DOTGenerator.writeAFA(tmpAfa);

		Map<Integer, Integer> newStateIDs = new HashMap<>();

		HashSet<State> S = new HashSet<>();
		TreeSet<Transition> transitions = new TreeSet<>();
		State f;
		HashSet<State> F = new HashSet<>();
		HashSet<State> L = new HashSet<>();

		int newID = 0;
		for (State state : tmpAfa.getS()) {
			newStateIDs.put(state.getID(), newID);
			S.add(new State(newID++));
		}

		f = new State(newStateIDs.get(tmpAfa.getf().getID()));
		for (Transition t : tmpAfa.getTau()) {
			transitions.add(new Transition(newStateIDs.get(t.getSrc()), newStateIDs.get(t.getDst()), t.getLabel(), t.getPredicate()));
		}

		for (State state : tmpAfa.getF()) {
			F.add(new State(newStateIDs.get(state.getID())));
		}

		for (State state : tmpAfa.getL()) {
			L.add(new State(newStateIDs.get(state.getID())));
		}

		return new AFA(S, transitions, f, F, L);
	}

	/*
	 * Start = ('a'*)* のように繰り返しを繰り返すとε遷移の閉路ができる
	 */
	public AFA eliminateEpsilonCycle(AFA argAfa) {
		StronglyConnectedComponent scc = new StronglyConnectedComponent(theNumberOfStates, argAfa);
		return scc.removeEpsilonCycle();
	}

	public AFA getAFA() {
		return afa;
	}

	public int getNewStateID() {
		return theNumberOfStates++;
	}

	public DFA computeDFA() {
		DFAConverter dfaConverter = new DFAConverter(afa);
		return dfaConverter.convert();
	}

	// <----- Visitor ----->

	private AFA visitProduction(Production rule, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate,
			boolean inPredicate) {
		if (showTrace) {
			System.out.println("here is Production : " + rule.getLocalName() + " " + (inPredicate ? "(in predicate)" : ""));
		}
		// return visitExpression(rule.getExpression());
		return visit(rule.getExpression(), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);
	}

	public AFA visit(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
		return find(e.getClass().getSimpleName()).accept(e, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);
	}

	public static class DefaultVisitor {
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			System.out.println("ERROR :: INVALID INSTANCE : WHAT IS " + e);
			return null;
		}
	}

	public class Empty extends DefaultVisitor {

		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pempty : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			int s = getNewStateID();
			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));

			F.add(new State(s));

			return new AFA(S, transitions, f, F, L);
		}
	}

	public class Fail extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pfail : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}
			System.out.println("ERROR : WHAT IS Pfail : UNIMPLEMENTED FUNCTION");
			return null;
		}
	}

	public class Any extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Cany : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			int s = getNewStateID();
			int t = getNewStateID();
			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			S.add(new State(t));

			// transitions.add(new Transition(s, t, '.', -1));
			transitions.add(new Transition(s, t, AFA.anyCharacter, -1));

			F.add(new State(t));

			return new AFA(S, transitions, f, F, L);
		}
	}

	public class Byte extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Cbyte : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			int s = getNewStateID();
			int t = getNewStateID();
			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			S.add(new State(t));

			transitions.add(new Transition(s, t, ((nez.lang.Nez.Byte) e).byteChar, -1));

			F.add(new State(t));

			return new AFA(S, transitions, f, F, L);
		}
	}

	public class ByteSet extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Cset : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			int s = getNewStateID();
			int t = getNewStateID();
			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			S.add(new State(t));

			for (int i = 0; i < 256; i++) {
				// if (((nez.lang.expr.Cset) e).byteMap[i]) {
				if (((nez.lang.Nez.ByteSet) e).byteset[i]) {
					transitions.add(new Transition(s, t, i, -1));
				}
			}

			F.add(new State(t));

			return new AFA(S, transitions, f, F, L);
		}
	}

	// e? ... e / !e ''
	public class Option extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Poption : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}
			AFA tmpAFA1 = visit(e.get(0), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate); // e
			AFA tmpAFA2 = computePnotAFA(copyAFA(tmpAFA1, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate)); // !e

			int s = getNewStateID();

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			for (State state : tmpAFA1.getS()) {
				S.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getS()) {
				S.add(new State(state.getID()));
			}

			transitions.add(new Transition(s, tmpAFA1.getf().getID(), AFA.epsilon, -1));
			transitions.add(new Transition(s, tmpAFA2.getf().getID(), AFA.epsilon, -1));
			for (Transition transition : tmpAFA1.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}
			for (Transition transition : tmpAFA2.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			for (State state : tmpAFA1.getF()) {
				F.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getF()) {
				F.add(new State(state.getID()));
			}
			if (tmpAFA2.getF().size() != 1) {
				System.out.println("FATAL ERROR : AFAConverter : Poption : tmpAFA2.getF().size() should be 1");
			}

			for (State state : tmpAFA1.getL()) {
				L.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getL()) {
				L.add(new State(state.getID()));
			}

			return new AFA(S, transitions, f, F, L);
		}
	}

	// theNumberOfStatesを利用し、新たに状態に番号を割り振るためAFA内ではなくここに書いてある
	private AFA copyAFA(AFA base, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate) {
		if (base == null) {
			System.out.println("WARNING :: AFAConverter :: copyAFA :: argument base is null");
			return null;
		}
		HashSet<State> S = new HashSet<>();
		TreeSet<Transition> tau = new TreeSet<>();
		State f = new State();
		HashSet<State> F = new HashSet<>();
		HashSet<State> L = new HashSet<>();

		HashMap<Integer, Integer> newStateIDTable = new HashMap<>();

		for (State state : base.getS()) {
			int newStateID;
			//FIXME: looking for integer keys inside Map<String, Integer>!!!!
			if (nonTerminalToVertexID.containsKey(state.getID()) || nonTerminalToVertexIDInPredicate.containsKey(state.getID())) {
				newStateID = state.getID();
			} else {
				newStateID = getNewStateID();
			}
			newStateIDTable.put(state.getID(), newStateID);
			S.add(new State(newStateID));
		}

		for (Transition transition : base.getTau()) {
			int src = transition.getSrc();
			int dst = transition.getDst();
			int label = transition.getLabel();
			int predicate = transition.getPredicate();
			if (!newStateIDTable.containsKey(src)) {
				System.out.println("WARNING :: AFAConverter :: copyAFA :: newStateIDTable.containsKey(" + src + ") is false");
			}
			if (!newStateIDTable.containsKey(dst)) {
				DOTGenerator.writeAFA(base);
				for (State state : base.getS()) {
					System.out.println("nyan state = " + state);
				}
				try {
					int x = 0;
					for (int i = 0; i < 2000000000; i++) {
						x = x + i;
					}
					System.out.println(x);
				} catch (Exception e) {
				}
				System.out.println("WARNING :: AFAConverter :: copyAFA :: newStateIDTable.containsKey(" + dst + ") is false");
			}

			tau.add(new Transition(newStateIDTable.get(src), newStateIDTable.get(dst), label, predicate));
		}

		f = new State(newStateIDTable.get(base.getf().getID()));

		for (State state : base.getF()) {
			F.add(new State(newStateIDTable.get(state.getID())));
		}

		for (State state : base.getL()) {
			L.add(new State(newStateIDTable.get(state.getID())));
		}

		return new AFA(S, tau, f, F, L);
	}

	private AFA computePzeroAFA(AFA tmpAFA1, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate) {

		AFA tmpAFA2 = computePnotAFA(copyAFA(tmpAFA1, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate));

		int s = getNewStateID();

		HashSet<State> S = new HashSet<>();
		TreeSet<Transition> transitions = new TreeSet<>();
		State f = new State(s);
		HashSet<State> F;
		HashSet<State> L = new HashSet<>();

		S.add(new State(s));
		for (State state : tmpAFA1.getS()) {
			S.add(new State(state.getID()));
		}
		for (State state : tmpAFA2.getS()) {
			S.add(new State(state.getID()));
		}

		transitions.add(new Transition(s, tmpAFA1.getf().getID(), AFA.epsilon, -1));
		transitions.add(new Transition(s, tmpAFA2.getf().getID(), AFA.epsilon, -1));
		for (State state : tmpAFA1.getF()) {
			transitions.add(new Transition(state.getID(), s, AFA.epsilon, -1));
		}
		for (Transition transition : tmpAFA1.getTau()) {
			transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
		}
		for (Transition transition : tmpAFA2.getTau()) {
			transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
		}

		F = tmpAFA2.getF();

		for (State state : tmpAFA1.getL()) {
			L.add(new State(state.getID()));
		}
		for (State state : tmpAFA2.getL()) {
			L.add(new State(state.getID()));
		}

		return new AFA(S, transitions, f, F, L);

	}

	// e* ... A <- e A / !e ''
	public class ZeroMore extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pzero : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			// ((e*)*)* => e* | (e*)* causes epsilon cycle, so eliminate inner *
			// (e?)* => e* | (e?)* causes epsilon cycle, so eliminate ?
			boolean update = true;
			while (update) {
				update = false;
				// while (e.get(0) instanceof nez.lang.expr.Pzero) {
				while (e.get(0) instanceof nez.lang.Nez.ZeroMore) {
					update = true;
					e = e.get(0);
				}
				// while (e.get(0) instanceof nez.lang.expr.Poption) {
				while (e.get(0) instanceof nez.lang.Nez.Option) {
					update = true;
					e = e.get(0);
				}
			}
			AFA tmpAFA1 = visit(e.get(0), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);

			AFA tmpAFA2 = computePnotAFA(copyAFA(tmpAFA1, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate));

			int s = getNewStateID();

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F;
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			for (State state : tmpAFA1.getS()) {
				S.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getS()) {
				S.add(new State(state.getID()));
			}

			transitions.add(new Transition(s, tmpAFA1.getf().getID(), AFA.epsilon, -1));
			transitions.add(new Transition(s, tmpAFA2.getf().getID(), AFA.epsilon, -1));
			for (State state : tmpAFA1.getF()) {
				transitions.add(new Transition(state.getID(), s, AFA.epsilon, -1));
			}
			for (Transition transition : tmpAFA1.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}
			for (Transition transition : tmpAFA2.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			F = tmpAFA2.getF();

			for (State state : tmpAFA1.getL()) {
				L.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getL()) {
				L.add(new State(state.getID()));
			}
			return new AFA(S, transitions, f, F, L);

			// return REzero(e);
		}
	}

	// e+ ... e e*
	public class OneMore extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pone : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}
			AFA tmpAFA1 = visit(e.get(0), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate); // e

			AFA tmpAFA2 = computePzeroAFA(copyAFA(tmpAFA1, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate,
					nonTerminalToVertexIDInPredicate); // e*

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = tmpAFA1.getf();
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			for (State state : tmpAFA1.getS()) {
				S.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getS()) {
				S.add(new State(state.getID()));
			}

			for (State state : tmpAFA1.getF()) {
				transitions.add(new Transition(state.getID(), tmpAFA2.getf().getID(), AFA.epsilon, -1));
			}
			for (Transition transition : tmpAFA1.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}
			for (Transition transition : tmpAFA2.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			for (State state : tmpAFA2.getF()) {
				F.add(new State(state.getID()));
			}

			for (State state : tmpAFA1.getL()) {
				L.add(new State(state.getID()));
			}
			for (State state : tmpAFA2.getL()) {
				L.add(new State(state.getID()));
			}

			return new AFA(S, transitions, f, F, L);
		}
	}

	public class And extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pand : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			AFA tmpAfa = visit(e.get(0), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, true);
			int s = getNewStateID();

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			for (State state : tmpAfa.getS()) {
				S.add(new State(state.getID()));
			}

			transitions.add(new Transition(s, tmpAfa.getf().getID(), AFA.epsilon, 0));
			for (Transition transition : tmpAfa.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			F.add(new State(s));

			for (State state : tmpAfa.getF()) {
				L.add(new State(state.getID()));
			}

			for (State state : tmpAfa.getL()) {
				L.add(new State(state.getID()));
			}

			// <--
			// for (State state : L) {
			// transitions.add(new Transition(state.getID(), state.getID(),
			// AFA.anyCharacter, -1));
			// }
			// -->

			return new AFA(S, transitions, f, F, L);
		}
	}

	private AFA computePnotAFA(AFA tmpAfa) {
		if (tmpAfa == null) {
			System.out.println("WARNING :: AFAConverter :: computePnotAFA :: argument tmpAfa is null");
			return null;
		}
		int s = getNewStateID();

		HashSet<State> S = new HashSet<>();
		TreeSet<Transition> transitions = new TreeSet<>();
		State f = new State(s);
		HashSet<State> F = new HashSet<>();
		HashSet<State> L = new HashSet<>();

		S.add(new State(s));
		for (State state : tmpAfa.getS()) {
			S.add(new State(state.getID()));
		}

		transitions.add(new Transition(s, tmpAfa.getf().getID(), AFA.epsilon, 1));
		for (Transition transition : tmpAfa.getTau()) {
			transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
		}

		F.add(new State(s));

		for (State state : tmpAfa.getF()) {
			L.add(new State(state.getID()));
		}

		for (State state : tmpAfa.getL()) {
			L.add(new State(state.getID()));
		}

		// <--
		// for (State state : L) {
		// transitions.add(new Transition(state.getID(), state.getID(),
		// AFA.anyCharacter, -1));
		// }
		// -->

		return new AFA(S, transitions, f, F, L);
	}

	public class Not extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pnot : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			// AFA tmpAfa = visitExpression(e.get(0));

			AFA tmpAfa = visit(e.get(0), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, true);
			int s = getNewStateID();

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			for (State state : tmpAfa.getS()) {
				S.add(new State(state.getID()));
			}

			transitions.add(new Transition(s, tmpAfa.getf().getID(), AFA.epsilon, 1));
			for (Transition transition : tmpAfa.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			F.add(new State(s));

			for (State state : tmpAfa.getF()) {
				L.add(new State(state.getID()));
			}

			for (State state : tmpAfa.getL()) {
				L.add(new State(state.getID()));
			}

			// <--
			// for (State state : L) {
			// transitions.add(new Transition(state.getID(), state.getID(),
			// AFA.anyCharacter, -1));
			// }
			// -->

			return new AFA(S, transitions, f, F, L);
		}
	}

	// public class Psequence extends DefaultVisitor {
	public class Pair extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Psequence : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			AFA tmpAfa1 = visit(((nez.lang.Nez.Pair) e).first, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);
			AFA tmpAfa2 = visit(((nez.lang.Nez.Pair) e).next, visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(tmpAfa1.getf().getID());
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			for (State state : tmpAfa1.getS()) {
				S.add(new State(state.getID()));
			}
			for (State state : tmpAfa2.getS()) {
				S.add(new State(state.getID()));
			}

			for (Transition transition : tmpAfa1.getTau()) {
				if (tmpAfa1.getF().contains(new State(transition.getDst()))) {
					transitions.add(new Transition(transition.getDst(), tmpAfa2.getf().getID(), AFA.epsilon, -1));
				}
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}
			for (Transition transition : tmpAfa2.getTau()) {
				transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
			}

			if (tmpAfa1.getF().contains(tmpAfa1.getf())) {
				transitions.add(new Transition(tmpAfa1.getf().getID(), tmpAfa2.getf().getID(), AFA.epsilon, -1));
			}

			for (State state : tmpAfa2.getF()) {
				F.add(new State(state.getID()));
			}

			for (State state : tmpAfa1.getL()) {
				L.add(new State(state.getID()));
			}

			for (State state : tmpAfa2.getL()) {
				L.add(new State(state.getID()));
			}

			return new AFA(S, transitions, f, F, L);
		}
	}

	// concatenate tmpAfa1 with tmpAfa2
	private AFA computeConcatenateAFAs(AFA tmpAfa1, AFA tmpAfa2) {
		if (tmpAfa1 == null && tmpAfa2 == null) {
			System.out.println("WARNING :: AFAConverter :: computeConcatenateAFAs :: tmpAfa1 and tmpAfa2 are null");
			return null;
		}
		if (tmpAfa1 == null) {
			return tmpAfa2;
		}
		if (tmpAfa2 == null) {
			return tmpAfa1;
		}

		HashSet<State> S = new HashSet<>();
		TreeSet<Transition> transitions = new TreeSet<>();
		State f = new State(tmpAfa1.getf().getID());
		HashSet<State> F = new HashSet<>();
		HashSet<State> L = new HashSet<>();

		for (State state : tmpAfa1.getS()) {
			S.add(new State(state.getID()));
		}

		for (State state : tmpAfa2.getS()) {
			S.add(new State(state.getID()));
		}

		for (State state : tmpAfa1.getF()) {
			transitions.add(new Transition(state.getID(), tmpAfa2.getf().getID(), AFA.epsilon, -1));
		}

		for (Transition transition : tmpAfa1.getTau()) {
			transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
		}

		for (Transition transition : tmpAfa2.getTau()) {
			transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
		}

		for (State state : tmpAfa2.getF()) {
			F.add(new State(state.getID()));
		}

		for (State state : tmpAfa1.getL()) {
			L.add(new State(state.getID()));
		}

		for (State state : tmpAfa2.getL()) {
			L.add(new State(state.getID()));
		}
		return new AFA(S, transitions, f, F, L);
	}

	public class Choice extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is Pchoice : " + e + " " + (inPredicate ? "(in predicate)" : ""));
			}

			int s = getNewStateID();
			int t = getNewStateID();

			HashSet<State> S = new HashSet<>();
			TreeSet<Transition> transitions = new TreeSet<>();
			State f = new State(s);
			HashSet<State> F = new HashSet<>();
			HashSet<State> L = new HashSet<>();

			S.add(new State(s));
			S.add(new State(t));

			F.add(new State(t));

			for (int i = 0; i < e.size(); i++) {
				AFA tmpAfa = visit(e.get(i), visitedNonTerminal, nonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);

				for (State state : tmpAfa.getS()) {
					S.add(new State(state.getID()));
				}

				transitions.add(new Transition(s, tmpAfa.getf().getID(), AFA.epsilon, -1));
				for (State state : tmpAfa.getF()) {
					transitions.add(new Transition(state.getID(), t, AFA.epsilon, -1));
				}
				for (Transition transition : tmpAfa.getTau()) {
					transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
				}

				for (State state : tmpAfa.getL()) {
					L.add(new State(state.getID()));
				}

			}

			return new AFA(S, transitions, f, F, L);

		}
	}

	public class NonTerminal extends DefaultVisitor {
		@Override
		public AFA accept(Expression e, HashSet<String> visitedNonTerminal, HashMap<String, Integer> nonTerminalToVertexID, HashSet<String> visitedNonTerminalInPredicate, HashMap<String, Integer> nonTerminalToVertexIDInPredicate, boolean inPredicate) {
			if (showTrace) {
				System.out.println("here is NonTerminal : " + ((nez.lang.NonTerminal) e).getLocalName() + " = " + ((nez.lang.NonTerminal) e).getProduction().getExpression() + " " + (inPredicate ? "(in predicate)" : ""));
			}
			String nonTerminalName = ((nez.lang.NonTerminal) e).getLocalName();
			boolean alreadyVisited = false;
			if (inPredicate && visitedNonTerminalInPredicate.contains(nonTerminalName)) {
				alreadyVisited = true;
			}
			if (!inPredicate && visitedNonTerminal.contains(nonTerminalName)) {
				alreadyVisited = true;
			}
			if (alreadyVisited) {
				System.out.println((inPredicate ? "(in predicate)" : "") + nonTerminalName + " again");
				int stateID = getNewStateID();
				HashSet<State> S = new HashSet<>();
				TreeSet<Transition> transitions = new TreeSet<>();
				State f = new State(stateID);
				HashSet<State> F = new HashSet<>();
				HashSet<State> L = new HashSet<>();

				S.add(new State(stateID));

				if (inPredicate) {
					if (nonTerminalToVertexIDInPredicate.containsValue(nonTerminalName)) {
						System.out.println("ERROR :: AFAConverter :: NonTerminal :: accept :: there are no " + nonTerminalName);
					}
					S.add(new State(nonTerminalToVertexIDInPredicate.get(nonTerminalName)));
					transitions.add(new Transition(stateID, nonTerminalToVertexIDInPredicate.get(nonTerminalName), AFA.epsilon, -1));
				} else {
					if (nonTerminalToVertexID.containsValue(nonTerminalName)) {
						System.out.println("ERROR :: AFAConverter :: NonTerminal :: accept :: there are no " + nonTerminalName);
					}
					S.add(new State(nonTerminalToVertexID.get(nonTerminalName)));
					transitions.add(new Transition(stateID, nonTerminalToVertexID.get(nonTerminalName), AFA.epsilon, -1));
				}
				return new AFA(S, transitions, f, F, L);

			} else {
				System.out.println((inPredicate ? "(in predicate)" : "") + nonTerminalName + " totally first");
				int stateID = getNewStateID();
				AFA tmpAfa;
				if (inPredicate) {

					HashSet<String> newVisitedNonTerminalInPredicate = new HashSet<>();
					newVisitedNonTerminalInPredicate.addAll(visitedNonTerminalInPredicate);
					newVisitedNonTerminalInPredicate.add(nonTerminalName);
					HashMap<String, Integer> newNonTerminalToVertexIDInPredicate = new HashMap<>();
					for (Map.Entry<String, Integer> itr : nonTerminalToVertexIDInPredicate.entrySet()) {
						newNonTerminalToVertexIDInPredicate.put(itr.getKey(), itr.getValue());
					}
					newNonTerminalToVertexIDInPredicate.put(nonTerminalName, stateID);
					tmpAfa = visitProduction(((nez.lang.NonTerminal) e).getProduction(), visitedNonTerminal, nonTerminalToVertexID, newVisitedNonTerminalInPredicate, newNonTerminalToVertexIDInPredicate, inPredicate);
				} else {
					HashSet<String> newVisitedNonTerminal = new HashSet<>();
					newVisitedNonTerminal.addAll(visitedNonTerminal);
					newVisitedNonTerminal.add(nonTerminalName);
					HashMap<String, Integer> newNonTerminalToVertexID = new HashMap<>();
					for (Map.Entry<String, Integer> itr : nonTerminalToVertexID.entrySet()) {
						newNonTerminalToVertexID.put(itr.getKey(), itr.getValue());
					}
					newNonTerminalToVertexID.put(nonTerminalName, stateID);
					tmpAfa = visitProduction(((nez.lang.NonTerminal) e).getProduction(), newVisitedNonTerminal, newNonTerminalToVertexID, visitedNonTerminalInPredicate, nonTerminalToVertexIDInPredicate, inPredicate);
				}
				if (tmpAfa == null) {
					System.out.println("ERROR :: AFAConverter :: NonTerminal :: accept :: tmpAfa is null");
				}
				HashSet<State> S = new HashSet<>();
				TreeSet<Transition> transitions = new TreeSet<>();
				State f = new State(stateID);
				HashSet<State> F = new HashSet<>();
				HashSet<State> L = new HashSet<>();

				S.add(new State(stateID));
				for (State state : tmpAfa.getS()) {
					S.add(new State(state.getID()));
				}

				transitions.add(new Transition(stateID, tmpAfa.getf().getID(), AFA.epsilon, -1));

				for (Transition transition : tmpAfa.getTau()) {
					transitions.add(new Transition(transition.getSrc(), transition.getDst(), transition.getLabel(), transition.getPredicate()));
				}

				for (State state : tmpAfa.getF()) {
					F.add(new State(state.getID()));
				}

				for (State state : tmpAfa.getL()) {
					L.add(new State(state.getID()));
				}

				return new AFA(S, transitions, f, F, L);
			}
		}
	}
}
