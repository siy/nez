package nez.parser.vm;

import nez.ast.Symbol;

public class SymbolTable {
	private static final byte[] NullSymbol = {0, 0, 0, 0}; // to distinguish
	// others
	private SymbolTableEntry[] tables;
	private int tableSize;
	private int maxTableSize;

	private int stateValue;
	private int stateCount;

	static final class SymbolTableEntry {
		int stateValue;
		Symbol table;
		long code;
		byte[] symbol; // if uft8 is null, hidden

		@Override
		public String toString() {
			return new StringBuilder()
				.append('[')
				.append(stateValue)
				.append(", ")
				.append(table)
				.append(", ")
				.append((symbol == null)
						? "<masked>"
						: new String(symbol)).append("]").toString();
		}
	}

	static long hash(byte[] utf8) {
		long hashCode = 1;
		for (byte b : utf8) {
			hashCode = hashCode * 31 + (b & 0xff);
		}
		return hashCode;
	}

	public static boolean equalsBytes(byte[] utf8, byte[] b) {
		if (utf8.length == b.length) {
			for (int i = 0; i < utf8.length; i++) {
				if (utf8[i] != b[i]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private void initEntry(int s, int e) {
		for (int i = s; i < e; i++) {
			tables[i] = new SymbolTableEntry();
		}
	}

	private void push(Symbol table, long code, byte[] utf8) {
		if (!(tableSize < maxTableSize)) {
			if (maxTableSize == 0) {
				maxTableSize = 128;
				this.tables = new SymbolTableEntry[128];
				initEntry(0, maxTableSize);
			} else {
				maxTableSize *= 2;
				SymbolTableEntry[] newtable = new SymbolTableEntry[maxTableSize];
				System.arraycopy(tables, 0, newtable, 0, tables.length);
				this.tables = newtable;
				initEntry(tables.length / 2, maxTableSize);
			}
		}
		SymbolTableEntry entry = tables[tableSize];
		tableSize++;
		if (entry.table == table && equalsBytes(entry.symbol, utf8)) {
			// reuse state value
			entry.code = code;
			this.stateValue = entry.stateValue;
		} else {
			entry.table = table;
			entry.code = code;
			entry.symbol = utf8;
			this.stateCount += 1;
			this.stateValue = stateCount;
			entry.stateValue = stateCount;
		}
	}

	public final int saveSymbolPoint() {
		return tableSize;
	}

	public final void backSymbolPoint(int savePoint) {
		if (tableSize != savePoint) {
			this.tableSize = savePoint;
			if (tableSize == 0) {
				this.stateValue = 0;
			} else {
				this.stateValue = tables[savePoint - 1].stateValue;
			}
		}
	}

	public final int getState() {
		return stateValue;
	}

	public final void addSymbol(Symbol table, byte[] utf8) {
		push(table, hash(utf8), utf8);
	}

	public final void addSymbolMask(Symbol table) {
		push(table, 0, NullSymbol);
	}

	public final boolean exists(Symbol table) {
		for (int i = tableSize - 1; i >= 0; i--) {
			SymbolTableEntry entry = tables[i];
			if (entry.table == table) {
				return entry.symbol != NullSymbol;
			}
		}
		return false;
	}

	public final boolean exists(Symbol table, byte[] symbol) {
		long code = hash(symbol);
		for (int i = tableSize - 1; i >= 0; i--) {
			SymbolTableEntry entry = tables[i];
			if (entry.table == table) {
				if (entry.symbol == NullSymbol) {
					return false; // masked
				}
				if (entry.code == code && equalsBytes(entry.symbol, symbol)) {
					return true;
				}
			}
		}
		return false;
	}

	public final byte[] getSymbol(Symbol table) {
		for (int i = tableSize - 1; i >= 0; i--) {
			SymbolTableEntry entry = tables[i];
			if (entry.table == table) {
				return entry.symbol;
			}
		}
		return null;
	}

	public final boolean contains(Symbol table, byte[] symbol) {
		long code = hash(symbol);
		for (int i = tableSize - 1; i >= 0; i--) {
			SymbolTableEntry entry = tables[i];
			if (entry.table == table) {
				if (entry.symbol == NullSymbol) {
					return false; // masked
				}
				if (entry.code == code && equalsBytes(entry.symbol, symbol)) {
					return true;
				}
			}
		}
		return false;
	}
}
