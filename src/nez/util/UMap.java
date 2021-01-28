package nez.util;

import java.util.HashMap;

public final class UMap<T> {
	final HashMap<String, T> m;

	public UMap() {
		this.m = new HashMap<>();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		int i = 0;
		for (String Key : m.keySet()) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(stringify(Key));
			sb.append(" : ");
			sb.append(stringify(m.get(Key)));
			i++;
		}
		sb.append("}");
		return sb.toString();
	}

	protected String stringify(Object Value) {
		if (Value instanceof String) {
			return StringUtils.quoteString('"', (String) Value, '"');
		}
		return Value.toString();
	}

	public final void put(String key, T value) {
		m.put(key, value);
	}

	public final T get(String key) {
		return m.get(key);
	}

	public final T get(String key, T defaultValue) {
		T Value = m.get(key);
		if (Value == null) {
			return defaultValue;
		}
		return Value;
	}

	public final void remove(String Key) {
		m.remove(Key);
	}

	public final boolean hasKey(String Key) {
		return m.containsKey(Key);
	}

	public final UList<String> keys() {
		UList<String> a = new UList<>(new String[m.size()]);
		a.addAll(m.keySet());
		return a;
	}

	public final UList<T> values(T[] aa) {
		UList<T> a = new UList<>(aa);
		a.addAll(m.values());
		return a;
	}

	public final int size() {
		return m.size();
	}

	public final void clear() {
		m.clear();
	}
}
