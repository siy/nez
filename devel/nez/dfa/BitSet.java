package nez.dfa;

import java.util.ArrayList;

public class BitSet implements Comparable<BitSet> {
	private static final int LEN = 31;

	private final ArrayList<Integer> arr;
	private int size;

	public BitSet() {
		this(new ArrayList<>(), 0);
	}

	public BitSet(ArrayList<Integer> arr, int size) {
		this.arr = arr;
		this.size = size;
	}

	public int arrSize() {
		return arr.size();
	}

	public int arrGet(int i) {
		return arr.get(i);
	}

	public int size() {
		return size;
	}

	public boolean get(int i) {
		if (i >= size) {
			return false;
		}
		int arrID = i / LEN;
		int ID = i % LEN;
		return ((arr.get(arrID) >> ID) & 1) >= 1;
	}

	public void remove(int i) {
		int arrID = i / LEN;
		int ID = i % LEN;
		int v = arr.get(arrID);
		v = v & (~(1 << ID));
		arr.set(arrID, v);
	}

	public void add(int i) {
		if (size <= i) {
			while (arr.size() < i / LEN + 1) {
				arr.add(0);
			}
			size = i + 1;
		}
		int arrID = i / LEN;
		int ID = i % LEN;
		int tmp = arr.get(arrID);
		tmp |= (1 << ID);

		arr.set(arrID, tmp);
	}

	public ArrayList<Integer> toArrayList() {
		ArrayList<Integer> set = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			int arrID = i / LEN;
			int ID = i % LEN;
			if (((arr.get(arrID) >> ID) & 1) >= 1) {
				set.add(i);
			}
		}
		return set;
	}

	public BitSet copy() {
		return new BitSet(new ArrayList<>(arr), size);
	}

	@Override
	public int compareTo(BitSet o) {
		if (arr.size() != o.arrSize()) {
			return Integer.compare(arr.size(), o.arrSize());
		}
		for (int i = 0; i < arr.size(); i++) {
			if (arr.get(i) != o.arrGet(i)) {
				return arr.get(i).compareTo(o.arrGet(i));
			}
		}
		return 0;
	}

	@Override
	public String toString() {
		int top = -1;
		for (int i = 0; i < size; i++) {
			int arrID = i / LEN;
			int ID = i % LEN;
			if (((arr.get(arrID) >> ID) & 1) >= 1) {
				top = i;
			}
		}
		if (top == -1) {
			return "0";
		}

		StringBuilder v = new StringBuilder();
		for (int i = 0; i <= top; i++) {
			int arrID = i / LEN;
			int ID = i % LEN;
			if (((arr.get(arrID) >> ID) & 1) >= 1) {
				v.append("1");
			} else {
				v.append("0");
			}
		}
		return v.toString();
	}
}
