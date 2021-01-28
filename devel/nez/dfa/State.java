package nez.dfa;

public class State {
	private int ID;

	public State() {

	}

	public State(int ID) {
		this.ID = ID;
	}

	public void setID(int ID) {
		this.ID = ID;
	}

	public int getID() {
		return ID;
	}

	@Override
	public String toString() {
		return String.valueOf(ID);
	}

	@Override
	public int hashCode() {
		return Integer.valueOf(ID).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof State) {
			State state = (State) obj;
			return getID() == state.getID();
		}
		return false;
	}

	public int compareTo(State state) {
		return Integer.compare(ID, state.getID());
	}
}
