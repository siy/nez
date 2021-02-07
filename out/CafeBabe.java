
public class CafeBabe {
   private static <T> boolean start(ParserContext<T> c) {
      return pProgram(c);
   }
   
   /* Embedded ParserContext from nez.parser.ParserContext */
   
   public final static <T> T parse(String text, NewFunc<T> f, SetFunc<T> f2, int w, int n) {
   	T left = null;
   	ParserContext<T> c = new ParserContext<T>(text, f, f2, w, n);
   	if (start(c)) {
   		left = c.left;
   		if (left == null) {
   			left = c.f.newTree(0, c.inputs, 0, c.pos, 0);
   		}
   		return left;
   	}
   	return null;
   }
   
   public final static SimpleTree parse(String text, int w, int n) {
   	SimpleTree f = new SimpleTree();
   	return parse(text, f, f, w, n);
   }
   
   public final static int match(String text, int w, int n) {
   	NoneTree f = new NoneTree();
   	ParserContext<NoneTree> c = new ParserContext<NoneTree>(text, f, f, w, n);
   	if (start(c)) {
   		return c.pos;
   	}
   	return -1;
   }
   
   public static interface NewFunc<T> {
   	T newTree(int tag, byte[] inputs, int pos, int len, int size);
   }
   
   public static interface SetFunc<T> {
   	void setTree(T parent, int n, int label, T child);
   }
   
   private static final int[] EmptyLabels = new int[0];
   private static final SimpleTree[] EmptyTrees = new SimpleTree[0];
   
   public static class SimpleTree implements NewFunc<SimpleTree>, SetFunc<SimpleTree> {
   	public int tag;
   	public byte[] text;
   	public int start;
   	public int len;
   	public int[] labels;
   	public SimpleTree[] childs;
   	
   	@Override
   	public SimpleTree newTree(int tag, byte[] inputs, int pos, int len, int size) {
   		SimpleTree t = new SimpleTree();
   		t.tag = tag;
   		t.text = inputs;
   		t.start = pos;
   		t.len = len;
   		if(size == 0) {
   			t.labels = EmptyLabels;
   			t.childs = EmptyTrees;
   		}
   		else {
   			t.labels = new int[size];
   			t.childs = new SimpleTree[size];
   		}
   		return t;
   	}
   
   	@Override
   	public void setTree(SimpleTree parent, int n, int label, SimpleTree child) {
   		parent.labels[n] = label;
   		parent.childs[n] = child;
   	}
   	
   	@Override
      	public String toString() {
      		StringBuilder sb = new StringBuilder();
      		this.appendStringfied(sb);
      		return sb.toString();
      	}
   	
   	private void appendStringfied(StringBuilder sb) {
   		sb.append("[#");
   		sb.append(_tags[this.tag]);
   		if(this.childs.length == 0) {
   			sb.append(" '");
   			for(int i = 0; i < len; i++) {
   				char ch = (char)this.text[i + this.start];
   				sb.append(ch);
   			}
   			sb.append("'");
   		}
   		else {
   			for(int i = 0; i < labels.length; i++) {
   				if(labels[i] != 0) {
   					sb.append(" $");
   					sb.append(_labels[this.labels[i]]);
   					sb.append("=");
   				}
   				else {
   					sb.append(" ");
   				}
   				childs[i].appendStringfied(sb);
   			}
   		}
   		sb.append("]");
   	}
   }
   
   public static class NoneTree implements NewFunc<NoneTree>, SetFunc<NoneTree> {
   	@Override
   	public void setTree(NoneTree parent, int n, int label, NoneTree child) {
   	}
   
   	@Override
   	public NoneTree newTree(int tag, byte[] inputs, int pos, int len, int size) {
   		return null;
   	}
   
   }
   
   static final class ParserContext<T> {
   	public int pos = 0;
   	public T left;
   	NewFunc<T> f;
   	SetFunc<T> f2;
   	
   	public ParserContext(String s, NewFunc<T> f, SetFunc<T> f2, int w, int n) {
   		inputs = toUTF8(s + "\0");
   		length = inputs.length - 1;
   		this.pos = 0;
   		this.left = null;
   		this.f = f;
   		this.f2 = f2;
   		initMemo(w, n);
   	}
   
   	private byte[] inputs;
   	private int length;
   
   	public boolean eof() {
   		return !(pos < length);
   	}
   
   	public int read() {
   		return inputs[pos++] & 0xff;
   	}
   
   	public int prefetch() {
   		return inputs[pos] & 0xff;
   	}
   
   	public final void move(int shift) {
   		pos += shift;
   	}
   
   	public void back(int pos) {
   		this.pos = pos;
   	}
   
   	public boolean match(byte[] text) {
   		int len = text.length;
   		if (pos + len > this.length) {
   			return false;
   		}
   		for (int i = 0; i < len; i++) {
   			if (text[i] != this.inputs[pos + i]) {
   				return false;
   			}
   		}
   		pos += len;
   		return true;
   	}
   
   	// AST
   
   	private enum Operation {
   		Link, Tag, Replace, New;
   	}
   
   	static class TreeLog {
   		Operation op;
   		int iValue;
   		Object oValue;
   	}
   
   	private TreeLog[] logs = new TreeLog[0];
   	private int unused_log = 0;
   
   	private void log2(Operation op, int iValue, Object oValue) {
   		if (!(unused_log < logs.length)) {
   			TreeLog[] newlogs = new TreeLog[logs.length + 1024];
   			System.arraycopy(logs, 0, newlogs, 0, logs.length);
   			for (int i = logs.length; i < newlogs.length; i++) {
   				newlogs[i] = new TreeLog();
   			}
   			logs = newlogs;
   		}
   		TreeLog l = logs[unused_log];
   		l.op = op;
   		l.iValue = iValue;
   		l.oValue = oValue;
   		this.unused_log++;
   	}
   
   	public final void beginTree(int shift) {
   		log2(Operation.New, pos + shift, null);
   	}
   
   	public final void linkTree(int label) {
   		log2(Operation.Link, label, left);
   	}
   
   	public final void tagTree(int tag) {
   		log2(Operation.Tag, tag, null);
   	}
   
   	public final void valueTree(byte[] value) {
   		log2(Operation.Replace, 0, value);
   	}
   
   	public final void foldTree(int shift, int label) {
   		log2(Operation.New, pos + shift,  null);
   		log2(Operation.Link, label, left);
   	}
   
   	@SuppressWarnings("unchecked")
   	public final void endTree(int shift, int tag, byte[] value) {
   		int objectSize = 0;
   		TreeLog start = null;
   		int start_index = 0;
   		for (int i = unused_log - 1; i >= 0; i--) {
   			TreeLog l = logs[i];
   			if (l.op == Operation.Link) {
   				objectSize++;
   				continue;
   			}
   			if (l.op == Operation.New) {
   				start = l;
   				start_index = i;
   				break;
   			}
   			if (l.op == Operation.Tag && tag == 0) {
   				tag = l.iValue;
   			}
   			if (l.op == Operation.Replace && value == null) {
   				value = (byte[]) l.oValue;
   			}
   		}
   		if(value == null) {
   			left = f.newTree(tag, this.inputs, start.iValue, (pos + shift) - start.iValue, objectSize);
   		}
   		else {
   			left = f.newTree(tag, value, 0, value.length, objectSize);
   		}
   		if (objectSize > 0) {
   			int n = 0;
   			for (int j = start_index; j < unused_log; j++) {
   				TreeLog l = logs[j];
   				if (l.op == Operation.Link) {
   					f2.setTree(left, n++, l.iValue, (T)l.oValue);
   					l.oValue = null;
   				}
   			}
   		}
   		this.backLog(start_index);
   	}
   
   	public final int saveLog() {
   		return unused_log;
   	}
   
   	public final void backLog(int log) {
   		if (this.unused_log > log) {
   			this.unused_log = log;
   		}
   	}
   
   	public final T saveTree() {
   		return this.left;
   	}
   
   	public final void backTree(T tree) {
   		this.left = tree;
   	}
   
   	// int Table
   	// ---------------------------------------------------------
   
   	private final static byte[] NullSymbol = { 0, 0, 0, 0 }; // to
   																// distinguish
   	// others
   	private SymbolTableEntry[] tables = new SymbolTableEntry[0];
   	private int tableSize = 0;
   	private int stateValue = 0;
   	private int stateCount = 0;
   
   	static final class SymbolTableEntry {
   		int stateValue;
   		int table;
   		long code;
   		byte[] symbol; // if symbol is null, hidden
   	}
   
   	private final static long hash(byte[] utf8, int ppos, int pos) {
   		long hashCode = 1;
   		for (int i = ppos; i < pos; i++) {
   			hashCode = hashCode * 31 + (utf8[i] & 0xff);
   		}
   		return hashCode;
   	}
   
