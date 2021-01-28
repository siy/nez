package nez.lang.schema;

public class PermutationGenerator {
	private final int number;
	private final int[] perm;
	private final boolean[] flag;
	private final int[][] perm_list;
	private int perm_list_index;

	public PermutationGenerator(int[] target) {
		this.number = target.length;
		int list_size = fact(number);
		this.perm = new int[number];
		this.flag = new boolean[number + 1];
		this.perm_list = new int[list_size][number];
		this.perm_list_index = 0;
		genPermutation(0, target);
	}

	public PermutationGenerator(int listLength) {
		this(initList(listLength));
	}

	public static int[] initList(int listLength) {
		int[] target = new int[listLength];
		for (int i = 0; i < target.length; i++) {
			target[i] = i;
		}
		return target;
	}

	public int[][] getPermList() {
		return perm_list;
	}

	private int fact(int n) {
		return n == 0 ? 1 : n * fact(n - 1);
	}

	private void printPerm() {
		for (int[] x : perm_list) {
			for (int i : x) {
				System.out.print(i + " ");
			}
			System.out.println();
		}
	}

	public void genPermutation(int n, int[] target) {
		if (n == number) {
			if (n >= 0) {
				System.arraycopy(perm, 0, perm_list[perm_list_index], 0, n);
			}
			perm_list_index++;
		} else {
			for (int i = 0; i < perm.length; i++) {
				if (flag[i])
					continue;
				perm[n] = target[i];
				flag[i] = true;
				genPermutation(n + 1, target);
				flag[i] = false;
			}
		}
	}

	// for DEBUG
	public static void main(String[] args) {
		int[] target = { 2, 4, 6, 8 };
		PermutationGenerator permutation = new PermutationGenerator(target);
		permutation.printPerm();
	}
}