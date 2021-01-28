package nez.dfa;


public class BinaryOperatorMemoState implements Comparable<BinaryOperatorMemoState> {
	public char op;
	public int F, G;

	public BinaryOperatorMemoState(char op, int F, int G) {
		this.op = op;
		this.F = F;
		this.G = G;
	}

	@Override
	public int compareTo(BinaryOperatorMemoState o) {
		int result = Character.compare(op, o.op);
		if (result == 0) {
			result = Integer.compare(F, o.F);
			if (result == 0) {
				result = Integer.compare(G, o.G);
			}
		}
		return result;
	}
}
