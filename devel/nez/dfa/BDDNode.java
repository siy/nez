package nez.dfa;


public class BDDNode implements Comparable<BDDNode> {
	public int variableID, zeroID, oneID;

	public BDDNode(int variableID, int zeroID, int oneID) {
		this.variableID = variableID;
		this.zeroID = zeroID;
		this.oneID = oneID;
	}

	public BDDNode deepCopy() {
		return new BDDNode(variableID, zeroID, oneID);
	}

	public boolean equals(BDDNode o) {
		return variableID == o.variableID && zeroID == o.zeroID && oneID == o.oneID;
	}

	@Override
	public int compareTo(BDDNode o) {
		int result = Integer.compare(variableID, o.variableID);
		if (result == 0) {
			result = Integer.compare(zeroID, o.zeroID);
			if (result == 0) {
				result = Integer.compare(oneID, o.oneID);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return "(" + variableID + "," + zeroID + "," + oneID + ")";
	}
}
