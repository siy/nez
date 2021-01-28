package nez.dfa;

public class TauKey implements Comparable<TauKey> {
	private final State state;
	private final int sigma;

	public TauKey(State state, int sigma) {
		this.state = new State(state.getID());
		this.sigma = sigma;
	}

	public State getState() {
		return state;
	}

	public int getSigma() {
		return sigma;
	}

	@Override
	public int compareTo(TauKey o) {
		if (state.getID() != o.getState().getID()) {
			return Integer.compare(state.getID(), o.getState().getID());
		}
		return Integer.compare(sigma, o.getSigma());
	}
}
