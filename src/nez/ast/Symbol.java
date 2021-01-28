package nez.ast;

import java.util.HashMap;

import nez.util.UList;

public class Symbol {
	private static final HashMap<String, Symbol> tagIdMap = new HashMap<>();
	private static final UList<Symbol> tagNameList = new UList<>(new Symbol[64]);

	public static Symbol unique(String s) {
		Symbol tag = tagIdMap.get(s);
		if (tag == null) {
			tag = new Symbol(tagIdMap.size(), s);
			tagIdMap.put(s, tag);
			tagNameList.add(tag);
		}
		return tag;
	}

	public static int uniqueId(String symbol) {
		return unique(symbol).id;
	}

	public static Symbol tag(int tagId) {
		return tagNameList.ArrayValues[tagId];
	}

	public static final Symbol Null = unique("");
	public static final Symbol MetaSymbol = unique("$");

	final int id;
	final String symbol;

	private Symbol(int id, String symbol) {
		this.id = id;
		this.symbol = symbol;
	}

	public final int id() {
		return id;
	}

	public final String getSymbol() {
		return symbol;
	}

	@Override
	public String toString() {
		return symbol;
	}
}
