package nez.parser;

public class MemoEntry {
	public boolean failed;
	public int consumed;
	public Object result;
	// int memoPoint;
	public int stateValue;
}

class MemoEntryKey extends MemoEntry {
	long key = -1;
}

class MemoEntryList extends MemoEntry {
	int memoPoint;
	MemoEntryList next;
}