package nez.parser.vm;

import nez.ast.Source;
import nez.ast.Symbol;
import nez.ast.Tree;
import nez.util.Verbose;

class ASTMachine {
	static final boolean debugMode = false;
	static final int Nop = 0;
	static final int Capture = 1;
	static final int Tag = 2;
	static final int Replace = 3;
	static final int LeftFold = 4;
	static final int Pop = 5;
	static final int Push = 6;
	static final int Link = 7;
	static final int New = 8;

	Source source;
	Tree<?> prototype;
	ASTLog firstLog;
	ASTLog lastAppendedLog;
	ASTLog unusedDataLog;

	public ASTMachine(Source source, Tree<?> prototype) {
		this.source = source;
		this.prototype = prototype == null ? new EmptyTree() : prototype;
		// this.log(ASTMachine.Nop, 0, null);
		this.firstLog = new ASTLog();
		this.lastAppendedLog = firstLog;
	}

	private void log(int type, long pos, Symbol label, Object value) {
		ASTLog l;
		if (unusedDataLog == null) {
			l = new ASTLog();
		} else {
			l = unusedDataLog;
			this.unusedDataLog = l.next;
		}
		l.id = lastAppendedLog.id + 1;
		l.type = type;
		l.value = pos;
		l.label = label;
		l.ref = value;
		l.next = null;
		lastAppendedLog.next = l;
		lastAppendedLog = l;
	}

	public final void logNew(long pos, Object debug) {
		log(New, pos, null, debug);
	}

	public final void logCapture(long pos) {
		log(Capture, pos, null, null);
	}

	public final void logTag(Symbol tag) {
		log(Tag, 0, null, tag);
	}

	public final void logReplace(Object value) {
		log(Replace, 0, null, value);
	}

	public final void logLeftFold(long pos, Symbol label) {
		log(LeftFold, pos, label, null);
	}

	public final void logPush() {
		log(Push, 0, null, null);
	}

	public final void logPop(Symbol label) {
		log(Pop, 0, label, null);
	}

	private Object latestLinkedNode;

	public final Object getLatestLinkedNode() {
		return latestLinkedNode;
	}

	public final void logLink(Symbol label, Object node) {
		log(Link, 0, label, node);
		latestLinkedNode = node;
	}

	public final Object saveTransactionPoint() {
		return lastAppendedLog;
	}

	public final void rollTransactionPoint(Object point) {
		ASTLog save = (ASTLog) point;
		if (debugMode) {
			Verbose.debug("roll" + save + " < " + lastAppendedLog);
		}
		if (save != lastAppendedLog) {
			lastAppendedLog.next = unusedDataLog;
			this.unusedDataLog = save.next;
			save.next = null;
			this.lastAppendedLog = save;
		}
		assert (lastAppendedLog.next == null);
	}

	public final void commitTransactionPoint(Symbol label, Object point) {
		ASTLog save = (ASTLog) point;
		Object node = createNode(save.next, null);
		rollTransactionPoint(point);
		if (node != null) {
			logLink(label, node);
		}
	}

	private void dump(ASTLog start) {
		for (ASTLog cur = start; cur != null; cur = cur.next) {
			Verbose.debug(cur.toString());
		}
	}

	public final Tree<?> createNode(ASTLog start, ASTLog pushed) {
		ASTLog cur = start;
		if (debugMode) {
			Verbose.debug("createNode.start: " + start + "     pushed:" + pushed);
		}
		long spos = cur.value, epos = spos;
		Symbol tag = null;
		Object value = null;
		int objectSize = 0;
		for (cur = start; cur != null; cur = cur.next) {
			switch (cur.type) {
			case New:
				spos = cur.value;
				epos = spos;
				objectSize = 0;
				tag = null;
				value = null;
				start = cur;
				break;
			case Capture:
				epos = cur.value;
				break;
			case Tag:
				tag = (Symbol) cur.ref;
				break;
			case Replace:
				value = cur.ref;
				break;
			case LeftFold:
				cur.ref = constructLeft(start, cur, spos, epos, objectSize, tag, value);
				cur.type = Link;
				// cur.value = 0;
				spos = cur.value;
				tag = null;
				value = null;
				objectSize = 1;
				start = cur;
				break;
			case Pop:
				assert (pushed != null);
				pushed.type = Link;
				pushed.label = cur.label;
				pushed.ref = constructLeft(start, cur, spos, epos, objectSize, tag, value);
				pushed.value = cur.value;
				// TODO unused
				pushed.next = cur.next;
				return (Tree<?>) pushed.ref;
			case Push:
				createNode(cur.next, cur);
				assert (cur.type == Link);
			case Link:
				objectSize++;
				break;
			}
		}
		assert (pushed == null);
		return constructLeft(start, null, spos, epos, objectSize, tag, value);
	}

	private Tree<?> constructLeft(ASTLog start, ASTLog end, long spos, long epos, int objectSize, Symbol tag, Object value) {
		if (tag == null) {
			tag = Symbol.Null;
		}
		Tree<?> newnode = prototype.newInstance(tag, source, spos, (int) (epos - spos), objectSize, value);
		int n = 0;
		if (objectSize > 0) {
			for (ASTLog cur = start; cur != end; cur = cur.next) {
				if (cur.type == Link) {
					if (cur.ref == null) {
						Verbose.debug("@@ linking null child at " + cur.value);
					} else {
						newnode.link(n, cur.label, cur.ref);
					}
					n++;
				}
			}
		}
		// return this.treeTransducer.commit(newnode);
		return newnode;
	}

	private Tree<?> parseResult;

	public final Tree<?> getParseResult(long startpos, long endpos) {
		if (parseResult != null) {
			return parseResult;
		}
		if (debugMode) {
			dump(firstLog);
		}
		for (ASTLog cur = firstLog; cur != null; cur = cur.next) {
			if (cur.type == New) {
				parseResult = createNode(cur, null);
				break;
			}
		}
		if (parseResult == null) {
			parseResult = prototype.newInstance(Symbol.Null, source, startpos, (int) (endpos - startpos), 0, null);
		}
		this.firstLog = null;
		this.unusedDataLog = null;
		if (debugMode) {
			Verbose.debug("getParseResult: " + parseResult);
		}
		return parseResult;
	}

	static class ASTLog {
		int id;
		int type;
		Symbol label;
		Object ref;
		long value;

		ASTLog next;

		@Override
		public String toString() {
			switch (type) {
			case Link:
				return "[" + id + "] link(index=" + value + ")";
			case Capture:
				return "[" + id + "] cap(" + value + ")";
			case Tag:
				return "[" + id + "] tag(" + ref + ")";
			case Replace:
				return "[" + id + "] replace(" + ref + ")";
			case LeftFold:
				return "[" + id + "] left(" + value + ")";
			case New:
				return "[" + id + "] new(" + value + "," + ref + ")";
			case Pop:
				return "[" + id + "] pop(" + ref + ")";
			case Push:
				return "[" + id + "] push";
			}
			return "[" + id + "] nop";
		}
	}

	static class EmptyTree extends Tree<EmptyTree> {

		public EmptyTree() {
			super(null, null, 0, 0, null, null);
		}

		@Override
		public EmptyTree newInstance(Symbol tag, Source source, long pos, int len, int size, Object value) {
			return null;
		}

		@Override
		public void link(int n, Symbol label, Object child) {
		}

		@Override
		public EmptyTree newInstance(Symbol tag, int size, Object value) {
			return null;
		}

		@Override
		protected EmptyTree dupImpl() {
			return null;
		}

	}

}
