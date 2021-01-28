package nez.debugger;

import nez.ast.Source;
import nez.ast.Symbol;

public abstract class TreeTransducer {
	public abstract Object newNode(Symbol tag, Source s, long spos, long epos, int size, Object value);

	public abstract void link(Object node, int index, Symbol label, Object child);

	public abstract Object commit(Object node);
}
