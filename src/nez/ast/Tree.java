package nez.ast;

import nez.parser.io.CommonSource;
import nez.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.Arrays;

public abstract class Tree<E extends Tree<E>> extends AbstractList<E> implements SourceLocation {
	protected static final Symbol[] EmptyLabels = new Symbol[0];

	protected Symbol tag;
	protected Source source;
	protected int pos;
	protected int length;
	protected Symbol[] labels;
	protected E[] subTree;
	protected Object value;

	protected Tree() {
		this.tag = Symbol.Null;
		this.source = null;
		this.pos = 0;
		this.length = 0;
		this.subTree = null;
		this.value = null;
		this.labels = EmptyLabels;
	}

	protected Tree(Symbol tag, Source source, long pos, int len, E[] subTree, Object value) {
		this.tag = tag;
		this.source = source;
		this.pos = (int) pos;
		this.length = len;
		this.subTree = subTree;
		this.value = value;
		this.labels = (this.subTree != null) ? new Symbol[this.subTree.length] : EmptyLabels;
	}

	public abstract E newInstance(Symbol tag, Source source, long pos, int len, int objectsize, Object value);

	public abstract void link(int n, Symbol label, Object child);

	public abstract E newInstance(Symbol tag, int objectsize, Object value);

	protected abstract E dupImpl();

	public final E dup() {
		E t = dupImpl();
		if (subTree != null) {
			for (int i = 0; i < subTree.length; i++) {
				if (subTree[i] != null) {
					t.subTree[i] = subTree[i].dup();
					t.labels[i] = labels[i];
				}
			}
		}
		return t;
	}

	/* Source */

	@Override
	public final Source getSource() {
		return source;
	}

	@Override
	public final long getSourcePosition() {
		return pos;
	}

	public final void setPosition(int pos, int len) {
		this.pos = pos;
		this.length = len;
	}

	@Override
	public final int getLineNum() {
		return (int) source.linenum(pos);
	}

	@Override
	public final int getColumn() {
		return source.column(pos);
	}

	public final int getLength() {
		return length;
	}

	/* Tag, Type */

	public final Symbol getTag() {
		return tag;
	}

	public final void setTag(Symbol tag) {
		this.tag = tag;
	}

	public final boolean is(Symbol tag) {
		return tag == getTag();
	}

	@Override
	public int size() {
		return labels.length;
	}

	@Override
	public final boolean isEmpty() {
		return size() == 0;
	}

	public final Symbol getLabel(int index) {
		return labels[index];
	}

	public final boolean isAllLabeled() {
		for (Symbol label : labels) {
			if (label == null) {
				return false;
			}
		}
		return true;
	}

	public final int countSubNodes() {
		int c = 1;
		for (E t : this) {
			if (t != null) {
				c += t.countSubNodes();
			}
		}
		return c;
	}

	@Override
	public E get(int index) {
		return subTree[index];
	}

	public final E get(int index, E defaultValue) {
		if (index < size()) {
			return subTree[index];
		}
		return defaultValue;
	}

	@Override
	public final E set(int index, E node) {
		E oldValue = subTree[index];
		subTree[index] = node;
		return oldValue;
	}

	public final void set(int index, Symbol label, E node) {
		labels[index] = label;
		subTree[index] = node;
	}

	public final int indexOf(Symbol label) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return i;
			}
		}
		return -1;
	}

	public final boolean has(Symbol label) {
		for (Symbol symbol : labels) {
			if (symbol == label) {
				return true;
			}
		}
		return false;
	}

	public final E get(Symbol label) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return subTree[i];
			}
		}
		throw newNoSuchLabel(label);
	}

	protected RuntimeException newNoSuchLabel(Symbol label) {
		return new RuntimeException("undefined label: " + label);
	}

	public final E get(Symbol label, E defval) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return subTree[i];
			}
		}
		return defval;
	}

	public final void set(Symbol label, E defval) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				subTree[i] = defval;
				return;
			}
		}
	}

	public final void rename(Symbol oldlabel, Symbol newlabel) {
		if (tag == oldlabel) {
			this.tag = newlabel;
		}
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == oldlabel) {
				labels[i] = newlabel;
			}
		}
	}

	/* Value */

	public final byte[] getRawCharacters() {
		return source.subByte(getSourcePosition(), getSourcePosition() + getLength());
	}

	public final Object getValue() {
		return value;
	}

	public final void setValue(Object value) {
		this.value = value;
	}

	public final String toText() {
		if (value != null) {
			if (!(value instanceof Tree<?>)) {
				return value.toString();
			}
		}
		if (source != null) {
			long pos = getSourcePosition();
			long epos = pos + length;
			String s = source.subString(pos, epos);
			/* Binary */
			byte[] tmp = source.subByte(pos, epos);
			if (Arrays.equals(tmp, s.getBytes(StandardCharsets.UTF_8))) {
				this.value = s;
			}
			if (value == null) {
				if (tmp != null) {
					StringBuilder sb = new StringBuilder();
					sb.append("0x");
					for (byte c : tmp) {
						sb.append(String.format("%02x", c & 0xff));
					}
					this.value = sb.toString();
				} else {
					this.value = "";
				}
			}
			return value.toString();
		}
		return "";
	}

	public final boolean is(Symbol label, Symbol tag) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return subTree[i].is(tag);
			}
		}
		return false;
	}

	public final String getText(int index, String defval) {
		if (index < size()) {
			return get(index).toText();
		}
		return defval;
	}

	public final String getText(Symbol label, String defval) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return getText(i, defval);
			}
		}
		return defval;
	}

	public final int toInt(int defvalue) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			String s = toText();
			int num = Integer.parseInt(s);
			if (value == null) {
				this.value = num;
			}
			return num;
		} catch (NumberFormatException e) {
		}
		return defvalue;
	}

	public final int getInt(int index, int defvalue) {
		if (index < size()) {
			return get(index).toInt(defvalue);
		}
		return defvalue;
	}

	public final int getInt(Symbol label, int defvalue) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == label) {
				return getInt(i, defvalue);
			}
		}
		return defvalue;
	}

	@Override
	public final String formatSourceMessage(String type, String msg) {
		return getSource().formatPositionLine(type, getSourcePosition(), msg);
	}

	/**
	 * Create new input stream
	 * 
	 * @return SourceContext
	 */

	public final Source toSource() {
		return CommonSource.newStringSource(getSource().getResourceName(), getSource().linenum(getSourcePosition()), toText());
	}

	public final boolean containsToken(String token) {
		for (E sub : this) {
			if (sub.containsToken(token)) {
				return true;
			}
		}
		return token.equals(toText());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		appendStringified(sb, 0, false);
		return sb.toString();
	}

	protected void appendStringified(StringBuilder sb, int indent, boolean ret) {
		if (ret) {
			sb.append('\n').append("  ".repeat(indent));
		}
		sb.append("(#");
		if (getTag() != null) {
			sb.append(getTag().getSymbol());
		}
		if (subTree == null) {
			sb.append(" ");
			StringUtils.formatStringLiteral(sb, '\'', toText(), '\'');
		} else {
			for (int i = 0; i < size(); i++) {
				sb.append(" ");
				if (labels[i] != null) {
					sb.append("$");
					sb.append(labels[i].getSymbol());
					sb.append("=");
				}
				if (subTree[i] == null) {
					sb.append("null");
				} else {
					subTree[i].appendStringified(sb, indent + 1, this.labels[i] == null);
				}
			}
		}
		appendExtraStringfied(sb);
		sb.append(")");
	}

	protected void appendExtraStringfied(StringBuilder sb) {

	}

}