   	private final static boolean equalsBytes(byte[] utf8, byte[] b) {
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
   	
   	public final byte[] subByte(int startIndex, int endIndex) {
   		byte[] b = new byte[endIndex - startIndex];
   		System.arraycopy(this.inputs, (startIndex), b, 0, b.length);
   		return b;
   	}
   
   	private void push(int table, long code, byte[] utf8) {
   		if (!(tableSize < tables.length)) {
   			SymbolTableEntry[] newtable = new SymbolTableEntry[tables.length + 256];
   			System.arraycopy(this.tables, 0, newtable, 0, tables.length);
   			for (int i = tables.length; i < newtable.length; i++) {
   				newtable[i] = new SymbolTableEntry();
   			}
   			this.tables = newtable;
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
   		return this.tableSize;
   	}
   
   	public final void backSymbolPoint(int savePoint) {
   		if (this.tableSize != savePoint) {
   			this.tableSize = savePoint;
   			if (this.tableSize == 0) {
   				this.stateValue = 0;
   			} else {
   				this.stateValue = tables[savePoint - 1].stateValue;
   			}
   		}
   	}
   
   	public final void addSymbol(int table, int ppos) {
   		byte[] b = this.subByte(ppos, pos);
   		push(table, hash(b, 0, b.length), b);
   	}
   
   	public final void addSymbolMask(int table) {
   		push(table, 0, NullSymbol);
   	}
   
   	public final boolean exists(int table) {
   		for (int i = tableSize - 1; i >= 0; i--) {
   			SymbolTableEntry entry = tables[i];
   			if (entry.table == table) {
   				return entry.symbol != NullSymbol;
   			}
   		}
   		return false;
   	}
   
   	public final boolean existsSymbol(int table, byte[] symbol) {
   		long code = hash(symbol, 0, symbol.length);
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
   
   	public final boolean matchSymbol(int table) {
   		for (int i = tableSize - 1; i >= 0; i--) {
   			SymbolTableEntry entry = tables[i];
   			if (entry.table == table) {
   				if (entry.symbol == NullSymbol) {
   					return false; // masked
   				}
   				return this.match(entry.symbol);
   			}
   		}
   		return false;
   	}
   
   	private final long hashInputs(int ppos, int pos) {
   		long hashCode = 1;
   		for (int i = ppos; i < pos; i++) {
   			hashCode = hashCode * 31 + (this.inputs[ppos + i] & 0xff);
   		}
   		return hashCode;
   	}
   
   	private final boolean equalsInputs(int ppos, int pos, byte[] b2) {
   		if ((pos - ppos) == b2.length) {
   			for (int i = 0; i < b2.length; i++) {
   				if (this.inputs[ppos + i] != b2[i]) {
   					return false;
   				}
   			}
   			return true;
   		}
   		return false;
   	}
   
   	public final boolean equals(int table, int ppos) {
   		for (int i = tableSize - 1; i >= 0; i--) {
   			SymbolTableEntry entry = tables[i];
   			if (entry.table == table) {
   				if (entry.symbol == NullSymbol) {
   					return false; // masked
   				}
   				return equalsInputs(ppos, pos, entry.symbol);
   			}
   		}
   		return false;
   	}
   
   	public boolean contains(int table, int ppos) {
   		long code = hashInputs(ppos, pos);
   		for (int i = tableSize - 1; i >= 0; i--) {
   			SymbolTableEntry entry = tables[i];
   			if (entry.table == table) {
   				if (entry.symbol == NullSymbol) {
   					return false; // masked
   				}
   				if (code == entry.code && equalsInputs(ppos, pos, entry.symbol)) {
   					return true;
   				}
   			}
   		}
   		return false;
   	}
   
   	// Counter ------------------------------------------------------------
   
   	private int count = 0;
   
   	public final void scanCount(int ppos, long mask, int shift) {
   		if (mask == 0) {
   			StringBuilder sb = new StringBuilder();
   			for (int i = ppos; i < pos; i++) {
   				sb.append((char)inputs[i]);
   			}
   			count = (int) Long.parseLong(sb.toString());
   		} else {
   			StringBuilder sb = new StringBuilder();
   			for (int i = ppos; i < pos; i++) {
   				sb.append(Integer.toBinaryString(inputs[i] & 0xff));
   			}
   			long v = Long.parseUnsignedLong(sb.toString(), 2);
   			count = (int) ((v & mask) >> shift);
   		}
   		// Verbose.println("set count %d", count);
   	}
   
   	public final boolean decCount() {
   		return count-- > 0;
   	}
   
   	// Memotable
   	// ------------------------------------------------------------
   
   	public final static int NotFound = 0;
   	public final static int SuccFound = 1;
   	public final static int FailFound = 2;
   
   	private static class MemoEntry {
   		long key = -1;
   		public int consumed;
   		public Object memoTree;
   		public int result;
   		public int stateValue = 0;
   	}
   
   	private MemoEntry[] memoArray = null;
   	private int shift = 0;
   
   	public void initMemo(int w, int n) {
   		this.memoArray = new MemoEntry[w * n + 1];
   		for (int i = 0; i < this.memoArray.length; i++) {
   			this.memoArray[i] = new MemoEntry();
   			this.memoArray[i].key = -1;
   			this.memoArray[i].result = NotFound;
   		}
   		// this.initStat();
   	}
   
   	final long longkey(long pos, int memoPoint, int shift) {
   		return ((pos << 12) | memoPoint);
   	}
   
   	public final int memoLookup(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		if (m.key == key) {
   			this.pos += m.consumed;
   			return m.result;
   		}
   		return NotFound;
   	}
   
   	@SuppressWarnings("unchecked")
   	public final int memoLookupTree(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		if (m.key == key) {
   			this.pos += m.consumed;
   			this.left = (T)m.memoTree;
   			return m.result;
   		}
   		return NotFound;
   	}
   
   	public void memoSucc(int memoPoint, int ppos) {
   		long key = longkey(ppos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = pos - ppos;
   		m.result = SuccFound;
   		m.stateValue = -1;
   		// this.CountStored += 1;
   	}
   
   	public void memoTreeSucc(int memoPoint, int ppos) {
   		long key = longkey(ppos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = pos - ppos;
   		m.result = SuccFound;
   		m.stateValue = -1;
   		// this.CountStored += 1;
   	}
   
   	public void memoFail(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = 0;
   		m.result = FailFound;
   		m.stateValue = -1;
   	}
   
   	/* State Version */
   
   	public final int lookupStateMemo(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		if (m.key == key) {
   			this.pos += m.consumed;
   			return m.result;
   		}
   		return NotFound;
   	}
   
   	
   	@SuppressWarnings("unchecked")
   	public final int lookupStateTreeMemo(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		if (m.key == key && m.stateValue == this.stateValue) {
   			this.pos += m.consumed;
   			this.left = (T) m.memoTree;
   			return m.result;
   		}
   		return NotFound;
   	}
   
   	public void memoStateSucc(int memoPoint, int ppos) {
   		long key = longkey(ppos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = pos - ppos;
   		m.result = SuccFound;
   		m.stateValue = this.stateValue;
   		// this.CountStored += 1;
   	}
   
   	public void memoStateTreeSucc(int memoPoint, int ppos) {
   		long key = longkey(ppos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = pos - ppos;
   		m.result = SuccFound;
   		m.stateValue = this.stateValue;
   		// this.CountStored += 1;
   	}
   
   	public void memoStateFail(int memoPoint) {
   		long key = longkey(pos, memoPoint, shift);
   		int hash = (int) (key % memoArray.length);
   		MemoEntry m = this.memoArray[hash];
   		m.key = key;
   		m.memoTree = left;
   		m.consumed = 0;
   		m.result = FailFound;
   		m.stateValue = this.stateValue;
   	}
   }
   
   final static byte[] toUTF8(String text) {
   	try {
   		return text.getBytes("UTF8");
   	} catch (java.io.UnsupportedEncodingException e) {
   	}
   	return text.getBytes();
   }
   
   private final static int _T = 0;
   private final static int _L = 0;
   private final static int _S = 0;
   private final static boolean[] _set0 = {false,false,false,false,false,false,false,false,false,true,true,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static byte[] _text1 = {47,47};
   private final static byte[] _index2 = {1,0,0,0,0,0,0,0,0,0,2,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _index3 = {1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
   private final static byte[] _text4 = {117,115,101};
   private final static byte[] _text5 = {70,34};
   private final static boolean[] _set6 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set7 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,true,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TQualifiedName = 1;
   private final static boolean[] _set8 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TName = 2;
   private final static byte[] _text9 = {97,115};
   private final static int _TNameWithAlias = 3;
   private final static int _TImportList = 4;
   private final static int _TUse = 5;
   private final static byte[] _text10 = {112,117,98};
   private final static int _TPublic = 6;
   private final static byte[] _text11 = {99,111,110,115,116};
   private final static byte[] _index12 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text13 = {118,97,108};
   private final static int _TTypeNameList = 7;
   private final static byte[] _text14 = {46,46,46};
   private final static int _TOptionalEllipsis = 8;
   private final static int _TTypeArguments = 9;
   private final static int _TArrayDecl = 10;
   private final static byte[] _text15 = {91,93};
   private final static int _TTypeName = 11;
   private final static byte[] _index16 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,2,0,2,0,0,2,3,0,0,2,0,2,2,1,2,2,2,2,2,2,2,2,2,2,2,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,2,0,2,2,2,2,2,2,2,2,4,2,2,2,5,2,2,2,2,2,6,2,2,2,2,2,2,2,2,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text17 = {105,102};
   private final static int _TBooleanExpression = 12;
   private final static byte[] _index18 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,0,1,5,5,5,5,5,5,5,5,5,5,0,0,2,0,0,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,5,2,0,0,3,0,3,6,7,3,3,8,3,3,9,3,3,3,10,3,3,3,3,11,12,3,3,3,3,3,3,3,13,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text19 = {102,111,114};
   private final static byte[] _index20 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text21 = {118,97};
   private final static int _TVarTitle = 13;
   private final static int _TForSetup = 14;
   private final static int _TForStatement = 15;
   private final static byte[] _text22 = {115,101,108,101,99,116};
   private final static int _TNestedExpr = 16;
   private final static byte[] _text23 = {99,97,115,101};
   private final static byte[] _index24 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text25 = {48,120};
   private final static boolean[] _set26 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set27 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _THexNumLiteral = 17;
   private final static byte[] _text28 = {48,111};
   private final static boolean[] _set29 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set30 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TOctNumLiteral = 18;
   private final static byte[] _text31 = {48,98};
   private final static boolean[] _set32 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set33 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TBinNumLiteral = 19;
   private final static boolean[] _set34 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set35 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set36 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set37 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TFloatNumLiteral = 20;
   private final static int _TDecNumLiteral = 21;
   private final static byte[] _index38 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _index39 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text40 = {49,54};
   private final static byte[] _text41 = {51,50};
   private final static byte[] _text42 = {54,52};
   private final static int _TNumSuffix = 22;
   private final static byte[] _index43 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,3,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TNumLiteral = 23;
   private final static byte[] _index44 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _index45 = {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
   private final static boolean[] _set46 = {false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true};
   private final static byte[] _index47 = {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0};
   private final static int _TInterpolatedStringLiteral = 24;
   private final static int _TStringSegment = 25;
   private final static int _TStringLiteral = 26;
   private final static byte[] _index48 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,3,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _index49 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TNamedAssignment = 27;
   private final static int _TNamedAssignmentList = 28;
   private final static int _TNamedCallParameters = 29;
   private final static int _TExpressionList = 30;
   private final static int _TAnonymousCallParameters = 31;
   private final static int _TCallParameters = 32;
   private final static int _TMethodCall = 33;
   private final static int _TIndex = 34;
   private final static int _TInvoke = 35;
   private final static int _TValueReference = 36;
   private final static int _TObjConstructionBody = 37;
   private final static int _TObjConstruction = 38;
   private final static byte[] _text50 = {45,62};
   private final static int _TSelectCase = 39;
   private final static byte[] _text51 = {100,101,102,97,117,108,116};
   private final static int _TDefault = 40;
   private final static int _TSelectBody = 41;
   private final static int _TSelectExpression = 42;
   private final static byte[] _text52 = {109,97,116,99,104};
   private final static byte[] _index53 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TTypeConstructor = 43;
   private final static int _TMatchCase = 44;
   private final static int _TMatchBody = 45;
   private final static int _TMatchExpression = 46;
   private final static byte[] _text54 = {114,101,116,117,114,110};
   private final static int _TReturnStatement = 47;
   private final static byte[] _text55 = {98,114,101,97,107};
   private final static int _TBreakStatement = 48;
   private final static byte[] _text56 = {99,111,110,116,105,110,117,101};
   private final static int _TContinueStatement = 49;
   private final static byte[] _text57 = {43,61};
   private final static byte[] _text58 = {45,61};
   private final static byte[] _text59 = {94,61};
   private final static byte[] _text60 = {124,61};
   private final static byte[] _text61 = {38,61};
   private final static int _TRegularAssignment = 50;
   private final static int _TNameList = 51;
   private final static int _TRequiredNameList = 52;
   private final static int _TTupleConstruction = 53;
   private final static int _TParallelAssignment = 54;
   private final static int _TAssignmentExpr = 55;
   private final static int _TAssignment = 56;
   private final static int _TVariableDeclaration = 57;
   private final static byte[] _index62 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text63 = {46,46};
   private final static int _TOpenRange = 58;
   private final static int _TNamedRange = 59;
   private final static int _TComprPipeline = 60;
   private final static int _TComprehension = 61;
   private final static int _TIterable = 62;
   private final static int _TIterableStatement = 63;
   private final static byte[] _index64 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,0,1,5,5,5,5,5,5,5,5,5,5,0,0,6,0,0,0,3,3,3,3,3,3,7,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,8,2,0,0,3,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TArrayElements = 64;
   private final static int _TArrayConstruction = 65;
   private final static int _TSingleParamLambda = 66;
   private final static int _TMultiparamLambda = 67;
   private final static int _TLambda = 68;
   private final static byte[] _index65 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,3,0,0,0,0,0,0,0,1,4,4,4,4,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static boolean[] _set66 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static boolean[] _set67 = {false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,false,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true};
   private final static int _TCharLiteral = 69;
   private final static int _TLiteralValue = 70;
   private final static byte[] _index68 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text69 = {43,43};
   private final static byte[] _text70 = {45,45};
   private final static int _TValue = 71;
   private final static int _TValueStatement = 72;
   private final static int _TStatement = 73;
   private final static byte[] _text71 = {101,108,115,101};
   private final static int _TIfExpression = 74;
   private final static int _TTernaryExpression = 75;
   private final static byte[] _index72 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,0,0,0,0,0,0,3,0,0,2,0,2,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TCast = 76;
   private final static byte[] _index73 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,2,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _index74 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,3,0,0,2,4,0,0,0,0,0,5,1,2,2,2,2,2,2,2,2,2,2,6,0,2,0,0,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,2,0,0,3,0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text75 = {58,58};
   private final static int _TMethodReference = 77;
   private final static int _TProduct = 78;
   private final static boolean[] _set76 = {false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true,false,false,false,false,true,false,false,false,false,true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false};
   private final static int _TSum = 79;
   private final static int _TShift = 80;
   private final static byte[] _index77 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text78 = {60,60};
   private final static byte[] _text79 = {62,62};
   private final static int _TCompare = 81;
   private final static byte[] _index80 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text81 = {60,61};
   private final static byte[] _text82 = {62,61};
   private final static int _TComparison = 82;
   private final static byte[] _text83 = {105,115,65};
   private final static int _TTypeCheck = 83;
   private final static int _TEquality = 84;
   private final static byte[] _index84 = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text85 = {33,61};
   private final static byte[] _text86 = {61,61};
   private final static int _TBitwiseAnd = 85;
   private final static int _TBitwiseXor = 86;
   private final static int _TBitwiseOr = 87;
   private final static int _TAnd = 88;
   private final static byte[] _text87 = {38,38};
   private final static int _TOr = 89;
   private final static byte[] _text88 = {124,124};
   private final static int _TExpr = 90;
   private final static int _TExpression = 91;
   private final static int _TConstant = 92;
   private final static int _TAnnotationRef = 93;
   private final static byte[] _text89 = {116,121,112,101};
   private final static byte[] _index90 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,3,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,4,4,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,5,2,6,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static byte[] _text91 = {97,112,105};
   private final static byte[] _index92 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,3,2,2,2,2,2,2,2,2,2,2,2,4,2,2,4,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TLimitedTypeName = 94;
   private final static int _TTypeVarsDecl = 95;
   private final static byte[] _index93 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TArgumentType = 96;
   private final static int _TTypedArgument = 97;
   private final static int _TTypedArgList = 98;
   private final static int _TTypedArgDecl = 99;
   private final static int _TTypedFunction = 100;
   private final static byte[] _text94 = {102,110};
   private final static int _TOptionalNameList = 101;
   private final static int _TUntypedFunction = 102;
   private final static int _TMethodSignature = 103;
   private final static byte[] _text95 = {115,116,97,116,105,99};
   private final static byte[] _index96 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TMethodExpression = 104;
   private final static int _TMethodBody = 105;
   private final static int _TStaticMethodImpl = 106;
   private final static int _TDefaultMethodImpl = 107;
   private final static byte[] _text97 = {99,108,97,115,115};
   private final static int _TApiRefs = 108;
   private final static int _TConstructor = 109;
   private final static int _TDestructor = 110;
   private final static byte[] _index98 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,0,0,0,0,2,0,2,2,2,2,2,2,2,2,3,2,2,2,2,2,2,4,2,2,4,2,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TFunction = 111;
   private final static byte[] _text99 = {105,109,112,108};
   private final static int _TMethodImpl = 112;
   private final static int _TClassBlock = 113;
   private final static int _TClassBody = 114;
   private final static int _TNamedClassDecl = 115;
   private final static int _TCommonApiBody = 116;
   private final static byte[] _text100 = {97,110,110,111,116,97,116,105,111,110};
   private final static int _TAnnotationBody = 117;
   private final static int _TAnnotationDecl = 118;
   private final static int _TTypeAliasDecl = 119;
   private final static byte[] _index101 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TAndType = 120;
   private final static int _TOrType = 121;
   private final static byte[] _index102 = {0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,2,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
   private final static int _TCommonApiDecl = 122;
   private final static int _TCompoundTypeDecl = 123;
   private final static int _TTupleDecl = 124;
   private final static byte[] _text103 = {118,97,114};
   private final static int _TMutable = 125;
   private final static int _TClassField = 126;
   private final static int _TClassFields = 127;
   private final static int _TClassDataDecl = 128;
   private final static int _TClassDecl = 129;
   private final static int _TType = 130;
   private final static byte[] _text104 = {101,120,116,101,110,100};
   private final static int _TExtension = 131;
   private final static int _TCommons = 132;
   private final static byte[] _text105 = {117,110,115,97,102,101};
   private final static byte[] _text106 = {116,101,115,116};
   private final static int _TTest = 133;
   private final static int _TUnsafe = 134;
   private final static int _TProgram = 135;
   private final static String[] _tags = {"","QualifiedName","Name","NameWithAlias","ImportList","Use","Public","TypeNameList","OptionalEllipsis","TypeArguments","ArrayDecl","TypeName","BooleanExpression","VarTitle","ForSetup","ForStatement","NestedExpr","HexNumLiteral","OctNumLiteral","BinNumLiteral","FloatNumLiteral","DecNumLiteral","NumSuffix","NumLiteral","InterpolatedStringLiteral","StringSegment","StringLiteral","NamedAssignment","NamedAssignmentList","NamedCallParameters","ExpressionList","AnonymousCallParameters","CallParameters","MethodCall","Index","Invoke","ValueReference","ObjConstructionBody","ObjConstruction","SelectCase","Default","SelectBody","SelectExpression","TypeConstructor","MatchCase","MatchBody","MatchExpression","ReturnStatement","BreakStatement","ContinueStatement","RegularAssignment","NameList","RequiredNameList","TupleConstruction","ParallelAssignment","AssignmentExpr","Assignment","VariableDeclaration","OpenRange","NamedRange","ComprPipeline","Comprehension","Iterable","IterableStatement","ArrayElements","ArrayConstruction","SingleParamLambda","MultiparamLambda","Lambda","CharLiteral","LiteralValue","Value","ValueStatement","Statement","IfExpression","TernaryExpression","Cast","MethodReference","Product","Sum","Shift","Compare","Comparison","TypeCheck","Equality","BitwiseAnd","BitwiseXor","BitwiseOr","And","Or","Expr","Expression","Constant","AnnotationRef","LimitedTypeName","TypeVarsDecl","ArgumentType","TypedArgument","TypedArgList","TypedArgDecl","TypedFunction","OptionalNameList","UntypedFunction","MethodSignature","MethodExpression","MethodBody","StaticMethodImpl","DefaultMethodImpl","ApiRefs","Constructor","Destructor","Function","MethodImpl","ClassBlock","ClassBody","NamedClassDecl","CommonApiBody","AnnotationBody","AnnotationDecl","TypeAliasDecl","AndType","OrType","CommonApiDecl","CompoundTypeDecl","TupleDecl","Mutable","ClassField","ClassFields","ClassDataDecl","ClassDecl","Type","Extension","Commons","Test","Unsafe","Program"};
   private final static String[] _labels = {""};
   private final static String[] _tables = {""};
   // "+="
   private static <T> boolean e113(ParserContext<T> c) {
      if (!c.match(_text57)) {
         return false;
      }
      return true;
   }
   // "-="
   private static <T> boolean e114(ParserContext<T> c) {
      if (!c.match(_text58)) {
         return false;
      }
      return true;
   }
   // ''
   private static <T> boolean e6(ParserContext<T> c) {
      return true;
   }
   // '\n'
   private static <T> boolean e4(ParserContext<T> c) {
      if (c.read() != 10) {
         return false;
      }
      return true;
   }
   // '\n' / ''
   private static <T> boolean e7(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         // '\n'
         if (e4(c)) {
            temp = false;
         } else {
            c.pos = pos;
         }
      }
      if (temp) {
         int pos2 = c.pos;
         // ''
         if (e6(c)) {
            temp = false;
         } else {
            c.pos = pos2;
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // '\r' ('' / ('\n' / ''))
   private static <T> boolean e5(ParserContext<T> c) {
      if (c.read() != 13) {
         return false;
      }
      boolean temp = true;
      switch(_index3[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // ''
         temp = e6(c);
         break;
         case 2: 
         // '\n' / ''
         temp = e7(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // !.
   private static <T> boolean e3(ParserContext<T> c) {
      if (!c.eof()) {
         return false;
      }
      return true;
   }
   // !. / '\n' / '\r' ('' / ('\n' / ''))
   private static <T> boolean p_End(ParserContext<T> c) {
      boolean temp = true;
      switch(_index2[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // !.
         temp = e3(c);
         break;
         case 2: 
         // '\n'
         temp = e4(c);
         break;
         case 3: 
         // '\r' ('' / ('\n' / ''))
         temp = e5(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // !~End .
   private static <T> boolean e2(ParserContext<T> c) {
      {
         int pos = c.pos;
         // ~End
         if (p_End(c)) {
            return false;
         }
         c.pos = pos;
      }
      if (c.read() == 0) {
         return false;
      }
      return true;
   }
   // "//" (!~End .)* &~End ~__?
   private static <T> boolean p_Comment(ParserContext<T> c) {
      if (!c.match(_text1)) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         // !~End .
         if (!e2(c)) {
            c.pos = pos;
            break;
         }
      }
      {
         int pos1 = c.pos;
         // ~End
         if (!p_End(c)) {
            return false;
         }
         c.pos = pos1;
      }
      int pos2 = c.pos;
      // ~__
      if (!p___(c)) {
         c.pos = pos2;
      }
      return true;
   }
   // [\t-\n\r ]* ~Comment?
   private static <T> boolean e1(ParserContext<T> c) {
      while (_set0[c.prefetch()]) {
         c.move(1);
      }
      int pos = c.pos;
      // ~Comment
      if (!p_Comment(c)) {
         c.pos = pos;
      }
      return true;
   }
   // [\t-\n\r ]* ~Comment?
   private static <T> boolean p___(ParserContext<T> c) {
      int memo = c.memoLookup(0);
      if (memo == 0) {
         int pos = c.pos;
         if (e1(c)) {
            c.memoSucc(0,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(0);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ !"F\"" [$@-Z_a-z] { [$0-9A-Z_a-z]* #Name } ~__
   private static <T> boolean e11(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.match(_text5)) {
         return false;
      }
      if (!_set6[c.read()]) {
         return false;
      }
      c.beginTree(-1);
      while (_set8[c.prefetch()]) {
         c.move(1);
      }
      c.endTree(0,_TName,null);
      if (!p___(c)) {
         return false;
      }
      return true;
   }
   // ~__ !"F\"" [$@-Z_a-z] { [$0-9A-Z_a-z]* #Name } ~__
   private static <T> boolean pName(ParserContext<T> c) {
      int memo = c.memoLookupTree(2);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e11(c)) {
            c.memoTreeSucc(2,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(2);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ ']'
   private static <T> boolean p_RSqB(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 93) {
         return false;
      }
      return true;
   }
   // ~__ '['
   private static <T> boolean p_LSqB(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 91) {
         return false;
      }
      return true;
   }
   // $((~LSqB { `[]` #ArrayDecl } ~RSqB))
   private static <T> boolean e32(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p_LSqB(c)) {
         return false;
      }
      c.beginTree(0);
      c.endTree(0,_TArrayDecl,_text15);
      if (!p_RSqB(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ '&'
   private static <T> boolean p_BitAnd(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 38) {
         return false;
      }
      return true;
   }
   // { $(Name) ($(TypeArguments))? ~BitAnd? ($((~LSqB { `[]` #ArrayDecl } ~RSqB)))* #TypeName }
   private static <T> boolean e26(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(TypeArguments)
      if (!e27(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      int pos4 = c.pos;
      // ~BitAnd
      if (!p_BitAnd(c)) {
         c.pos = pos4;
      }
      while (true) {
         int pos5 = c.pos;
         T left6 = c.saveTree();
         int log7 = c.saveLog();
         // $((~LSqB { `[]` #ArrayDecl } ~RSqB))
         if (!e32(c)) {
            c.pos = pos5;
            c.backTree(left6);
            c.backLog(log7);
            break;
         }
      }
      c.endTree(0,_TTypeName,null);
      return true;
   }
   // { $(Name) ($(TypeArguments))? ~BitAnd? ($((~LSqB { `[]` #ArrayDecl } ~RSqB)))* #TypeName }
   private static <T> boolean pTypeName(ParserContext<T> c) {
      int memo = c.memoLookupTree(1);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e26(c)) {
            c.memoTreeSucc(1,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(1);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ ','
   private static <T> boolean e14(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 44) {
         return false;
      }
      return true;
   }
   // ~__ ','
   private static <T> boolean p_Comma(ParserContext<T> c) {
      int memo = c.memoLookup(18);
      if (memo == 0) {
         int pos = c.pos;
         if (e14(c)) {
            c.memoSucc(18,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(18);
            return false;
         }
      }
      return memo == 1;
   }
   // ~Comma $(TypeName)
   private static <T> boolean e29(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(TypeName) (~Comma $(TypeName))* #TypeNameList }
   private static <T> boolean pTypeNameList(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(TypeName)
         if (!e29(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TTypeNameList,null);
      return true;
   }
   // ~__ "..."
   private static <T> boolean p_Ellipsis(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text14)) {
         return false;
      }
      return true;
   }
   // { ~Comma ~Ellipsis #OptionalEllipsis }
   private static <T> boolean e31(ParserContext<T> c) {
      c.beginTree(0);
      if (!p_Comma(c)) {
         return false;
      }
      if (!p_Ellipsis(c)) {
         return false;
      }
      c.endTree(0,_TOptionalEllipsis,null);
      return true;
   }
   // { ~Comma ~Ellipsis #OptionalEllipsis }
   private static <T> boolean pOptionalEllipsis(ParserContext<T> c) {
      int memo = c.memoLookupTree(14);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e31(c)) {
            c.memoTreeSucc(14,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(14);
            return false;
         }
      }
      return memo == 1;
   }
   // $(OptionalEllipsis)
   private static <T> boolean e30(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pOptionalEllipsis(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ '<' { $(TypeNameList) ($(OptionalEllipsis))? #TypeArguments } ~__ '>'
   private static <T> boolean e28(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 60) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pTypeNameList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TTypeArguments,null);
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 62) {
         return false;
      }
      return true;
   }
   // ~__ '<' { $(TypeNameList) ($(OptionalEllipsis))? #TypeArguments } ~__ '>'
   private static <T> boolean pTypeArguments(ParserContext<T> c) {
      int memo = c.memoLookupTree(29);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e28(c)) {
            c.memoTreeSucc(29,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(29);
            return false;
         }
      }
      return memo == 1;
   }
   // $(TypeArguments)
   private static <T> boolean e27(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTypeArguments(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(TypeName)
   private static <T> boolean e25(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTypeName(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(TypeArguments) #TypeConstructor
   private static <T> boolean e104(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pTypeArguments(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TTypeConstructor);
      return true;
   }
   // $(TypeName) / $(TypeArguments) #TypeConstructor
   private static <T> boolean e103(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(TypeName)
         if (e25(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(TypeArguments) #TypeConstructor
         if (e104(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~__ ';' ~__
   private static <T> boolean e16(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 59) {
         return false;
      }
      if (!p___(c)) {
         return false;
      }
      return true;
   }
   // ~__ ';' ~__
   private static <T> boolean p_Semicolon(ParserContext<T> c) {
      int memo = c.memoLookup(12);
      if (memo == 0) {
         int pos = c.pos;
         if (e16(c)) {
            c.memoSucc(12,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(12);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ "->"
   private static <T> boolean p_Arrow(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text50)) {
         return false;
      }
      return true;
   }
   // { (($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor) }
   private static <T> boolean e102(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index53[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(TypeName) / $(TypeArguments) #TypeConstructor
         temp = e103(c);
         break;
         case 2: 
         // $(TypeName)
         temp = e25(c);
         break;
         case 3: 
         // $(TypeArguments) #TypeConstructor
         temp = e104(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($(TypeName) / $(TypeArguments) #TypeConstructor) / $(TypeName) / $(TypeArguments) #TypeConstructor) }
   private static <T> boolean pTypeConstructor(ParserContext<T> c) {
      int memo = c.memoLookupTree(45);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e102(c)) {
            c.memoTreeSucc(45,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(45);
            return false;
         }
      }
      return memo == 1;
   }
   // { $(TypeConstructor) ~Arrow $(Expression) #MatchCase } ~Semicolon
   private static <T> boolean pMatchCase(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pTypeConstructor(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p_Arrow(c)) {
         return false;
      }
      {
         T left1 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TMatchCase,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $(MatchCase)
   private static <T> boolean e101(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pMatchCase(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "default" ~Arrow { $(Expression) #Default } ~Semicolon
   private static <T> boolean pDefault(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text51)) {
         return false;
      }
      if (!p_Arrow(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TDefault,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $(Default)
   private static <T> boolean e98(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pDefault(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ '{'
   private static <T> boolean e10(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 123) {
         return false;
      }
      return true;
   }
   // ~__ '{'
   private static <T> boolean p_BlockStart(ParserContext<T> c) {
      int memo = c.memoLookup(38);
      if (memo == 0) {
         int pos = c.pos;
         if (e10(c)) {
            c.memoSucc(38,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(38);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ '}' ~__
   private static <T> boolean e15(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 125) {
         return false;
      }
      if (!p___(c)) {
         return false;
      }
      return true;
   }
   // ~__ '}' ~__
   private static <T> boolean p_BlockEnd(ParserContext<T> c) {
      int memo = c.memoLookup(39);
      if (memo == 0) {
         int pos = c.pos;
         if (e15(c)) {
            c.memoSucc(39,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(39);
            return false;
         }
      }
      return memo == 1;
   }
   // ~BlockStart { ($(MatchCase))* ($(Default))? #MatchBody } ~BlockEnd
   private static <T> boolean pMatchBody(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MatchCase)
         if (!e101(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(Default)
      if (!e98(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      c.endTree(0,_TMatchBody,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // ~__ '('
   private static <T> boolean e37(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 40) {
         return false;
      }
      return true;
   }
   // ~__ '('
   private static <T> boolean p_LP(ParserContext<T> c) {
      int memo = c.memoLookup(21);
      if (memo == 0) {
         int pos = c.pos;
         if (e37(c)) {
            c.memoSucc(21,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(21);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ ')'
   private static <T> boolean e38(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 41) {
         return false;
      }
      return true;
   }
   // ~__ ')'
   private static <T> boolean p_RP(ParserContext<T> c) {
      int memo = c.memoLookup(22);
      if (memo == 0) {
         int pos = c.pos;
         if (e38(c)) {
            c.memoSucc(22,pos);
            return true;
         } else {
            c.pos = pos;
            c.memoFail(22);
            return false;
         }
      }
      return memo == 1;
   }
   // ~LP { $(Expression) #NestedExpr } ~RP
   private static <T> boolean e50(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TNestedExpr,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~LP { $(Expression) #NestedExpr } ~RP
   private static <T> boolean pNestedExpr(ParserContext<T> c) {
      int memo = c.memoLookupTree(23);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e50(c)) {
            c.memoTreeSucc(23,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(23);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ "match" { $(NestedExpr) $(MatchBody) #MatchExpression }
   private static <T> boolean e100(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text52)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNestedExpr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pMatchBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TMatchExpression,null);
      return true;
   }
   // ~__ "match" { $(NestedExpr) $(MatchBody) #MatchExpression }
   private static <T> boolean pMatchExpression(ParserContext<T> c) {
      int memo = c.memoLookupTree(33);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e100(c)) {
            c.memoTreeSucc(33,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(33);
            return false;
         }
      }
      return memo == 1;
   }
   // $(MatchExpression)
   private static <T> boolean e99(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pMatchExpression(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // "16"
   private static <T> boolean e64(ParserContext<T> c) {
      if (!c.match(_text40)) {
         return false;
      }
      return true;
   }
   // "64" #NumSuffix
   private static <T> boolean e66(ParserContext<T> c) {
      if (!c.match(_text42)) {
         return false;
      }
      c.tagTree(_TNumSuffix);
      return true;
   }
   // "32"
   private static <T> boolean e65(ParserContext<T> c) {
      if (!c.match(_text41)) {
         return false;
      }
      return true;
   }
   // 'f' ("16" / "32" / "64" #NumSuffix)
   private static <T> boolean e63(ParserContext<T> c) {
      if (c.read() != 102) {
         return false;
      }
      boolean temp = true;
      switch(_index39[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "16"
         temp = e64(c);
         break;
         case 2: 
         // "32"
         temp = e65(c);
         break;
         case 3: 
         // "64" #NumSuffix
         temp = e66(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // "64"
   private static <T> boolean e68(ParserContext<T> c) {
      if (!c.match(_text42)) {
         return false;
      }
      return true;
   }
   // '8'
   private static <T> boolean e69(ParserContext<T> c) {
      if (c.read() != 56) {
         return false;
      }
      return true;
   }
   // 'i' ("16" / "32" / "64" / '8')
   private static <T> boolean e67(ParserContext<T> c) {
      if (c.read() != 105) {
         return false;
      }
      boolean temp = true;
      switch(_index43[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "16"
         temp = e64(c);
         break;
         case 2: 
         // "32"
         temp = e65(c);
         break;
         case 3: 
         // "64"
         temp = e68(c);
         break;
         case 4: 
         // '8'
         temp = e69(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // 'u' ("16" / "32" / "64" / '8')
   private static <T> boolean e70(ParserContext<T> c) {
      if (c.read() != 117) {
         return false;
      }
      boolean temp = true;
      switch(_index43[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "16"
         temp = e64(c);
         break;
         case 2: 
         // "32"
         temp = e65(c);
         break;
         case 3: 
         // "64"
         temp = e68(c);
         break;
         case 4: 
         // '8'
         temp = e69(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) }))
   private static <T> boolean e62(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      boolean temp = true;
      switch(_index38[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // 'f' ("16" / "32" / "64" #NumSuffix)
         temp = e63(c);
         break;
         case 2: 
         // 'i' ("16" / "32" / "64" / '8')
         temp = e67(c);
         break;
         case 3: 
         // 'u' ("16" / "32" / "64" / '8')
         temp = e70(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // [0-9] [0-9_]*
   private static <T> boolean p_DecSegment(ParserContext<T> c) {
      if (!_set34[c.read()]) {
         return false;
      }
      while (_set35[c.prefetch()]) {
         c.move(1);
      }
      return true;
   }
   // [Ee] [+\-]? ~DecSegment
   private static <T> boolean e59(ParserContext<T> c) {
      if (!_set36[c.read()]) {
         return false;
      }
      if (_set37[c.prefetch()]) {
         c.move(1);
      }
      if (!p_DecSegment(c)) {
         return false;
      }
      return true;
   }
   // $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__))
   private static <T> boolean e58(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      c.beginTree(0);
      if (!p_DecSegment(c)) {
         return false;
      }
      if (c.read() != 46) {
         return false;
      }
      if (!p_DecSegment(c)) {
         return false;
      }
      int pos = c.pos;
      // [Ee] [+\-]? ~DecSegment
      if (!e59(c)) {
         c.pos = pos;
      }
      c.endTree(0,_TFloatNumLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ { ~DecSegment #DecNumLiteral } ~__))
   private static <T> boolean e60(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      c.beginTree(0);
      if (!p_DecSegment(c)) {
         return false;
      }
      c.endTree(0,_TDecNumLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))
   private static <T> boolean e61(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__))
         if (e58(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ { ~DecSegment #DecNumLiteral } ~__))
         if (e60(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__))
   private static <T> boolean e55(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text25)) {
         return false;
      }
      if (!_set26[c.read()]) {
         return false;
      }
      c.beginTree(-3);
      while (_set27[c.prefetch()]) {
         c.move(1);
      }
      c.endTree(0,_THexNumLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__))
   private static <T> boolean e57(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text31)) {
         return false;
      }
      if (!_set32[c.read()]) {
         return false;
      }
      c.beginTree(-3);
      while (_set33[c.prefetch()]) {
         c.move(1);
      }
      c.endTree(0,_TBinNumLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__))
   private static <T> boolean e56(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text28)) {
         return false;
      }
      if (!_set29[c.read()]) {
         return false;
      }
      c.beginTree(-3);
      while (_set30[c.prefetch()]) {
         c.move(1);
      }
      c.endTree(0,_TOctNumLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__)) / $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__)) / $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__)) / $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))
   private static <T> boolean e54(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__))
         if (e55(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__))
         if (e56(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__))
         if (e57(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__))
         if (e58(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         int pos13 = c.pos;
         T left14 = c.saveTree();
         int log15 = c.saveLog();
         // $((~__ { ~DecSegment #DecNumLiteral } ~__))
         if (e60(c)) {
            temp = false;
         } else {
            c.pos = pos13;
            c.backTree(left14);
            c.backLog(log15);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__)) / $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__)) / $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__)) / $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))) / ($((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__)))) ($(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) })))? #NumLiteral }
   private static <T> boolean e53(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index24[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__)) / $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__)) / $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__)) / $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))
         temp = e54(c);
         break;
         case 2: 
         // $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))
         temp = e61(c);
         break;
      }
      if (!temp) {
         return false;
      }
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) }))
      if (!e62(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TNumLiteral,null);
      return true;
   }
   // { (($((~__ "0x" [0-9A-Fa-f] { [0-9A-F_a-f]* #HexNumLiteral } ~__)) / $((~__ "0o" [0-7] { [0-7_]* #OctNumLiteral } ~__)) / $((~__ "0b" [0-1] { [0-1_]* #BinNumLiteral } ~__)) / $((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__))) / ($((~__ { ~DecSegment '.' ~DecSegment ([Ee] [+\-]? ~DecSegment)? #FloatNumLiteral } ~__)) / $((~__ { ~DecSegment #DecNumLiteral } ~__)))) ($(({ ('f' ("16" / "32" / "64" #NumSuffix) / 'i' ("16" / "32" / "64" / '8') / 'u' ("16" / "32" / "64" / '8')) })))? #NumLiteral }
   private static <T> boolean pNumLiteral(ParserContext<T> c) {
      int memo = c.memoLookupTree(7);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e53(c)) {
            c.memoTreeSucc(7,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(7);
            return false;
         }
      }
      return memo == 1;
   }
   // $(NumLiteral)
   private static <T> boolean e52(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pNumLiteral(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // '"'
   private static <T> boolean e79(ParserContext<T> c) {
      if (c.read() != 34) {
         return false;
      }
      return true;
   }
   // '"' / ''
   private static <T> boolean e78(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         // '"'
         if (e79(c)) {
            temp = false;
         } else {
            c.pos = pos;
         }
      }
      if (temp) {
         int pos2 = c.pos;
         // ''
         if (e6(c)) {
            temp = false;
         } else {
            c.pos = pos2;
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // '\\' ('' / ('"' / ''))
   private static <T> boolean e77(ParserContext<T> c) {
      if (c.read() != 92) {
         return false;
      }
      boolean temp = true;
      switch(_index47[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // ''
         temp = e6(c);
         break;
         case 2: 
         // '"' / ''
         temp = e78(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // [\x01-!#-\xff]
   private static <T> boolean e76(ParserContext<T> c) {
      if (!_set46[c.read()]) {
         return false;
      }
      return true;
   }
   // [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
   private static <T> boolean e75(ParserContext<T> c) {
      boolean temp = true;
      switch(_index45[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // [\x01-!#-\xff]
         temp = e76(c);
         break;
         case 2: 
         // '\\' ('' / ('"' / ''))
         temp = e77(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__))
   private static <T> boolean e74(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text5)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         // [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
         if (!e75(c)) {
            c.pos = pos;
            break;
         }
      }
      c.endTree(0,_TInterpolatedStringLiteral,null);
      if (c.read() != 34) {
         return false;
      }
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__))
   private static <T> boolean e81(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 34) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         // [\x01-!#-\xff] / '\\' ('' / ('"' / ''))
         if (!e75(c)) {
            c.pos = pos;
            break;
         }
      }
      c.endTree(0,_TStringSegment,null);
      if (c.read() != 34) {
         return false;
      }
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral
   private static <T> boolean e80(ParserContext<T> c) {
      if (!e81(c)) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__))
         if (!e81(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      c.tagTree(_TStringLiteral);
      return true;
   }
   // $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__)) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral
   private static <T> boolean e73(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__))
         if (e74(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral
         if (e80(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__)) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral / $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__))) }
   private static <T> boolean e72(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index44[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__)) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral
         temp = e73(c);
         break;
         case 2: 
         // ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral
         temp = e80(c);
         break;
         case 3: 
         // $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__))
         temp = e74(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__)) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral) / ($((~__ '"' { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #StringSegment } '"' ~__)))+ #StringLiteral / $((~__ "F\"" { ([\x01-!#-\xff] / '\\' ('' / ('"' / '')))* #InterpolatedStringLiteral } '"' ~__))) }
   private static <T> boolean pStringLiteral(ParserContext<T> c) {
      int memo = c.memoLookupTree(10);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e72(c)) {
            c.memoTreeSucc(10,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(10);
            return false;
         }
      }
      return memo == 1;
   }
   // $(StringLiteral) #LiteralValue
   private static <T> boolean e150(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pStringLiteral(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TLiteralValue);
      return true;
   }
   // $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__))
   private static <T> boolean e149(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!_set66[c.read()]) {
         return false;
      }
      if (!_set67[c.read()]) {
         return false;
      }
      if (!_set66[c.read()]) {
         return false;
      }
      c.beginTree(-3);
      c.endTree(0,_TCharLiteral,null);
      if (!p___(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue
   private static <T> boolean e148(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(NumLiteral)
         if (e52(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__))
         if (e149(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(StringLiteral) #LiteralValue
         if (e150(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
   private static <T> boolean e147(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      boolean temp = true;
      switch(_index65[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue
         temp = e148(c);
         break;
         case 2: 
         // $(StringLiteral) #LiteralValue
         temp = e150(c);
         break;
         case 3: 
         // $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__))
         temp = e149(c);
         break;
         case 4: 
         // $(NumLiteral)
         temp = e52(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { $(NumLiteral) ~__ ".." ($(NumLiteral))? #OpenRange }
   private static <T> boolean e128(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNumLiteral(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text63)) {
         return false;
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(NumLiteral)
      if (!e52(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TOpenRange,null);
      return true;
   }
   // { $(NumLiteral) ~__ ".." ($(NumLiteral))? #OpenRange }
   private static <T> boolean pOpenRange(ParserContext<T> c) {
      int memo = c.memoLookupTree(47);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e128(c)) {
            c.memoTreeSucc(47,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(47);
            return false;
         }
      }
      return memo == 1;
   }
   // $(OpenRange) #Iterable
   private static <T> boolean e132(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pOpenRange(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TIterable);
      return true;
   }
   // ~__ '|'
   private static <T> boolean p_BitOr(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 124) {
         return false;
      }
      return true;
   }
   // ~BitOr $(Expression)
   private static <T> boolean e131(ParserContext<T> c) {
      if (!p_BitOr(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // ~Arrow { $(Expression) (~BitOr $(Expression))* #ComprPipeline }
   private static <T> boolean pComprPipeline(ParserContext<T> c) {
      if (!p_Arrow(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~BitOr $(Expression)
         if (!e131(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TComprPipeline,null);
      return true;
   }
   // ~__ ':'
   private static <T> boolean p_Colon(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 58) {
         return false;
      }
      return true;
   }
   // { $(Name) ~Colon $(OpenRange) #NamedRange }
   private static <T> boolean pNamedRange(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p_Colon(c)) {
         return false;
      }
      {
         T left1 = c.saveTree();
         if (!pOpenRange(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TNamedRange,null);
      return true;
   }
   // ~__ '|'
   private static <T> boolean e130(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 124) {
         return false;
      }
      return true;
   }
   // (~__ ',' / ~__ '|') $(NamedRange)
   private static <T> boolean e129(ParserContext<T> c) {
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            // ~__ ','
            if (e14(c)) {
               temp = false;
            } else {
               c.pos = pos;
            }
         }
         if (temp) {
            int pos2 = c.pos;
            // ~__ '|'
            if (e130(c)) {
               temp = false;
            } else {
               c.pos = pos2;
            }
         }
         if (temp) {
            return false;
         }
      }
      {
         T left = c.saveTree();
         if (!pNamedRange(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // ~LSqB { $(NamedRange) ((~__ ',' / ~__ '|') $(NamedRange))* $(ComprPipeline) #Comprehension } ~RSqB
   private static <T> boolean pComprehension(ParserContext<T> c) {
      if (!p_LSqB(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNamedRange(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // (~__ ',' / ~__ '|') $(NamedRange)
         if (!e129(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      {
         T left4 = c.saveTree();
         if (!pComprPipeline(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      c.endTree(0,_TComprehension,null);
      if (!p_RSqB(c)) {
         return false;
      }
      return true;
   }
   // $(Comprehension)
   private static <T> boolean e127(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pComprehension(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(Comprehension) / $(OpenRange) #Iterable
   private static <T> boolean e126(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Comprehension)
         if (e127(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(OpenRange) #Iterable
         if (e132(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)) }
   private static <T> boolean e125(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index62[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(Comprehension) / $(OpenRange) #Iterable
         temp = e126(c);
         break;
         case 2: 
         // $(OpenRange) #Iterable
         temp = e132(c);
         break;
         case 3: 
         // $(Comprehension)
         temp = e127(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($(Comprehension) / $(OpenRange) #Iterable) / $(OpenRange) #Iterable / $(Comprehension)) }
   private static <T> boolean pIterable(ParserContext<T> c) {
      int memo = c.memoLookupTree(34);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e125(c)) {
            c.memoTreeSucc(34,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(34);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Iterable)
   private static <T> boolean e146(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pIterable(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~Comma $(Expression)
   private static <T> boolean e95(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Expression) (~Comma $(Expression))* #ExpressionList }
   private static <T> boolean e94(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(Expression)
         if (!e95(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TExpressionList,null);
      return true;
   }
   // { $(Expression) (~Comma $(Expression))* #ExpressionList }
   private static <T> boolean pExpressionList(ParserContext<T> c) {
      int memo = c.memoLookupTree(44);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e94(c)) {
            c.memoTreeSucc(44,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(44);
            return false;
         }
      }
      return memo == 1;
   }
   // { $(ExpressionList) #ArrayElements } ~Comma?
   private static <T> boolean pArrayElements(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpressionList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TArrayElements,null);
      int pos = c.pos;
      // ~Comma
      if (!p_Comma(c)) {
         c.pos = pos;
      }
      return true;
   }
   // $(ArrayElements)
   private static <T> boolean e139(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pArrayElements(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LSqB { ($(ArrayElements))? #ArrayConstruction } ~RSqB
   private static <T> boolean pArrayConstruction(ParserContext<T> c) {
      if (!p_LSqB(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(ArrayElements)
      if (!e139(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TArrayConstruction,null);
      if (!p_RSqB(c)) {
         return false;
      }
      return true;
   }
   // $(ArrayConstruction)
   private static <T> boolean e138(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pArrayConstruction(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(ArrayConstruction) / $(Iterable)
   private static <T> boolean e155(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ArrayConstruction)
         if (e138(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Iterable)
         if (e146(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~LSqB { $(Expression) #Index } ~RSqB
   private static <T> boolean pIndex(ParserContext<T> c) {
      if (!p_LSqB(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TIndex,null);
      if (!p_RSqB(c)) {
         return false;
      }
      return true;
   }
   // $(Index) #Invoke
   private static <T> boolean e97(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pIndex(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TInvoke);
      return true;
   }
   // ~__ !"F\"" [$@-Z_a-z] { [$.0-9A-Z_a-z]* #QualifiedName } ~__
   private static <T> boolean e8(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.match(_text5)) {
         return false;
      }
      if (!_set6[c.read()]) {
         return false;
      }
      c.beginTree(-1);
      while (_set7[c.prefetch()]) {
         c.move(1);
      }
      c.endTree(0,_TQualifiedName,null);
      if (!p___(c)) {
         return false;
      }
      return true;
   }
   // ~__ !"F\"" [$@-Z_a-z] { [$.0-9A-Z_a-z]* #QualifiedName } ~__
   private static <T> boolean pQualifiedName(ParserContext<T> c) {
      int memo = c.memoLookupTree(9);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e8(c)) {
            c.memoTreeSucc(9,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(9);
            return false;
         }
      }
      return memo == 1;
   }
   // { ~__ '.' $(QualifiedName) ($(Invoke))? #MethodCall }
   private static <T> boolean pMethodCall(ParserContext<T> c) {
      c.beginTree(0);
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 46) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pQualifiedName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(Invoke)
      if (!e85(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TMethodCall,null);
      return true;
   }
   // $(MethodCall)
   private static <T> boolean e96(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pMethodCall(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { $(Name) ~__ '=' $(Expression) #NamedAssignment }
   private static <T> boolean pNamedAssignment(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 61) {
         return false;
      }
      {
         T left1 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TNamedAssignment,null);
      return true;
   }
   // ~Comma $(NamedAssignment)
   private static <T> boolean e91(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pNamedAssignment(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(NamedAssignment) (~Comma $(NamedAssignment))* #NamedAssignmentList }
   private static <T> boolean pNamedAssignmentList(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNamedAssignment(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(NamedAssignment)
         if (!e91(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TNamedAssignmentList,null);
      return true;
   }
   // ~LP { $(NamedAssignmentList) #NamedCallParameters } ~RP
   private static <T> boolean pNamedCallParameters(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNamedAssignmentList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TNamedCallParameters,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // $(NamedCallParameters)
   private static <T> boolean e90(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pNamedCallParameters(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(ExpressionList) ($(OptionalEllipsis))?
   private static <T> boolean e93(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pExpressionList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      return true;
   }
   // ~LP { ($(ExpressionList) ($(OptionalEllipsis))?)? #AnonymousCallParameters } ~RP
   private static <T> boolean pAnonymousCallParameters(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(ExpressionList) ($(OptionalEllipsis))?
      if (!e93(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TAnonymousCallParameters,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // $(AnonymousCallParameters)
   private static <T> boolean e92(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pAnonymousCallParameters(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(TypeArguments))? (($(NamedCallParameters) / $(AnonymousCallParameters))) #CallParameters }
   private static <T> boolean e89(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(TypeArguments)
      if (!e27(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      {
         boolean temp = true;
         if (temp) {
            int pos4 = c.pos;
            T left5 = c.saveTree();
            int log6 = c.saveLog();
            // $(NamedCallParameters)
            if (e90(c)) {
               temp = false;
            } else {
               c.pos = pos4;
               c.backTree(left5);
               c.backLog(log6);
            }
         }
         if (temp) {
            int pos7 = c.pos;
            T left8 = c.saveTree();
            int log9 = c.saveLog();
            // $(AnonymousCallParameters)
            if (e92(c)) {
               temp = false;
            } else {
               c.pos = pos7;
               c.backTree(left8);
               c.backLog(log9);
            }
         }
         if (temp) {
            return false;
         }
      }
      c.endTree(0,_TCallParameters,null);
      return true;
   }
   // { ($(TypeArguments))? (($(NamedCallParameters) / $(AnonymousCallParameters))) #CallParameters }
   private static <T> boolean pCallParameters(ParserContext<T> c) {
      int memo = c.memoLookupTree(43);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e89(c)) {
            c.memoTreeSucc(43,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(43);
            return false;
         }
      }
      return memo == 1;
   }
   // $(CallParameters)
   private static <T> boolean e88(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pCallParameters(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(CallParameters) / $(MethodCall) / $(Index) #Invoke
   private static <T> boolean e87(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(CallParameters)
         if (e88(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(MethodCall)
         if (e96(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Index) #Invoke
         if (e97(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke) }
   private static <T> boolean e86(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index48[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(CallParameters) / $(MethodCall) / $(Index) #Invoke
         temp = e87(c);
         break;
         case 2: 
         // $(CallParameters)
         temp = e88(c);
         break;
         case 3: 
         // $(MethodCall)
         temp = e96(c);
         break;
         case 4: 
         // $(Index) #Invoke
         temp = e97(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($(CallParameters) / $(MethodCall) / $(Index) #Invoke) / $(CallParameters) / $(MethodCall) / $(Index) #Invoke) }
   private static <T> boolean pInvoke(ParserContext<T> c) {
      int memo = c.memoLookupTree(32);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e86(c)) {
            c.memoTreeSucc(32,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(32);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Invoke)
   private static <T> boolean e85(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pInvoke(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // "++"
   private static <T> boolean e157(ParserContext<T> c) {
      if (!c.match(_text69)) {
         return false;
      }
      return true;
   }
   // "--"
   private static <T> boolean e158(ParserContext<T> c) {
      if (!c.match(_text70)) {
         return false;
      }
      return true;
   }
   // ~__ ("++" / "--")
   private static <T> boolean e156(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      boolean temp = true;
      switch(_index68[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "++"
         temp = e157(c);
         break;
         case 2: 
         // "--"
         temp = e158(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
   private static <T> boolean e153(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Iterable)
         if (e146(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         if (e147(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(QualifiedName)
   private static <T> boolean e145(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pQualifiedName(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(TypeName))? $(CallParameters) #MultiparamLambda } ~Arrow
   private static <T> boolean pMultiparamLambda(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(TypeName)
      if (!e25(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      {
         T left3 = c.saveTree();
         if (!pCallParameters(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TMultiparamLambda,null);
      if (!p_Arrow(c)) {
         return false;
      }
      return true;
   }
   // $(MultiparamLambda)
   private static <T> boolean e144(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pMultiparamLambda(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { $(QualifiedName) ($(Invoke))* #ValueReference } ~BitOr
   private static <T> boolean pValueReference(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pQualifiedName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // $(Invoke)
         if (!e85(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
         if (pos == c.pos) {
            break;
         }
      }
      c.endTree(0,_TValueReference,null);
      if (!p_BitOr(c)) {
         return false;
      }
      return true;
   }
   // $(ValueReference)
   private static <T> boolean e84(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pValueReference(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~BlockStart { ($(ValueReference))? $(NamedAssignmentList) #ObjConstructionBody } ~BlockEnd
   private static <T> boolean pObjConstructionBody(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(ValueReference)
      if (!e84(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      {
         T left3 = c.saveTree();
         if (!pNamedAssignmentList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TObjConstructionBody,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // { ($(TypeName))? $(ObjConstructionBody) #ObjConstruction }
   private static <T> boolean e83(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(TypeName)
      if (!e25(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      {
         T left3 = c.saveTree();
         if (!pObjConstructionBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TObjConstruction,null);
      return true;
   }
   // { ($(TypeName))? $(ObjConstructionBody) #ObjConstruction }
   private static <T> boolean pObjConstruction(ParserContext<T> c) {
      int memo = c.memoLookupTree(24);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e83(c)) {
            c.memoTreeSucc(24,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(24);
            return false;
         }
      }
      return memo == 1;
   }
   // $(ObjConstruction)
   private static <T> boolean e82(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pObjConstruction(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(ObjConstruction) / $(Lambda) / $(QualifiedName)
   private static <T> boolean e151(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ObjConstruction)
         if (e82(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Lambda)
         if (e140(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(QualifiedName)
         if (e145(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(Expression)
   private static <T> boolean e106(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pExpression(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LP { ($(ValueReference))? ($(Expression))? (~Comma $(Expression))* ($(OptionalEllipsis))? #TupleConstruction } ~RP
   private static <T> boolean e122(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(ValueReference)
      if (!e84(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(Expression)
      if (!e106(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      while (true) {
         int pos6 = c.pos;
         T left7 = c.saveTree();
         int log8 = c.saveLog();
         // ~Comma $(Expression)
         if (!e95(c)) {
            c.pos = pos6;
            c.backTree(left7);
            c.backLog(log8);
            break;
         }
      }
      int pos9 = c.pos;
      T left10 = c.saveTree();
      int log11 = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos9;
         c.backTree(left10);
         c.backLog(log11);
      }
      c.endTree(0,_TTupleConstruction,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~LP { ($(ValueReference))? ($(Expression))? (~Comma $(Expression))* ($(OptionalEllipsis))? #TupleConstruction } ~RP
   private static <T> boolean pTupleConstruction(ParserContext<T> c) {
      int memo = c.memoLookupTree(46);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e122(c)) {
            c.memoTreeSucc(46,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(46);
            return false;
         }
      }
      return memo == 1;
   }
   // $(TupleConstruction)
   private static <T> boolean e121(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTupleConstruction(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(TupleConstruction) / $(Lambda)
   private static <T> boolean e152(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(TupleConstruction)
         if (e121(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Lambda)
         if (e140(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(ObjConstruction) / $(ArrayConstruction) / $(TupleConstruction) / $(Lambda) / $(QualifiedName) / $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
   private static <T> boolean e137(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ObjConstruction)
         if (e82(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(ArrayConstruction)
         if (e138(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(TupleConstruction)
         if (e121(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(Lambda)
         if (e140(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         int pos13 = c.pos;
         T left14 = c.saveTree();
         int log15 = c.saveLog();
         // $(QualifiedName)
         if (e145(c)) {
            temp = false;
         } else {
            c.pos = pos13;
            c.backTree(left14);
            c.backLog(log15);
         }
      }
      if (temp) {
         int pos16 = c.pos;
         T left17 = c.saveTree();
         int log18 = c.saveLog();
         // $(Iterable)
         if (e146(c)) {
            temp = false;
         } else {
            c.pos = pos16;
            c.backTree(left17);
            c.backLog(log18);
         }
      }
      if (temp) {
         int pos19 = c.pos;
         T left20 = c.saveTree();
         int log21 = c.saveLog();
         // $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         if (e147(c)) {
            temp = false;
         } else {
            c.pos = pos19;
            c.backTree(left20);
            c.backLog(log21);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { $(Value) #ValueStatement } ~Semicolon
   private static <T> boolean e135(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pValue(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TValueStatement,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // { $(Value) #ValueStatement } ~Semicolon
   private static <T> boolean pValueStatement(ParserContext<T> c) {
      int memo = c.memoLookupTree(5);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e135(c)) {
            c.memoTreeSucc(5,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(5);
            return false;
         }
      }
      return memo == 1;
   }
   // $(ValueStatement) #Statement
   private static <T> boolean e134(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pValueStatement(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TStatement);
      return true;
   }
   // $(MethodBlockBody) / $(ValueStatement) #Statement
   private static <T> boolean e169(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MethodBlockBody)
         if (e133(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { $(Iterable) ($(Invoke))* #IterableStatement } ~Semicolon
   private static <T> boolean pIterableStatement(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pIterable(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // $(Invoke)
         if (!e85(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
         if (pos == c.pos) {
            break;
         }
      }
      c.endTree(0,_TIterableStatement,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $(IterableStatement)
   private static <T> boolean e124(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pIterableStatement(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // 'l'
   private static <T> boolean e46(ParserContext<T> c) {
      if (c.read() != 108) {
         return false;
      }
      return true;
   }
   // 'r'
   private static <T> boolean e47(ParserContext<T> c) {
      if (c.read() != 114) {
         return false;
      }
      return true;
   }
   // "va" ('l' / 'r')
   private static <T> boolean e45(ParserContext<T> c) {
      if (!c.match(_text21)) {
         return false;
      }
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            // 'l'
            if (e46(c)) {
               temp = false;
            } else {
               c.pos = pos;
            }
         }
         if (temp) {
            int pos2 = c.pos;
            // 'r'
            if (e47(c)) {
               temp = false;
            } else {
               c.pos = pos2;
            }
         }
         if (temp) {
            return false;
         }
      }
      return true;
   }
   // "va" ('l' / 'r') / $(TypeName)
   private static <T> boolean e44(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         // "va" ('l' / 'r')
         if (e45(c)) {
            temp = false;
         } else {
            c.pos = pos;
         }
      }
      if (temp) {
         int pos2 = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(TypeName)
         if (e25(c)) {
            temp = false;
         } else {
            c.pos = pos2;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~__ { ($(TypeName) / ("va" ('l' / 'r') / $(TypeName))) #VarTitle }
   private static <T> boolean pVarTitle(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      c.beginTree(0);
      boolean temp = true;
      switch(_index20[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(TypeName)
         temp = e25(c);
         break;
         case 2: 
         // "va" ('l' / 'r') / $(TypeName)
         temp = e44(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_TVarTitle,null);
      return true;
   }
   // ~LP { $(VarTitle) $(Name) ~Colon $(Expression) #ForSetup } ~RP
   private static <T> boolean pForSetup(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pVarTitle(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      if (!p_Colon(c)) {
         return false;
      }
      {
         T left2 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      c.endTree(0,_TForSetup,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~__ "for" { $(ForSetup) $(MethodBlockBody) #ForStatement }
   private static <T> boolean pForStatement(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text19)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pForSetup(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pMethodBlockBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TForStatement,null);
      return true;
   }
   // $(ForStatement)
   private static <T> boolean e43(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pForStatement(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LP { $(Expression) #BooleanExpression } ~RP
   private static <T> boolean pBooleanExpression(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TBooleanExpression,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~__ "else" $(MethodBlockBody)
   private static <T> boolean e170(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text71)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pMethodBlockBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // ~__ "if" { $(BooleanExpression) $(MethodBlockBody) (~__ "else" $(MethodBlockBody))? #IfExpression }
   private static <T> boolean e36(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text17)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pBooleanExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pMethodBlockBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      int pos = c.pos;
      T left3 = c.saveTree();
      int log = c.saveLog();
      // ~__ "else" $(MethodBlockBody)
      if (!e170(c)) {
         c.pos = pos;
         c.backTree(left3);
         c.backLog(log);
      }
      c.endTree(0,_TIfExpression,null);
      return true;
   }
   // ~__ "if" { $(BooleanExpression) $(MethodBlockBody) (~__ "else" $(MethodBlockBody))? #IfExpression }
   private static <T> boolean pIfExpression(ParserContext<T> c) {
      int memo = c.memoLookupTree(30);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e36(c)) {
            c.memoTreeSucc(30,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(30);
            return false;
         }
      }
      return memo == 1;
   }
   // $(IfExpression)
   private static <T> boolean e35(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pIfExpression(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(StringLiteral)
   private static <T> boolean e71(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pStringLiteral(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(NumLiteral) / $(StringLiteral) / $(ObjConstruction)
   private static <T> boolean pCaseExpr(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(NumLiteral)
         if (e52(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(StringLiteral)
         if (e71(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(ObjConstruction)
         if (e82(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~__ "case" { CaseExpr ~Arrow $(Expression) #SelectCase } ~Semicolon
   private static <T> boolean pSelectCase(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text23)) {
         return false;
      }
      c.beginTree(0);
      if (!pCaseExpr(c)) {
         return false;
      }
      if (!p_Arrow(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TSelectCase,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $(SelectCase)
   private static <T> boolean e51(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pSelectCase(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~BlockStart { ($(SelectCase))* ($(Default))? #SelectBody } ~BlockEnd
   private static <T> boolean pSelectBody(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(SelectCase)
         if (!e51(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(Default)
      if (!e98(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      c.endTree(0,_TSelectBody,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // ~__ "select" { $(NestedExpr) $(SelectBody) #SelectExpression }
   private static <T> boolean e49(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text22)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNestedExpr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pSelectBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TSelectExpression,null);
      return true;
   }
   // ~__ "select" { $(NestedExpr) $(SelectBody) #SelectExpression }
   private static <T> boolean pSelectExpression(ParserContext<T> c) {
      int memo = c.memoLookupTree(31);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e49(c)) {
            c.memoTreeSucc(31,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(31);
            return false;
         }
      }
      return memo == 1;
   }
   // $(SelectExpression)
   private static <T> boolean e48(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pSelectExpression(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "continue" { #ContinueStatement } ~Semicolon))
   private static <T> boolean e108(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text56)) {
         return false;
      }
      c.beginTree(-8);
      c.endTree(0,_TContinueStatement,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~Comma $(Name)
   private static <T> boolean e120(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Name) (~Comma $(Name))* #NameList }
   private static <T> boolean pNameList(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(Name)
         if (!e120(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TNameList,null);
      return true;
   }
   // { $((~LP { $(NameList) #RequiredNameList } ~RP)) ~__ '=' ($(TupleConstruction) / $(Expression)) #ParallelAssignment }
   private static <T> boolean pParallelAssignment(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!p_LP(c)) {
            return false;
         }
         c.beginTree(0);
         {
            T left1 = c.saveTree();
            if (!pNameList(c)) {
               return false;
            }
            c.linkTree(_L);
            c.backTree(left1);
         }
         c.endTree(0,_TRequiredNameList,null);
         if (!p_RP(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 61) {
         return false;
      }
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            T left4 = c.saveTree();
            int log = c.saveLog();
            // $(TupleConstruction)
            if (e121(c)) {
               temp = false;
            } else {
               c.pos = pos;
               c.backTree(left4);
               c.backLog(log);
            }
         }
         if (temp) {
            int pos6 = c.pos;
            T left7 = c.saveTree();
            int log8 = c.saveLog();
            // $(Expression)
            if (e106(c)) {
               temp = false;
            } else {
               c.pos = pos6;
               c.backTree(left7);
               c.backLog(log8);
            }
         }
         if (temp) {
            return false;
         }
      }
      c.endTree(0,_TParallelAssignment,null);
      return true;
   }
   // $(ParallelAssignment) #AssignmentExpr
   private static <T> boolean e119(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pParallelAssignment(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TAssignmentExpr);
      return true;
   }
   // { ($(RegularAssignment) / $(ParallelAssignment) #AssignmentExpr) }
   private static <T> boolean pAssignmentExpr(ParserContext<T> c) {
      c.beginTree(0);
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            T left = c.saveTree();
            int log = c.saveLog();
            // $(RegularAssignment)
            if (e112(c)) {
               temp = false;
            } else {
               c.pos = pos;
               c.backTree(left);
               c.backLog(log);
            }
         }
         if (temp) {
            int pos4 = c.pos;
            T left5 = c.saveTree();
            int log6 = c.saveLog();
            // $(ParallelAssignment) #AssignmentExpr
            if (e119(c)) {
               temp = false;
            } else {
               c.pos = pos4;
               c.backTree(left5);
               c.backLog(log6);
            }
         }
         if (temp) {
            return false;
         }
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { $(VarTitle) $(Assignment) #VariableDeclaration }
   private static <T> boolean e110(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pVarTitle(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pAssignment(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TVariableDeclaration,null);
      return true;
   }
   // { $(VarTitle) $(Assignment) #VariableDeclaration }
   private static <T> boolean pVariableDeclaration(ParserContext<T> c) {
      int memo = c.memoLookupTree(11);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e110(c)) {
            c.memoTreeSucc(11,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(11);
            return false;
         }
      }
      return memo == 1;
   }
   // $(VariableDeclaration)
   private static <T> boolean e109(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pVariableDeclaration(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "break" { #BreakStatement } ~Semicolon))
   private static <T> boolean e107(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text55)) {
         return false;
      }
      c.beginTree(-5);
      c.endTree(0,_TBreakStatement,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "return" { ($(Expression))? #ReturnStatement } ~Semicolon
   private static <T> boolean pReturnStatement(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text54)) {
         return false;
      }
      c.beginTree(-6);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Expression)
      if (!e106(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TReturnStatement,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $(ReturnStatement)
   private static <T> boolean e105(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pReturnStatement(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(ForStatement) / $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(ReturnStatement) / $((~__ "break" { #BreakStatement } ~Semicolon)) / $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(IterableStatement) / $(MethodBlockBody) / $(ValueStatement) #Statement
   private static <T> boolean e42(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ForStatement)
         if (e43(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(IfExpression)
         if (e35(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(SelectExpression)
         if (e48(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(MatchExpression)
         if (e99(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         int pos13 = c.pos;
         T left14 = c.saveTree();
         int log15 = c.saveLog();
         // $(ReturnStatement)
         if (e105(c)) {
            temp = false;
         } else {
            c.pos = pos13;
            c.backTree(left14);
            c.backLog(log15);
         }
      }
      if (temp) {
         int pos16 = c.pos;
         T left17 = c.saveTree();
         int log18 = c.saveLog();
         // $((~__ "break" { #BreakStatement } ~Semicolon))
         if (e107(c)) {
            temp = false;
         } else {
            c.pos = pos16;
            c.backTree(left17);
            c.backLog(log18);
         }
      }
      if (temp) {
         int pos19 = c.pos;
         T left20 = c.saveTree();
         int log21 = c.saveLog();
         // $((~__ "continue" { #ContinueStatement } ~Semicolon))
         if (e108(c)) {
            temp = false;
         } else {
            c.pos = pos19;
            c.backTree(left20);
            c.backLog(log21);
         }
      }
      if (temp) {
         int pos22 = c.pos;
         T left23 = c.saveTree();
         int log24 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos22;
            c.backTree(left23);
            c.backLog(log24);
         }
      }
      if (temp) {
         int pos25 = c.pos;
         T left26 = c.saveTree();
         int log27 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos25;
            c.backTree(left26);
            c.backLog(log27);
         }
      }
      if (temp) {
         int pos28 = c.pos;
         T left29 = c.saveTree();
         int log30 = c.saveLog();
         // $(IterableStatement)
         if (e124(c)) {
            temp = false;
         } else {
            c.pos = pos28;
            c.backTree(left29);
            c.backLog(log30);
         }
      }
      if (temp) {
         int pos31 = c.pos;
         T left32 = c.saveTree();
         int log33 = c.saveLog();
         // $(MethodBlockBody)
         if (e133(c)) {
            temp = false;
         } else {
            c.pos = pos31;
            c.backTree(left32);
            c.backLog(log33);
         }
      }
      if (temp) {
         int pos34 = c.pos;
         T left35 = c.saveTree();
         int log36 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos34;
            c.backTree(left35);
            c.backLog(log36);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(MatchExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e166(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MatchExpression)
         if (e99(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(ReturnStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e167(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ReturnStatement)
         if (e105(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(ForStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e164(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ForStatement)
         if (e43(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(IfExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e165(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(IfExpression)
         if (e35(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $((~__ "break" { #BreakStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e162(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ "break" { #BreakStatement } ~Semicolon))
         if (e107(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e163(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ "continue" { #ContinueStatement } ~Semicolon))
         if (e108(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e160(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(IterableStatement) / $(ValueStatement) #Statement
   private static <T> boolean e161(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(IterableStatement)
         if (e124(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e159(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($(ForStatement) / $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(ReturnStatement) / $((~__ "break" { #BreakStatement } ~Semicolon)) / $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(IterableStatement) / $(MethodBlockBody) / $(ValueStatement) #Statement) / $(ValueStatement) #Statement / ($(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(Assignment) / $(ValueStatement) #Statement) / ($(IterableStatement) / $(ValueStatement) #Statement) / ($((~__ "break" { #BreakStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(ForStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(IfExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(MatchExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(ReturnStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(SelectExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(MethodBlockBody) / $(ValueStatement) #Statement)) }
   private static <T> boolean e41(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index18[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(ForStatement) / $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(ReturnStatement) / $((~__ "break" { #BreakStatement } ~Semicolon)) / $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(IterableStatement) / $(MethodBlockBody) / $(ValueStatement) #Statement
         temp = e42(c);
         break;
         case 2: 
         // $(ValueStatement) #Statement
         temp = e134(c);
         break;
         case 3: 
         // $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e159(c);
         break;
         case 4: 
         // $(Assignment) / $(ValueStatement) #Statement
         temp = e160(c);
         break;
         case 5: 
         // $(IterableStatement) / $(ValueStatement) #Statement
         temp = e161(c);
         break;
         case 6: 
         // $((~__ "break" { #BreakStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e162(c);
         break;
         case 7: 
         // $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e163(c);
         break;
         case 8: 
         // $(ForStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e164(c);
         break;
         case 9: 
         // $(IfExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e165(c);
         break;
         case 10: 
         // $(MatchExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e166(c);
         break;
         case 11: 
         // $(ReturnStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e167(c);
         break;
         case 12: 
         // $(SelectExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
         temp = e168(c);
         break;
         case 13: 
         // $(MethodBlockBody) / $(ValueStatement) #Statement
         temp = e169(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($(ForStatement) / $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(ReturnStatement) / $((~__ "break" { #BreakStatement } ~Semicolon)) / $((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(IterableStatement) / $(MethodBlockBody) / $(ValueStatement) #Statement) / $(ValueStatement) #Statement / ($(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(Assignment) / $(ValueStatement) #Statement) / ($(IterableStatement) / $(ValueStatement) #Statement) / ($((~__ "break" { #BreakStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($((~__ "continue" { #ContinueStatement } ~Semicolon)) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(ForStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(IfExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(MatchExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(ReturnStatement) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(SelectExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement) / ($(MethodBlockBody) / $(ValueStatement) #Statement)) }
   private static <T> boolean pStatement(ParserContext<T> c) {
      int memo = c.memoLookupTree(42);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e41(c)) {
            c.memoTreeSucc(42,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(42);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Statement)
   private static <T> boolean e40(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pStatement(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~BlockStart { ($(Statement))* } ~BlockEnd
   private static <T> boolean e39(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Statement)
         if (!e40(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
         if (pos == c.pos) {
            break;
         }
      }
      c.endTree(0,_T,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // ~BlockStart { ($(Statement))* } ~BlockEnd
   private static <T> boolean pMethodBlockBody(ParserContext<T> c) {
      int memo = c.memoLookupTree(15);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e39(c)) {
            c.memoTreeSucc(15,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(15);
            return false;
         }
      }
      return memo == 1;
   }
   // $(MethodBlockBody)
   private static <T> boolean e133(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pMethodBlockBody(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(Name) ~Arrow
   private static <T> boolean e143(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p_Arrow(c)) {
         return false;
      }
      return true;
   }
   // $(({ ($(Name) ~Arrow)+ #SingleParamLambda }))
   private static <T> boolean e142(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      if (!e143(c)) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // $(Name) ~Arrow
         if (!e143(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TSingleParamLambda,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(({ ($(Name) ~Arrow)+ #SingleParamLambda })) / $(MultiparamLambda)) ~Ellipsis? ($(Expression) / $(MethodBlockBody)) #Lambda }
   private static <T> boolean e141(ParserContext<T> c) {
      c.beginTree(0);
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            T left = c.saveTree();
            int log = c.saveLog();
            // $(({ ($(Name) ~Arrow)+ #SingleParamLambda }))
            if (e142(c)) {
               temp = false;
            } else {
               c.pos = pos;
               c.backTree(left);
               c.backLog(log);
            }
         }
         if (temp) {
            int pos4 = c.pos;
            T left5 = c.saveTree();
            int log6 = c.saveLog();
            // $(MultiparamLambda)
            if (e144(c)) {
               temp = false;
            } else {
               c.pos = pos4;
               c.backTree(left5);
               c.backLog(log6);
            }
         }
         if (temp) {
            return false;
         }
      }
      int pos7 = c.pos;
      // ~Ellipsis
      if (!p_Ellipsis(c)) {
         c.pos = pos7;
      }
      {
         boolean temp8 = true;
         if (temp8) {
            int pos9 = c.pos;
            T left10 = c.saveTree();
            int log11 = c.saveLog();
            // $(Expression)
            if (e106(c)) {
               temp8 = false;
            } else {
               c.pos = pos9;
               c.backTree(left10);
               c.backLog(log11);
            }
         }
         if (temp8) {
            int pos12 = c.pos;
            T left13 = c.saveTree();
            int log14 = c.saveLog();
            // $(MethodBlockBody)
            if (e133(c)) {
               temp8 = false;
            } else {
               c.pos = pos12;
               c.backTree(left13);
               c.backLog(log14);
            }
         }
         if (temp8) {
            return false;
         }
      }
      c.endTree(0,_TLambda,null);
      return true;
   }
   // { ($(({ ($(Name) ~Arrow)+ #SingleParamLambda })) / $(MultiparamLambda)) ~Ellipsis? ($(Expression) / $(MethodBlockBody)) #Lambda }
   private static <T> boolean pLambda(ParserContext<T> c) {
      int memo = c.memoLookupTree(26);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e141(c)) {
            c.memoTreeSucc(26,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(26);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Lambda)
   private static <T> boolean e140(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pLambda(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(ObjConstruction) / $(Lambda) / $(QualifiedName) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
   private static <T> boolean e154(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(ObjConstruction)
         if (e82(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Lambda)
         if (e140(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(QualifiedName)
         if (e145(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         if (e147(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($(ObjConstruction) / $(ArrayConstruction) / $(TupleConstruction) / $(Lambda) / $(QualifiedName) / $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) })) / ($(ObjConstruction) / $(Lambda) / $(QualifiedName)) / ($(TupleConstruction) / $(Lambda)) / ($(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / $(Lambda) / ($(ObjConstruction) / $(Lambda) / $(QualifiedName) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / ($(ArrayConstruction) / $(Iterable)) / $(ObjConstruction)) ($(Invoke))* (~__ ("++" / "--"))? #Value }
   private static <T> boolean e136(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index64[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(ObjConstruction) / $(ArrayConstruction) / $(TupleConstruction) / $(Lambda) / $(QualifiedName) / $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         temp = e137(c);
         break;
         case 2: 
         // $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         temp = e147(c);
         break;
         case 3: 
         // $(ObjConstruction) / $(Lambda) / $(QualifiedName)
         temp = e151(c);
         break;
         case 4: 
         // $(TupleConstruction) / $(Lambda)
         temp = e152(c);
         break;
         case 5: 
         // $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         temp = e153(c);
         break;
         case 6: 
         // $(Lambda)
         temp = e140(c);
         break;
         case 7: 
         // $(ObjConstruction) / $(Lambda) / $(QualifiedName) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))
         temp = e154(c);
         break;
         case 8: 
         // $(ArrayConstruction) / $(Iterable)
         temp = e155(c);
         break;
         case 9: 
         // $(ObjConstruction)
         temp = e82(c);
         break;
      }
      if (!temp) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Invoke)
         if (!e85(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
         if (pos == c.pos) {
            break;
         }
      }
      int pos4 = c.pos;
      // ~__ ("++" / "--")
      if (!e156(c)) {
         c.pos = pos4;
      }
      c.endTree(0,_TValue,null);
      return true;
   }
   // { (($(ObjConstruction) / $(ArrayConstruction) / $(TupleConstruction) / $(Lambda) / $(QualifiedName) / $(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) })) / ($(ObjConstruction) / $(Lambda) / $(QualifiedName)) / ($(TupleConstruction) / $(Lambda)) / ($(Iterable) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / $(Lambda) / ($(ObjConstruction) / $(Lambda) / $(QualifiedName) / $(({ (($(NumLiteral) / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(StringLiteral) #LiteralValue) / $(StringLiteral) #LiteralValue / $((~__ ['\\] [\x01-[\]-\xff] ['\\] { #CharLiteral } ~__)) / $(NumLiteral)) }))) / ($(ArrayConstruction) / $(Iterable)) / $(ObjConstruction)) ($(Invoke))* (~__ ("++" / "--"))? #Value }
   private static <T> boolean pValue(ParserContext<T> c) {
      int memo = c.memoLookupTree(25);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e136(c)) {
            c.memoTreeSucc(25,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(25);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Value)
   private static <T> boolean e184(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pValue(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(NestedExpr)
   private static <T> boolean e185(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pNestedExpr(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(Value) / $(NestedExpr)
   private static <T> boolean e188(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Value)
         if (e184(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(NestedExpr)
         if (e185(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~__ "..."
   private static <T> boolean e186(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text14)) {
         return false;
      }
      return true;
   }
   // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference }))
   private static <T> boolean e183(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(QualifiedName)
      if (!e145(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text75)) {
         return false;
      }
      {
         T left4 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      c.endTree(0,_TMethodReference,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value)
   private static <T> boolean e187(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference }))
         if (e183(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Value)
         if (e184(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // '+'
   private static <T> boolean e179(ParserContext<T> c) {
      if (c.read() != 43) {
         return false;
      }
      return true;
   }
   // '!'
   private static <T> boolean e178(ParserContext<T> c) {
      if (c.read() != 33) {
         return false;
      }
      return true;
   }
   // '-'
   private static <T> boolean e180(ParserContext<T> c) {
      if (c.read() != 45) {
         return false;
      }
      return true;
   }
   // '~'
   private static <T> boolean e181(ParserContext<T> c) {
      if (c.read() != 126) {
         return false;
      }
      return true;
   }
   // ~__ ('!' / '+' / '-' / '~')
   private static <T> boolean e177(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      boolean temp = true;
      switch(_index73[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // '!'
         temp = e178(c);
         break;
         case 2: 
         // '+'
         temp = e179(c);
         break;
         case 3: 
         // '-'
         temp = e180(c);
         break;
         case 4: 
         // '~'
         temp = e181(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $((~LP { $(TypeName) #Cast } ~RP))
   private static <T> boolean e176(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TCast,null);
      if (!p_RP(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~LP { $(TypeName) #Cast } ~RP)) / ~__ ('!' / '+' / '-' / '~')
   private static <T> boolean e175(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~LP { $(TypeName) #Cast } ~RP))
         if (e176(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         // ~__ ('!' / '+' / '-' / '~')
         if (e177(c)) {
            temp = false;
         } else {
            c.pos = pos4;
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ($((~LP { $(TypeName) #Cast } ~RP)) / ~__ ('!' / '+' / '-' / '~')) / ~__ ('!' / '+' / '-' / '~') / $((~LP { $(TypeName) #Cast } ~RP))
   private static <T> boolean e174(ParserContext<T> c) {
      boolean temp = true;
      switch(_index72[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $((~LP { $(TypeName) #Cast } ~RP)) / ~__ ('!' / '+' / '-' / '~')
         temp = e175(c);
         break;
         case 2: 
         // ~__ ('!' / '+' / '-' / '~')
         temp = e177(c);
         break;
         case 3: 
         // $((~LP { $(TypeName) #Cast } ~RP))
         temp = e176(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value) / $(NestedExpr) / ~__ "..."
   private static <T> boolean e182(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference }))
         if (e183(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Value)
         if (e184(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(NestedExpr)
         if (e185(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         // ~__ "..."
         if (e186(c)) {
            temp = false;
         } else {
            c.pos = pos10;
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($((~LP { $(TypeName) #Cast } ~RP)) / ~__ ('!' / '+' / '-' / '~')) / ~__ ('!' / '+' / '-' / '~') / $((~LP { $(TypeName) #Cast } ~RP)))* (($(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value) / $(NestedExpr) / ~__ "...") / $(Value) / ($(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value)) / ($(Value) / $(NestedExpr)) / ~__ "..." / $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference }))) #Product }
   private static <T> boolean pProduct(ParserContext<T> c) {
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // ($((~LP { $(TypeName) #Cast } ~RP)) / ~__ ('!' / '+' / '-' / '~')) / ~__ ('!' / '+' / '-' / '~') / $((~LP { $(TypeName) #Cast } ~RP))
         if (!e174(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      boolean temp = true;
      switch(_index74[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value) / $(NestedExpr) / ~__ "..."
         temp = e182(c);
         break;
         case 2: 
         // $(Value)
         temp = e184(c);
         break;
         case 3: 
         // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference })) / $(Value)
         temp = e187(c);
         break;
         case 4: 
         // $(Value) / $(NestedExpr)
         temp = e188(c);
         break;
         case 5: 
         // ~__ "..."
         temp = e186(c);
         break;
         case 6: 
         // $(({ ($(QualifiedName))? ~__ "::" $(Name) #MethodReference }))
         temp = e183(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_TProduct,null);
      return true;
   }
   // ~__ [%*/] $(Product)
   private static <T> boolean e189(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!_set76[c.read()]) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pProduct(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Product) (~__ [%*/] $(Product))* #Sum }
   private static <T> boolean pSum(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pProduct(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ [%*/] $(Product)
         if (!e189(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TSum,null);
      return true;
   }
   // ~__ [+\-] $(Sum)
   private static <T> boolean e190(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!_set37[c.read()]) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pSum(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Sum) (~__ [+\-] $(Sum))* #Shift }
   private static <T> boolean pShift(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pSum(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ [+\-] $(Sum)
         if (!e190(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TShift,null);
      return true;
   }
   // ">>"
   private static <T> boolean e193(ParserContext<T> c) {
      if (!c.match(_text79)) {
         return false;
      }
      return true;
   }
   // "<<"
   private static <T> boolean e192(ParserContext<T> c) {
      if (!c.match(_text78)) {
         return false;
      }
      return true;
   }
   // ~__ ("<<" / ">>") $(Shift)
   private static <T> boolean e191(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      boolean temp = true;
      switch(_index77[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "<<"
         temp = e192(c);
         break;
         case 2: 
         // ">>"
         temp = e193(c);
         break;
      }
      if (!temp) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pShift(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Shift) (~__ ("<<" / ">>") $(Shift))* #Compare }
   private static <T> boolean pCompare(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pShift(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ ("<<" / ">>") $(Shift)
         if (!e191(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TCompare,null);
      return true;
   }
   // $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
   private static <T> boolean e201(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text83)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypeConstructor(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TTypeCheck,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // '<'
   private static <T> boolean e199(ParserContext<T> c) {
      if (c.read() != 60) {
         return false;
      }
      return true;
   }
   // '>'
   private static <T> boolean e200(ParserContext<T> c) {
      if (c.read() != 62) {
         return false;
      }
      return true;
   }
   // "<="
   private static <T> boolean e197(ParserContext<T> c) {
      if (!c.match(_text81)) {
         return false;
      }
      return true;
   }
   // ">="
   private static <T> boolean e198(ParserContext<T> c) {
      if (!c.match(_text82)) {
         return false;
      }
      return true;
   }
   // { ~__ ("<=" / ">=" / '<' / '>') $(Compare) #Comparison }
   private static <T> boolean pComparison(ParserContext<T> c) {
      c.beginTree(0);
      if (!p___(c)) {
         return false;
      }
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            // "<="
            if (e197(c)) {
               temp = false;
            } else {
               c.pos = pos;
            }
         }
         if (temp) {
            int pos2 = c.pos;
            // ">="
            if (e198(c)) {
               temp = false;
            } else {
               c.pos = pos2;
            }
         }
         if (temp) {
            int pos3 = c.pos;
            // '<'
            if (e199(c)) {
               temp = false;
            } else {
               c.pos = pos3;
            }
         }
         if (temp) {
            int pos4 = c.pos;
            // '>'
            if (e200(c)) {
               temp = false;
            } else {
               c.pos = pos4;
            }
         }
         if (temp) {
            return false;
         }
      }
      {
         T left = c.saveTree();
         if (!pCompare(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TComparison,null);
      return true;
   }
   // $(Comparison)
   private static <T> boolean e196(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pComparison(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
   private static <T> boolean e195(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Comparison)
         if (e196(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
         if (e201(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ($(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))) / $(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
   private static <T> boolean e194(ParserContext<T> c) {
      boolean temp = true;
      switch(_index80[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
         temp = e195(c);
         break;
         case 2: 
         // $(Comparison)
         temp = e196(c);
         break;
         case 3: 
         // $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
         temp = e201(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // { $(Compare) (($(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))) / $(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck })))? #Equality }
   private static <T> boolean pEquality(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pCompare(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ($(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))) / $(Comparison) / $((~__ "isA" { $(TypeConstructor) #TypeCheck }))
      if (!e194(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TEquality,null);
      return true;
   }
   // "!="
   private static <T> boolean e203(ParserContext<T> c) {
      if (!c.match(_text85)) {
         return false;
      }
      return true;
   }
   // "=="
   private static <T> boolean e204(ParserContext<T> c) {
      if (!c.match(_text86)) {
         return false;
      }
      return true;
   }
   // ~__ ("!=" / "==") $(Equality)
   private static <T> boolean e202(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      boolean temp = true;
      switch(_index84[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // "!="
         temp = e203(c);
         break;
         case 2: 
         // "=="
         temp = e204(c);
         break;
      }
      if (!temp) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pEquality(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Equality) (~__ ("!=" / "==") $(Equality))? #BitwiseAnd }
   private static <T> boolean pBitwiseAnd(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pEquality(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ~__ ("!=" / "==") $(Equality)
      if (!e202(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TBitwiseAnd,null);
      return true;
   }
   // ~BitAnd $(BitwiseAnd)
   private static <T> boolean e205(ParserContext<T> c) {
      if (!p_BitAnd(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pBitwiseAnd(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(BitwiseAnd) (~BitAnd $(BitwiseAnd))* #BitwiseXor }
   private static <T> boolean pBitwiseXor(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pBitwiseAnd(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~BitAnd $(BitwiseAnd)
         if (!e205(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TBitwiseXor,null);
      return true;
   }
   // ~__ '^' $(BitwiseXor)
   private static <T> boolean e206(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 94) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pBitwiseXor(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(BitwiseXor) (~__ '^' $(BitwiseXor))* #BitwiseOr }
   private static <T> boolean pBitwiseOr(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pBitwiseXor(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ '^' $(BitwiseXor)
         if (!e206(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TBitwiseOr,null);
      return true;
   }
   // ~BitOr $(BitwiseOr)
   private static <T> boolean e207(ParserContext<T> c) {
      if (!p_BitOr(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pBitwiseOr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(BitwiseOr) (~BitOr $(BitwiseOr))* #And }
   private static <T> boolean pAnd(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pBitwiseOr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~BitOr $(BitwiseOr)
         if (!e207(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TAnd,null);
      return true;
   }
   // ~__ "&&" $(And)
   private static <T> boolean e208(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text87)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pAnd(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(And) (~__ "&&" $(And))* #Or }
   private static <T> boolean pOr(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pAnd(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ "&&" $(And)
         if (!e208(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TOr,null);
      return true;
   }
   // ~__ "||" $(Or)
   private static <T> boolean e209(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text88)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pOr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Or) (~__ "||" $(Or))* #Expr }
   private static <T> boolean e173(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pOr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~__ "||" $(Or)
         if (!e209(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TExpr,null);
      return true;
   }
   // { $(Or) (~__ "||" $(Or))* #Expr }
   private static <T> boolean pExpr(ParserContext<T> c) {
      int memo = c.memoLookupTree(19);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e173(c)) {
            c.memoTreeSucc(19,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(19);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Expr) #Expression
   private static <T> boolean e172(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pExpr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TExpression);
      return true;
   }
   // $(MatchExpression) / $(Expr) #Expression
   private static <T> boolean e212(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MatchExpression)
         if (e99(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Expr) #Expression
         if (e172(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(SelectExpression) / $(Expr) #Expression
   private static <T> boolean e213(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(SelectExpression)
         if (e48(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Expr) #Expression
         if (e172(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { $(NestedExpr) ~__ '?' $(Expression) ~Colon $(Expression) #TernaryExpression }
   private static <T> boolean pTernaryExpression(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pNestedExpr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 63) {
         return false;
      }
      {
         T left1 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      if (!p_Colon(c)) {
         return false;
      }
      {
         T left2 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      c.endTree(0,_TTernaryExpression,null);
      return true;
   }
   // $(TernaryExpression)
   private static <T> boolean e171(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTernaryExpression(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(TernaryExpression) / $(Expr) #Expression
   private static <T> boolean e210(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(TernaryExpression)
         if (e171(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Expr) #Expression
         if (e172(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(IfExpression) / $(Expr) #Expression
   private static <T> boolean e211(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(IfExpression)
         if (e35(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Expr) #Expression
         if (e172(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(TernaryExpression) / $(Expr) #Expression
   private static <T> boolean e34(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(IfExpression)
         if (e35(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(SelectExpression)
         if (e48(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(MatchExpression)
         if (e99(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(TernaryExpression)
         if (e171(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         int pos13 = c.pos;
         T left14 = c.saveTree();
         int log15 = c.saveLog();
         // $(Expr) #Expression
         if (e172(c)) {
            temp = false;
         } else {
            c.pos = pos13;
            c.backTree(left14);
            c.backLog(log15);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(TernaryExpression) / $(Expr) #Expression) / $(Expr) #Expression / ($(TernaryExpression) / $(Expr) #Expression) / ($(IfExpression) / $(Expr) #Expression) / ($(MatchExpression) / $(Expr) #Expression) / ($(SelectExpression) / $(Expr) #Expression)) }
   private static <T> boolean e33(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index16[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(TernaryExpression) / $(Expr) #Expression
         temp = e34(c);
         break;
         case 2: 
         // $(Expr) #Expression
         temp = e172(c);
         break;
         case 3: 
         // $(TernaryExpression) / $(Expr) #Expression
         temp = e210(c);
         break;
         case 4: 
         // $(IfExpression) / $(Expr) #Expression
         temp = e211(c);
         break;
         case 5: 
         // $(MatchExpression) / $(Expr) #Expression
         temp = e212(c);
         break;
         case 6: 
         // $(SelectExpression) / $(Expr) #Expression
         temp = e213(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($(IfExpression) / $(SelectExpression) / $(MatchExpression) / $(TernaryExpression) / $(Expr) #Expression) / $(Expr) #Expression / ($(TernaryExpression) / $(Expr) #Expression) / ($(IfExpression) / $(Expr) #Expression) / ($(MatchExpression) / $(Expr) #Expression) / ($(SelectExpression) / $(Expr) #Expression)) }
   private static <T> boolean pExpression(ParserContext<T> c) {
      int memo = c.memoLookupTree(3);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e33(c)) {
            c.memoTreeSucc(3,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(3);
            return false;
         }
      }
      return memo == 1;
   }
   // "&="
   private static <T> boolean e117(ParserContext<T> c) {
      if (!c.match(_text61)) {
         return false;
      }
      return true;
   }
   // '='
   private static <T> boolean e118(ParserContext<T> c) {
      if (c.read() != 61) {
         return false;
      }
      return true;
   }
   // "^="
   private static <T> boolean e115(ParserContext<T> c) {
      if (!c.match(_text59)) {
         return false;
      }
      return true;
   }
   // "|="
   private static <T> boolean e116(ParserContext<T> c) {
      if (!c.match(_text60)) {
         return false;
      }
      return true;
   }
   // { $(QualifiedName) ~__ ("+=" / "-=" / "^=" / "|=" / "&=" / '=') $(Expression) #RegularAssignment }
   private static <T> boolean pRegularAssignment(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pQualifiedName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p___(c)) {
         return false;
      }
      {
         boolean temp = true;
         if (temp) {
            int pos = c.pos;
            // "+="
            if (e113(c)) {
               temp = false;
            } else {
               c.pos = pos;
            }
         }
         if (temp) {
            int pos3 = c.pos;
            // "-="
            if (e114(c)) {
               temp = false;
            } else {
               c.pos = pos3;
            }
         }
         if (temp) {
            int pos4 = c.pos;
            // "^="
            if (e115(c)) {
               temp = false;
            } else {
               c.pos = pos4;
            }
         }
         if (temp) {
            int pos5 = c.pos;
            // "|="
            if (e116(c)) {
               temp = false;
            } else {
               c.pos = pos5;
            }
         }
         if (temp) {
            int pos6 = c.pos;
            // "&="
            if (e117(c)) {
               temp = false;
            } else {
               c.pos = pos6;
            }
         }
         if (temp) {
            int pos7 = c.pos;
            // '='
            if (e118(c)) {
               temp = false;
            } else {
               c.pos = pos7;
            }
         }
         if (temp) {
            return false;
         }
      }
      {
         T left8 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left8);
      }
      c.endTree(0,_TRegularAssignment,null);
      return true;
   }
   // $(RegularAssignment)
   private static <T> boolean e112(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pRegularAssignment(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { $(AssignmentExpr) #Assignment } ~Semicolon
   private static <T> boolean e111(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pAssignmentExpr(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TAssignment,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // { $(AssignmentExpr) #Assignment } ~Semicolon
   private static <T> boolean pAssignment(ParserContext<T> c) {
      int memo = c.memoLookupTree(8);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e111(c)) {
            c.memoTreeSucc(8,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(8);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Assignment)
   private static <T> boolean e123(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pAssignment(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(SelectExpression) / $(VariableDeclaration) / $(Assignment) / $(ValueStatement) #Statement
   private static <T> boolean e168(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(SelectExpression)
         if (e48(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(VariableDeclaration)
         if (e109(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(Assignment)
         if (e123(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(ValueStatement) #Statement
         if (e134(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon))
   private static <T> boolean e243(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 61) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TMethodExpression,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(MethodBlockBody) #MethodBody
   private static <T> boolean e244(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pMethodBlockBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.tagTree(_TMethodBody);
      return true;
   }
   // $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody
   private static <T> boolean e242(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon))
         if (e243(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(MethodBlockBody) #MethodBody
         if (e244(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { (($((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody) / $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody) }
   private static <T> boolean e241(ParserContext<T> c) {
      c.beginTree(0);
      boolean temp = true;
      switch(_index96[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody
         temp = e242(c);
         break;
         case 2: 
         // $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon))
         temp = e243(c);
         break;
         case 3: 
         // $(MethodBlockBody) #MethodBody
         temp = e244(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_T,null);
      return true;
   }
   // { (($((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody) / $((~__ '=' { $(Expression) #MethodExpression } ~Semicolon)) / $(MethodBlockBody) #MethodBody) }
   private static <T> boolean pMethodBody(ParserContext<T> c) {
      int memo = c.memoLookupTree(17);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e241(c)) {
            c.memoTreeSucc(17,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(17);
            return false;
         }
      }
      return memo == 1;
   }
   // ~__ "pub" { #Public }
   private static <T> boolean e22(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text10)) {
         return false;
      }
      c.beginTree(-3);
      c.endTree(0,_TPublic,null);
      return true;
   }
   // ~__ "pub" { #Public }
   private static <T> boolean pPublic(ParserContext<T> c) {
      int memo = c.memoLookupTree(13);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e22(c)) {
            c.memoTreeSucc(13,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(13);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Public)
   private static <T> boolean e21(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pPublic(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ ($(Public))? ~__ '#' $(Name) ~LP ~RP $(MethodBody) #Destructor }))
   private static <T> boolean e254(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 35) {
         return false;
      }
      {
         T left4 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      if (!p_LP(c)) {
         return false;
      }
      if (!p_RP(c)) {
         return false;
      }
      {
         T left5 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left5);
      }
      c.endTree(0,_TDestructor,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~Arrow ~Ellipsis? $(TypeName)
   private static <T> boolean e234(ParserContext<T> c) {
      if (!p_Arrow(c)) {
         return false;
      }
      int pos = c.pos;
      // ~Ellipsis
      if (!p_Ellipsis(c)) {
         c.pos = pos;
      }
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(TypeName) (~Arrow ~Ellipsis? $(TypeName))* #ArgumentType }
   private static <T> boolean e233(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // ~Arrow ~Ellipsis? $(TypeName)
         if (!e234(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TArgumentType,null);
      return true;
   }
   // { $(TypeName) (~Arrow ~Ellipsis? $(TypeName))* #ArgumentType }
   private static <T> boolean pArgumentType(ParserContext<T> c) {
      int memo = c.memoLookupTree(27);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e233(c)) {
            c.memoTreeSucc(27,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(27);
            return false;
         }
      }
      return memo == 1;
   }
   // { $(ArgumentType) $(Name) #TypedArgument }
   private static <T> boolean e232(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pArgumentType(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      {
         T left1 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TTypedArgument,null);
      return true;
   }
   // { $(ArgumentType) $(Name) #TypedArgument }
   private static <T> boolean pTypedArgument(ParserContext<T> c) {
      int memo = c.memoLookupTree(48);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e232(c)) {
            c.memoTreeSucc(48,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(48);
            return false;
         }
      }
      return memo == 1;
   }
   // ~Comma $(TypedArgument)
   private static <T> boolean e235(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pTypedArgument(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // $(({ $(TypedArgument) (~Comma $(TypedArgument))* ($(OptionalEllipsis))? #TypedArgList }))
   private static <T> boolean e231(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypedArgument(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(TypedArgument)
         if (!e235(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      int pos5 = c.pos;
      T left6 = c.saveTree();
      int log7 = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos5;
         c.backTree(left6);
         c.backLog(log7);
      }
      c.endTree(0,_TTypedArgList,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LP { ($(({ $(TypedArgument) (~Comma $(TypedArgument))* ($(OptionalEllipsis))? #TypedArgList })))? #TypedArgDecl } ~RP
   private static <T> boolean e230(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(({ $(TypedArgument) (~Comma $(TypedArgument))* ($(OptionalEllipsis))? #TypedArgList }))
      if (!e231(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TTypedArgDecl,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~LP { ($(({ $(TypedArgument) (~Comma $(TypedArgument))* ($(OptionalEllipsis))? #TypedArgList })))? #TypedArgDecl } ~RP
   private static <T> boolean pTypedArgDecl(ParserContext<T> c) {
      int memo = c.memoLookupTree(20);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e230(c)) {
            c.memoTreeSucc(20,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(20);
            return false;
         }
      }
      return memo == 1;
   }
   // $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction }))
   private static <T> boolean e229(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      {
         T left2 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      {
         T left3 = c.saveTree();
         if (!pTypedArgDecl(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TTypedFunction,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(NameList)
   private static <T> boolean e238(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pNameList(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LP { ($(NameList))? #OptionalNameList } ~RP
   private static <T> boolean e237(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(NameList)
      if (!e238(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      c.endTree(0,_TOptionalNameList,null);
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~LP { ($(NameList))? #OptionalNameList } ~RP
   private static <T> boolean pOptionalNameList(ParserContext<T> c) {
      int memo = c.memoLookupTree(49);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e237(c)) {
            c.memoTreeSucc(49,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(49);
            return false;
         }
      }
      return memo == 1;
   }
   // $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))
   private static <T> boolean e236(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text94)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      {
         T left2 = c.saveTree();
         if (!pOptionalNameList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      c.endTree(0,_TUntypedFunction,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction })) / $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))
   private static <T> boolean e228(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction }))
         if (e229(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))
         if (e236(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~Colon $(TypeName) [+\-]?
   private static <T> boolean e226(ParserContext<T> c) {
      if (!p_Colon(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (_set37[c.prefetch()]) {
         c.move(1);
      }
      return true;
   }
   // { $(Name) (~Colon $(TypeName) [+\-]?)? #LimitedTypeName }
   private static <T> boolean pLimitedTypeName(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ~Colon $(TypeName) [+\-]?
      if (!e226(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TLimitedTypeName,null);
      return true;
   }
   // ~Comma $(LimitedTypeName)
   private static <T> boolean e227(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pLimitedTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // $((~__ '<' { $(LimitedTypeName) (~Comma $(LimitedTypeName))* #TypeVarsDecl } ~__ '>'))
   private static <T> boolean e225(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 60) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pLimitedTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(LimitedTypeName)
         if (!e227(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TTypeVarsDecl,null);
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 62) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($((~__ '<' { $(LimitedTypeName) (~Comma $(LimitedTypeName))* #TypeVarsDecl } ~__ '>')))? (($(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction })) / $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))) / $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction }))) #MethodSignature }
   private static <T> boolean e224(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $((~__ '<' { $(LimitedTypeName) (~Comma $(LimitedTypeName))* #TypeVarsDecl } ~__ '>'))
      if (!e225(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      boolean temp = true;
      switch(_index93[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction })) / $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))
         temp = e228(c);
         break;
         case 2: 
         // $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction }))
         temp = e229(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_TMethodSignature,null);
      return true;
   }
   // { ($((~__ '<' { $(LimitedTypeName) (~Comma $(LimitedTypeName))* #TypeVarsDecl } ~__ '>')))? (($(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction })) / $((~__ "fn" { $(Name) $(OptionalNameList) #UntypedFunction }))) / $(({ $(TypeName) $(Name) $(TypedArgDecl) #TypedFunction }))) #MethodSignature }
   private static <T> boolean pMethodSignature(ParserContext<T> c) {
      int memo = c.memoLookupTree(16);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e224(c)) {
            c.memoTreeSucc(16,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(16);
            return false;
         }
      }
      return memo == 1;
   }
   // $(ExpressionList)
   private static <T> boolean e217(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pExpressionList(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~LP ($(ExpressionList))? ~RP
   private static <T> boolean e216(ParserContext<T> c) {
      if (!p_LP(c)) {
         return false;
      }
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(ExpressionList)
      if (!e217(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      if (!p_RP(c)) {
         return false;
      }
      return true;
   }
   // ~__ '@' { $(Name) (~LP ($(ExpressionList))? ~RP)? #AnnotationRef }
   private static <T> boolean pAnnotationRef(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 64) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ~LP ($(ExpressionList))? ~RP
      if (!e216(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TAnnotationRef,null);
      return true;
   }
   // $(AnnotationRef)
   private static <T> boolean e215(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pAnnotationRef(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(AnnotationRef))* ($(Public))? $(MethodSignature) $(MethodBody) #Function }
   private static <T> boolean e258(ParserContext<T> c) {
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(AnnotationRef)
         if (!e215(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      {
         T left6 = c.saveTree();
         if (!pMethodSignature(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left6);
      }
      {
         T left7 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left7);
      }
      c.endTree(0,_TFunction,null);
      return true;
   }
   // { ($(AnnotationRef))* ($(Public))? $(MethodSignature) $(MethodBody) #Function }
   private static <T> boolean pFunction(ParserContext<T> c) {
      int memo = c.memoLookupTree(28);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e258(c)) {
            c.memoTreeSucc(28,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(28);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Function)
   private static <T> boolean e257(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pFunction(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(Public))? ~__ "static" $(MethodSignature) $(MethodBody) #StaticMethodImpl }
   private static <T> boolean e240(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text95)) {
         return false;
      }
      {
         T left3 = c.saveTree();
         if (!pMethodSignature(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      {
         T left4 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      c.endTree(0,_TStaticMethodImpl,null);
      return true;
   }
   // { ($(Public))? ~__ "static" $(MethodSignature) $(MethodBody) #StaticMethodImpl }
   private static <T> boolean pStaticMethodImpl(ParserContext<T> c) {
      int memo = c.memoLookupTree(35);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e240(c)) {
            c.memoTreeSucc(35,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(35);
            return false;
         }
      }
      return memo == 1;
   }
   // $(StaticMethodImpl)
   private static <T> boolean e239(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pStaticMethodImpl(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
   private static <T> boolean e259(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text99)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      {
         T left2 = c.saveTree();
         if (!pOptionalNameList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      {
         T left3 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TMethodImpl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
   private static <T> boolean e256(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(StaticMethodImpl)
         if (e239(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Function)
         if (e257(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
         if (e259(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(StaticMethodImpl) / $(Function)
   private static <T> boolean e261(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(StaticMethodImpl)
         if (e239(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(Function)
         if (e257(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
   private static <T> boolean e260(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Function)
         if (e257(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
         if (e259(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ($(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / $(Function) / ($(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / ($(StaticMethodImpl) / $(Function))
   private static <T> boolean e255(ParserContext<T> c) {
      boolean temp = true;
      switch(_index98[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
         temp = e256(c);
         break;
         case 2: 
         // $(Function)
         temp = e257(c);
         break;
         case 3: 
         // $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))
         temp = e260(c);
         break;
         case 4: 
         // $(StaticMethodImpl) / $(Function)
         temp = e261(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // $(({ ($(Public))? $(Name) $(TypedArgDecl) $(MethodBody) #Constructor }))
   private static <T> boolean e253(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      {
         T left4 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      {
         T left5 = c.saveTree();
         if (!pTypedArgDecl(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left5);
      }
      {
         T left6 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left6);
      }
      c.endTree(0,_TConstructor,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "val"
   private static <T> boolean e24(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text13)) {
         return false;
      }
      return true;
   }
   // ~__ "val" / $(TypeName)
   private static <T> boolean e23(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         // ~__ "val"
         if (e24(c)) {
            temp = false;
         } else {
            c.pos = pos;
         }
      }
      if (temp) {
         int pos2 = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(TypeName)
         if (e25(c)) {
            temp = false;
         } else {
            c.pos = pos2;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // { ($(Public))? ~__ "const" ((~__ "val" / $(TypeName)) / $(TypeName)) $(Name) ~__ '=' $(Expression) #Constant } ~Semicolon
   private static <T> boolean e20(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text11)) {
         return false;
      }
      boolean temp = true;
      switch(_index12[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // ~__ "val" / $(TypeName)
         temp = e23(c);
         break;
         case 2: 
         // $(TypeName)
         temp = e25(c);
         break;
      }
      if (!temp) {
         return false;
      }
      {
         T left4 = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 61) {
         return false;
      }
      {
         T left5 = c.saveTree();
         if (!pExpression(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left5);
      }
      c.endTree(0,_TConstant,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // { ($(Public))? ~__ "const" ((~__ "val" / $(TypeName)) / $(TypeName)) $(Name) ~__ '=' $(Expression) #Constant } ~Semicolon
   private static <T> boolean pConstant(ParserContext<T> c) {
      int memo = c.memoLookupTree(41);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e20(c)) {
            c.memoTreeSucc(41,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(41);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Constant)
   private static <T> boolean e19(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pConstant(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~Colon { $(TypeNameList) #ApiRefs }
   private static <T> boolean e251(ParserContext<T> c) {
      if (!p_Colon(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pTypeNameList(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      c.endTree(0,_TApiRefs,null);
      return true;
   }
   // ~Colon { $(TypeNameList) #ApiRefs }
   private static <T> boolean pApiRefs(ParserContext<T> c) {
      int memo = c.memoLookupTree(4);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e251(c)) {
            c.memoTreeSucc(4,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(4);
            return false;
         }
      }
      return memo == 1;
   }
   // $(ApiRefs)
   private static <T> boolean e250(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pApiRefs(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(TypedArgDecl)
   private static <T> boolean e249(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTypedArgDecl(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(TypedArgDecl))? ($(ApiRefs))? $(ClassBlock) #ClassBody }
   private static <T> boolean pClassBody(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(TypedArgDecl)
      if (!e249(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(ApiRefs)
      if (!e250(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      {
         T left6 = c.saveTree();
         if (!pClassBlock(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left6);
      }
      c.endTree(0,_TClassBody,null);
      return true;
   }
   // $(NamedClassDecl)
   private static <T> boolean e248(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pNamedClassDecl(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~BlockStart { ($(Constant))* ($(({ ($(Public))? $(Name) $(TypedArgDecl) $(MethodBody) #Constructor })))* ($(({ ($(Public))? ~__ '#' $(Name) ~LP ~RP $(MethodBody) #Destructor })))? (($(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / $(Function) / ($(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / ($(StaticMethodImpl) / $(Function)))* ($(NamedClassDecl))* #ClassBlock } ~BlockEnd
   private static <T> boolean e252(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Constant)
         if (!e19(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      while (true) {
         int pos3 = c.pos;
         T left4 = c.saveTree();
         int log5 = c.saveLog();
         // $(({ ($(Public))? $(Name) $(TypedArgDecl) $(MethodBody) #Constructor }))
         if (!e253(c)) {
            c.pos = pos3;
            c.backTree(left4);
            c.backLog(log5);
            break;
         }
      }
      int pos6 = c.pos;
      T left7 = c.saveTree();
      int log8 = c.saveLog();
      // $(({ ($(Public))? ~__ '#' $(Name) ~LP ~RP $(MethodBody) #Destructor }))
      if (!e254(c)) {
         c.pos = pos6;
         c.backTree(left7);
         c.backLog(log8);
      }
      while (true) {
         int pos9 = c.pos;
         T left10 = c.saveTree();
         int log11 = c.saveLog();
         // ($(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / $(Function) / ($(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / ($(StaticMethodImpl) / $(Function))
         if (!e255(c)) {
            c.pos = pos9;
            c.backTree(left10);
            c.backLog(log11);
            break;
         }
         if (pos9 == c.pos) {
            break;
         }
      }
      while (true) {
         int pos12 = c.pos;
         T left13 = c.saveTree();
         int log14 = c.saveLog();
         // $(NamedClassDecl)
         if (!e248(c)) {
            c.pos = pos12;
            c.backTree(left13);
            c.backLog(log14);
            break;
         }
      }
      c.endTree(0,_TClassBlock,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // ~BlockStart { ($(Constant))* ($(({ ($(Public))? $(Name) $(TypedArgDecl) $(MethodBody) #Constructor })))* ($(({ ($(Public))? ~__ '#' $(Name) ~LP ~RP $(MethodBody) #Destructor })))? (($(StaticMethodImpl) / $(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / $(Function) / ($(Function) / $((~__ "impl" { $(Name) $(OptionalNameList) $(MethodBody) #MethodImpl }))) / ($(StaticMethodImpl) / $(Function)))* ($(NamedClassDecl))* #ClassBlock } ~BlockEnd
   private static <T> boolean pClassBlock(ParserContext<T> c) {
      int memo = c.memoLookupTree(36);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e252(c)) {
            c.memoTreeSucc(36,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(36);
            return false;
         }
      }
      return memo == 1;
   }
   // { ($(Public))? ~__ "class" $(TypeName) $(ClassBody) #NamedClassDecl }
   private static <T> boolean pNamedClassDecl(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text97)) {
         return false;
      }
      {
         T left3 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      {
         T left4 = c.saveTree();
         if (!pClassBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      c.endTree(0,_TNamedClassDecl,null);
      return true;
   }
   // $(({ ($(Public))? ~__ "extend" $(TypeName) ($(ApiRefs))? $(ClassBlock) #Extension }))
   private static <T> boolean e282(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text104)) {
         return false;
      }
      {
         T left4 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      int pos5 = c.pos;
      T left6 = c.saveTree();
      int log7 = c.saveLog();
      // $(ApiRefs)
      if (!e250(c)) {
         c.pos = pos5;
         c.backTree(left6);
         c.backLog(log7);
      }
      {
         T left8 = c.saveTree();
         if (!pClassBlock(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left8);
      }
      c.endTree(0,_TExtension,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon))
   private static <T> boolean e263(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pArgumentType(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      c.endTree(0,_TTypeAliasDecl,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~BitOr $(TypeName)
   private static <T> boolean e268(ParserContext<T> c) {
      if (!p_BitOr(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))
   private static <T> boolean e267(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      if (!e268(c)) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~BitOr $(TypeName)
         if (!e268(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TOrType,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ $(TypeName) (~Comma $(TypeName))+ #AndType }))
   private static <T> boolean e266(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      if (!e29(c)) {
         return false;
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(TypeName)
         if (!e29(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TAndType,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType })))
   private static <T> boolean e265(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ $(TypeName) (~Comma $(TypeName))+ #AndType }))
         if (e266(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))
         if (e267(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(MethodSignature) ~Semicolon
   private static <T> boolean e223(ParserContext<T> c) {
      {
         T left = c.saveTree();
         if (!pMethodSignature(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      if (!p_Semicolon(c)) {
         return false;
      }
      return true;
   }
   // $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
   private static <T> boolean e245(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text51)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pMethodSignature(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      {
         T left2 = c.saveTree();
         if (!pMethodBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      c.endTree(0,_TDefaultMethodImpl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
   private static <T> boolean e246(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MethodSignature) ~Semicolon
         if (e223(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
         if (e245(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
   private static <T> boolean e222(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MethodSignature) ~Semicolon
         if (e223(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(StaticMethodImpl)
         if (e239(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
         if (e245(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(MethodSignature) ~Semicolon / $(StaticMethodImpl)
   private static <T> boolean e247(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(MethodSignature) ~Semicolon
         if (e223(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(StaticMethodImpl)
         if (e239(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ($(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / $(MethodSignature) ~Semicolon / ($(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / ($(MethodSignature) ~Semicolon / $(StaticMethodImpl))
   private static <T> boolean e221(ParserContext<T> c) {
      boolean temp = true;
      switch(_index92[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // $(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
         temp = e222(c);
         break;
         case 2: 
         // $(MethodSignature) ~Semicolon
         temp = e223(c);
         break;
         case 3: 
         // $(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))
         temp = e246(c);
         break;
         case 4: 
         // $(MethodSignature) ~Semicolon / $(StaticMethodImpl)
         temp = e247(c);
         break;
      }
      if (!temp) {
         return false;
      }
      return true;
   }
   // ~BlockStart { ($(Constant))* (($(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / $(MethodSignature) ~Semicolon / ($(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / ($(MethodSignature) ~Semicolon / $(StaticMethodImpl)))* ($(NamedClassDecl))* #CommonApiBody } ~BlockEnd
   private static <T> boolean e220(ParserContext<T> c) {
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Constant)
         if (!e19(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      while (true) {
         int pos3 = c.pos;
         T left4 = c.saveTree();
         int log5 = c.saveLog();
         // ($(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / $(MethodSignature) ~Semicolon / ($(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / ($(MethodSignature) ~Semicolon / $(StaticMethodImpl))
         if (!e221(c)) {
            c.pos = pos3;
            c.backTree(left4);
            c.backLog(log5);
            break;
         }
      }
      while (true) {
         int pos6 = c.pos;
         T left7 = c.saveTree();
         int log8 = c.saveLog();
         // $(NamedClassDecl)
         if (!e248(c)) {
            c.pos = pos6;
            c.backTree(left7);
            c.backLog(log8);
            break;
         }
      }
      c.endTree(0,_TCommonApiBody,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // ~BlockStart { ($(Constant))* (($(MethodSignature) ~Semicolon / $(StaticMethodImpl) / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / $(MethodSignature) ~Semicolon / ($(MethodSignature) ~Semicolon / $((~__ "default" { $(MethodSignature) $(MethodBody) #DefaultMethodImpl }))) / ($(MethodSignature) ~Semicolon / $(StaticMethodImpl)))* ($(NamedClassDecl))* #CommonApiBody } ~BlockEnd
   private static <T> boolean pCommonApiBody(ParserContext<T> c) {
      int memo = c.memoLookupTree(6);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e220(c)) {
            c.memoTreeSucc(6,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(6);
            return false;
         }
      }
      return memo == 1;
   }
   // $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))
   private static <T> boolean e270(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(ApiRefs)
      if (!e250(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      {
         T left4 = c.saveTree();
         if (!pCommonApiBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left4);
      }
      c.endTree(0,_TCommonApiDecl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))
   private static <T> boolean e269(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         // ~__ ';' ~__
         if (e16(c)) {
            temp = false;
         } else {
            c.pos = pos;
         }
      }
      if (temp) {
         int pos2 = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))
         if (e270(c)) {
            temp = false;
         } else {
            c.pos = pos2;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
   private static <T> boolean e264(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType })))
      if (!e265(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      boolean temp = true;
      switch(_index102[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // ~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))
         temp = e269(c);
         break;
         case 2: 
         // $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))
         temp = e270(c);
         break;
         case 3: 
         // ~__ ';' ~__
         temp = e16(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_TCompoundTypeDecl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
   private static <T> boolean e279(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon))
         if (e263(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         if (e264(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(ClassBlock)
   private static <T> boolean e278(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pClassBlock(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ ~__ "var" #Mutable }))
   private static <T> boolean e276(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text103)) {
         return false;
      }
      c.endTree(0,_TMutable,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(({ ~__ "var" #Mutable })))? $(TypedArgument) #ClassField }
   private static <T> boolean e275(ParserContext<T> c) {
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(({ ~__ "var" #Mutable }))
      if (!e276(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      {
         T left3 = c.saveTree();
         if (!pTypedArgument(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left3);
      }
      c.endTree(0,_TClassField,null);
      return true;
   }
   // { ($(({ ~__ "var" #Mutable })))? $(TypedArgument) #ClassField }
   private static <T> boolean pClassField(ParserContext<T> c) {
      int memo = c.memoLookupTree(37);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e275(c)) {
            c.memoTreeSucc(37,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(37);
            return false;
         }
      }
      return memo == 1;
   }
   // ~Comma $(ClassField)
   private static <T> boolean e277(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pClassField(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // $(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields }))
   private static <T> boolean e274(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pClassField(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(ClassField)
         if (!e277(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      int pos5 = c.pos;
      T left6 = c.saveTree();
      int log7 = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos5;
         c.backTree(left6);
         c.backLog(log7);
      }
      c.endTree(0,_TClassFields,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP))
   private static <T> boolean e273(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields }))
      if (!e274(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TClassDataDecl,null);
      if (!p_RP(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
   private static <T> boolean e272(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text97)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP))
      if (!e273(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      int pos4 = c.pos;
      T left5 = c.saveTree();
      int log6 = c.saveLog();
      // $(ApiRefs)
      if (!e250(c)) {
         c.pos = pos4;
         c.backTree(left5);
         c.backLog(log6);
      }
      {
         boolean temp = true;
         if (temp) {
            int pos8 = c.pos;
            T left9 = c.saveTree();
            int log10 = c.saveLog();
            // $(ClassBlock)
            if (e278(c)) {
               temp = false;
            } else {
               c.pos = pos8;
               c.backTree(left9);
               c.backLog(log10);
            }
         }
         if (temp) {
            int pos11 = c.pos;
            // ~__ ';' ~__
            if (e16(c)) {
               temp = false;
            } else {
               c.pos = pos11;
            }
         }
         if (temp) {
            return false;
         }
      }
      c.endTree(0,_TClassDecl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
   private static <T> boolean e281(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon))
         if (e263(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         if (e264(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
         if (e272(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon))
   private static <T> boolean e271(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p_LP(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // $(TypeName)
      if (!e25(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      while (true) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // ~Comma $(TypeName)
         if (!e29(c)) {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
            break;
         }
      }
      int pos7 = c.pos;
      T left8 = c.saveTree();
      int log9 = c.saveLog();
      // $(OptionalEllipsis)
      if (!e30(c)) {
         c.pos = pos7;
         c.backTree(left8);
         c.backLog(log9);
      }
      c.endTree(0,_TTupleDecl,null);
      if (!p_RP(c)) {
         return false;
      }
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl }))
   private static <T> boolean e262(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text100)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pTypedArgDecl(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      {
         T left2 = c.saveTree();
         if (!p_BlockStart(c)) {
            return false;
         }
         c.beginTree(0);
         while (true) {
            int pos = c.pos;
            T left4 = c.saveTree();
            int log = c.saveLog();
            // $(Statement)
            if (!e40(c)) {
               c.pos = pos;
               c.backTree(left4);
               c.backLog(log);
               break;
            }
            if (pos == c.pos) {
               break;
            }
         }
         c.endTree(0,_TAnnotationBody,null);
         if (!p_BlockEnd(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left2);
      }
      c.endTree(0,_TAnnotationDecl,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "api" $(CommonApiBody)
   private static <T> boolean e219(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text91)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pCommonApiBody(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // ~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
   private static <T> boolean e218(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // ~__ "api" $(CommonApiBody)
         if (e219(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl }))
         if (e262(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon))
         if (e263(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         if (e264(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         int pos13 = c.pos;
         T left14 = c.saveTree();
         int log15 = c.saveLog();
         // $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon))
         if (e271(c)) {
            temp = false;
         } else {
            c.pos = pos13;
            c.backTree(left14);
            c.backLog(log15);
         }
      }
      if (temp) {
         int pos16 = c.pos;
         T left17 = c.saveTree();
         int log18 = c.saveLog();
         // $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
         if (e272(c)) {
            temp = false;
         } else {
            c.pos = pos16;
            c.backTree(left17);
            c.backLog(log18);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // ~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
   private static <T> boolean e280(ParserContext<T> c) {
      boolean temp = true;
      if (temp) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // ~__ "api" $(CommonApiBody)
         if (e219(c)) {
            temp = false;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
         }
      }
      if (temp) {
         int pos4 = c.pos;
         T left5 = c.saveTree();
         int log6 = c.saveLog();
         // $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl }))
         if (e262(c)) {
            temp = false;
         } else {
            c.pos = pos4;
            c.backTree(left5);
            c.backLog(log6);
         }
      }
      if (temp) {
         int pos7 = c.pos;
         T left8 = c.saveTree();
         int log9 = c.saveLog();
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon))
         if (e263(c)) {
            temp = false;
         } else {
            c.pos = pos7;
            c.backTree(left8);
            c.backLog(log9);
         }
      }
      if (temp) {
         int pos10 = c.pos;
         T left11 = c.saveTree();
         int log12 = c.saveLog();
         // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         if (e264(c)) {
            temp = false;
         } else {
            c.pos = pos10;
            c.backTree(left11);
            c.backLog(log12);
         }
      }
      if (temp) {
         return false;
      }
      return true;
   }
   // $(({ ($(AnnotationRef))* ($(Public))? ~__ "type" $(TypeName) ~__ '=' ((~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / (~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl })))) #Type }))
   private static <T> boolean e214(ParserContext<T> c) {
      T left = c.saveTree();
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left2 = c.saveTree();
         int log = c.saveLog();
         // $(AnnotationRef)
         if (!e215(c)) {
            c.pos = pos;
            c.backTree(left2);
            c.backLog(log);
            break;
         }
      }
      int pos4 = c.pos;
      T left5 = c.saveTree();
      int log6 = c.saveLog();
      // $(Public)
      if (!e21(c)) {
         c.pos = pos4;
         c.backTree(left5);
         c.backLog(log6);
      }
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text89)) {
         return false;
      }
      {
         T left7 = c.saveTree();
         if (!pTypeName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left7);
      }
      if (!p___(c)) {
         return false;
      }
      if (c.read() != 61) {
         return false;
      }
      boolean temp = true;
      switch(_index90[c.prefetch()]) {
         case 0: 
         return false;
         case 1: 
         // ~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
         temp = e218(c);
         break;
         case 2: 
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         temp = e279(c);
         break;
         case 3: 
         // $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon))
         temp = e271(c);
         break;
         case 4: 
         // $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         temp = e264(c);
         break;
         case 5: 
         // ~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))
         temp = e280(c);
         break;
         case 6: 
         // $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))
         temp = e281(c);
         break;
      }
      if (!temp) {
         return false;
      }
      c.endTree(0,_TType,null);
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($(Constant))* ($(({ ($(AnnotationRef))* ($(Public))? ~__ "type" $(TypeName) ~__ '=' ((~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / (~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl })))) #Type })))* ($(({ ($(Public))? ~__ "extend" $(TypeName) ($(ApiRefs))? $(ClassBlock) #Extension })))* ($(Function))* #Commons }
   private static <T> boolean e18(ParserContext<T> c) {
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $(Constant)
         if (!e19(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      while (true) {
         int pos3 = c.pos;
         T left4 = c.saveTree();
         int log5 = c.saveLog();
         // $(({ ($(AnnotationRef))* ($(Public))? ~__ "type" $(TypeName) ~__ '=' ((~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / (~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl })))) #Type }))
         if (!e214(c)) {
            c.pos = pos3;
            c.backTree(left4);
            c.backLog(log5);
            break;
         }
      }
      while (true) {
         int pos6 = c.pos;
         T left7 = c.saveTree();
         int log8 = c.saveLog();
         // $(({ ($(Public))? ~__ "extend" $(TypeName) ($(ApiRefs))? $(ClassBlock) #Extension }))
         if (!e282(c)) {
            c.pos = pos6;
            c.backTree(left7);
            c.backLog(log8);
            break;
         }
      }
      while (true) {
         int pos9 = c.pos;
         T left10 = c.saveTree();
         int log11 = c.saveLog();
         // $(Function)
         if (!e257(c)) {
            c.pos = pos9;
            c.backTree(left10);
            c.backLog(log11);
            break;
         }
         if (pos9 == c.pos) {
            break;
         }
      }
      c.endTree(0,_TCommons,null);
      return true;
   }
   // { ($(Constant))* ($(({ ($(AnnotationRef))* ($(Public))? ~__ "type" $(TypeName) ~__ '=' ((~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / $((~LP { ($(TypeName))? (~Comma $(TypeName))* ($(OptionalEllipsis))? #TupleDecl } ~RP ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / (~__ "api" $(CommonApiBody) / $((~__ "annotation" { $(TypedArgDecl) $((~BlockStart { ($(Statement))* #AnnotationBody } ~BlockEnd)) #AnnotationDecl })) / $(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl }))) / ($(({ $(ArgumentType) #TypeAliasDecl } ~Semicolon)) / $(({ (($(({ $(TypeName) (~Comma $(TypeName))+ #AndType })) / $(({ $(TypeName) (~BitOr $(TypeName))+ #OrType }))))? ((~__ ';' ~__ / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl }))) / $(({ ($(ApiRefs))? $(CommonApiBody) #CommonApiDecl })) / ~__ ';' ~__) #CompoundTypeDecl })) / $((~__ "class" { ($((~LP { ($(({ $(ClassField) (~Comma $(ClassField))* ($(OptionalEllipsis))? #ClassFields })))? #ClassDataDecl } ~RP)))? ($(ApiRefs))? ($(ClassBlock) / ~__ ';' ~__) #ClassDecl })))) #Type })))* ($(({ ($(Public))? ~__ "extend" $(TypeName) ($(ApiRefs))? $(ClassBlock) #Extension })))* ($(Function))* #Commons }
   private static <T> boolean pCommons(ParserContext<T> c) {
      int memo = c.memoLookupTree(40);
      if (memo == 0) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         if (e18(c)) {
            c.memoTreeSucc(40,pos);
            return true;
         } else {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            c.memoFail(40);
            return false;
         }
      }
      return memo == 1;
   }
   // $(Commons)
   private static <T> boolean e17(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pCommons(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "test" ~BlockStart { ($(Commons))? ($(Unsafe))* #Test } ~BlockEnd
   private static <T> boolean pTest(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text106)) {
         return false;
      }
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Commons)
      if (!e17(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      while (true) {
         int pos3 = c.pos;
         T left4 = c.saveTree();
         int log5 = c.saveLog();
         // $(Unsafe)
         if (!e283(c)) {
            c.pos = pos3;
            c.backTree(left4);
            c.backLog(log5);
            break;
         }
      }
      c.endTree(0,_TTest,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // $(Test)
   private static <T> boolean e284(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pTest(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "unsafe" ~BlockStart { ($(Commons))? ($(Test))* #Unsafe } ~BlockEnd
   private static <T> boolean pUnsafe(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text105)) {
         return false;
      }
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      int pos = c.pos;
      T left = c.saveTree();
      int log = c.saveLog();
      // $(Commons)
      if (!e17(c)) {
         c.pos = pos;
         c.backTree(left);
         c.backLog(log);
      }
      while (true) {
         int pos3 = c.pos;
         T left4 = c.saveTree();
         int log5 = c.saveLog();
         // $(Test)
         if (!e284(c)) {
            c.pos = pos3;
            c.backTree(left4);
            c.backLog(log5);
            break;
         }
      }
      c.endTree(0,_TUnsafe,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      return true;
   }
   // $(Unsafe)
   private static <T> boolean e283(ParserContext<T> c) {
      T left = c.saveTree();
      if (!pUnsafe(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // ~__ "as" $(Name)
   private static <T> boolean e12(ParserContext<T> c) {
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text9)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // { $(Name) (~__ "as" $(Name))? #NameWithAlias }
   private static <T> boolean pNameWithAlias(ParserContext<T> c) {
      c.beginTree(0);
      {
         T left = c.saveTree();
         if (!pName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      int pos = c.pos;
      T left2 = c.saveTree();
      int log = c.saveLog();
      // ~__ "as" $(Name)
      if (!e12(c)) {
         c.pos = pos;
         c.backTree(left2);
         c.backLog(log);
      }
      c.endTree(0,_TNameWithAlias,null);
      return true;
   }
   // ~Comma $(NameWithAlias)
   private static <T> boolean e13(ParserContext<T> c) {
      if (!p_Comma(c)) {
         return false;
      }
      {
         T left = c.saveTree();
         if (!pNameWithAlias(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left);
      }
      return true;
   }
   // $((~BlockStart { $(NameWithAlias) (~Comma $(NameWithAlias))* #ImportList } ~BlockEnd))
   private static <T> boolean e9(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p_BlockStart(c)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pNameWithAlias(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      while (true) {
         int pos = c.pos;
         T left3 = c.saveTree();
         int log = c.saveLog();
         // ~Comma $(NameWithAlias)
         if (!e13(c)) {
            c.pos = pos;
            c.backTree(left3);
            c.backLog(log);
            break;
         }
      }
      c.endTree(0,_TImportList,null);
      if (!p_BlockEnd(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // $((~__ "use" { $(QualifiedName) ($((~BlockStart { $(NameWithAlias) (~Comma $(NameWithAlias))* #ImportList } ~BlockEnd)))? #Use } ~Semicolon))
   private static <T> boolean e0(ParserContext<T> c) {
      T left = c.saveTree();
      if (!p___(c)) {
         return false;
      }
      if (!c.match(_text4)) {
         return false;
      }
      c.beginTree(0);
      {
         T left1 = c.saveTree();
         if (!pQualifiedName(c)) {
            return false;
         }
         c.linkTree(_L);
         c.backTree(left1);
      }
      int pos = c.pos;
      T left3 = c.saveTree();
      int log = c.saveLog();
      // $((~BlockStart { $(NameWithAlias) (~Comma $(NameWithAlias))* #ImportList } ~BlockEnd))
      if (!e9(c)) {
         c.pos = pos;
         c.backTree(left3);
         c.backLog(log);
      }
      c.endTree(0,_TUse,null);
      if (!p_Semicolon(c)) {
         return false;
      }
      c.linkTree(_L);
      c.backTree(left);
      return true;
   }
   // { ($((~__ "use" { $(QualifiedName) ($((~BlockStart { $(NameWithAlias) (~Comma $(NameWithAlias))* #ImportList } ~BlockEnd)))? #Use } ~Semicolon)))* ($(Commons))? ($(Unsafe))* ($(Test))* #Program }
   private static <T> boolean pProgram(ParserContext<T> c) {
      c.beginTree(0);
      while (true) {
         int pos = c.pos;
         T left = c.saveTree();
         int log = c.saveLog();
         // $((~__ "use" { $(QualifiedName) ($((~BlockStart { $(NameWithAlias) (~Comma $(NameWithAlias))* #ImportList } ~BlockEnd)))? #Use } ~Semicolon))
         if (!e0(c)) {
            c.pos = pos;
            c.backTree(left);
            c.backLog(log);
            break;
         }
      }
      int pos3 = c.pos;
      T left4 = c.saveTree();
      int log5 = c.saveLog();
      // $(Commons)
      if (!e17(c)) {
         c.pos = pos3;
         c.backTree(left4);
         c.backLog(log5);
      }
      while (true) {
         int pos6 = c.pos;
         T left7 = c.saveTree();
         int log8 = c.saveLog();
         // $(Unsafe)
         if (!e283(c)) {
            c.pos = pos6;
            c.backTree(left7);
            c.backLog(log8);
            break;
         }
      }
      while (true) {
         int pos9 = c.pos;
         T left10 = c.saveTree();
         int log11 = c.saveLog();
         // $(Test)
         if (!e284(c)) {
            c.pos = pos9;
            c.backTree(left10);
            c.backLog(log11);
            break;
         }
      }
      c.endTree(0,_TProgram,null);
      return true;
   }
   public final static void main(String[] a) {
      int w = 64;
      int n = 50;
      SimpleTree t = parse(a[0], w, n);
      System.out.println(t);
   }
}
/*EOF*/
