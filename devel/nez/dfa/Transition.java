package nez.dfa;

public class Transition implements Comparable<Transition> {
	private State src;
	private State dst;
	private int label; // any character -> -2 empty -> ε(-1), otherwise ->
						// alphabet
	private int predicate; // and predicate : 0, not predicate : 1
	private boolean theOthers;

	public Transition() {

	}

	public Transition(int src, int dst, int label, int predicate) {
		this.src = new State(src);
		this.dst = new State(dst);
		this.label = label;
		this.predicate = predicate;
		if (label == AFA.theOthers) {
			this.theOthers = true;
		}
	}

	public Transition(State src, State dst, int label, int predicate) {
		this.src = new State(src.getID());
		this.dst = new State(dst.getID());
		this.label = label;
		this.predicate = predicate;
		if (label == AFA.theOthers) {
			this.theOthers = true;
		}
	}

	public Transition(int src, int dst, int label, int predicate, boolean theOthers) {
		this.src = new State(src);
		this.dst = new State(dst);
		this.label = label;
		this.predicate = predicate;
		this.theOthers = theOthers;
	}

	public Transition(State src, State dst, int label, int predicate, boolean theOthers) {
		this.src = new State(src.getID());
		this.dst = new State(dst.getID());
		this.label = label;
		this.predicate = predicate;
		this.theOthers = theOthers;
	}

	public void setSrc(int ID) {
		src.setID(ID);
	}

	public int getSrc() {
		return src.getID();
	}

	public void setDst(int ID) {
		dst.setID(ID);
	}

	public int getDst() {
		return dst.getID();
	}

	public void setLabel(int label) {
		this.label = label;
	}

	public int getLabel() {
		return label;
	}

	public void setPredicate(int predicate) {
		this.predicate = predicate;
	}

	public int getPredicate() {
		return predicate;
	}

	public boolean getTheOthers() {
		return theOthers;
	}

	@Override
	public String toString() {
		if (label == AFA.epsilon) {
			if (predicate != -1) {
				return "((" + src + ",[predicate]" + ((predicate == 0) ? "&" : "!") + ")," + dst + ")";
			} else {
				return "((" + src + ",ε)," + dst + ")";
			}
		} else {
			StringBuilder sb = new StringBuilder("((" + src + ",");
			if (label == AFA.anyCharacter) {
				sb.append("any");
			} else if (label == AFA.theOthers) {
				sb.append("other");
			} else {
				sb.append((char) label);
			}
			sb.append("),").append(dst).append(")");
			return sb.toString();
		}
	}

	@Override
	public int compareTo(Transition transition) {
		if (src.getID() != transition.getSrc()) {
			return Integer.compare(src.getID(), transition.getSrc());
		}
		if (dst.getID() != transition.getDst()) {
			return Integer.compare(dst.getID(), transition.getDst());
		}
		if (label != transition.getLabel()) {
			return Integer.compare(label, transition.getLabel());
		}
		return Integer.compare(predicate, transition.getPredicate());
	}
}