package nez.util;

import java.lang.reflect.Array;
import java.util.AbstractList;

public class UList<T> extends AbstractList<T> {
	private int currentSize;
	public T[] ArrayValues;

	public UList(T[] Values) {
		this.ArrayValues = Values;
		this.currentSize = 0;
	}

	@Override
	public String toString() {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("[");
		for (int i = 0; i < size(); i++) {
			if (i > 0) {
				sBuilder.append(", ");
			}
			sBuilder.append(Stringify(ArrayValues[i]));
		}
		sBuilder.append("]");
		return sBuilder.toString();
	}

	protected String Stringify(Object Value) {
		if (Value instanceof String) {
			return StringUtils.quoteString('"', (String) Value, '"');
		}
		return Value.toString();
	}

	@Override
	public final int size() {
		return currentSize;
	}

	@Override
	public final T set(int index, T value) {
		T v = ArrayValues[index];
		ArrayValues[index] = value;
		return v;
	}

	private T[] newArray(int orgsize, int newsize) {
		@SuppressWarnings("unchecked")
		T[] newarrays = (T[]) Array.newInstance(ArrayValues.getClass().getComponentType(), newsize);
		System.arraycopy(ArrayValues, 0, newarrays, 0, orgsize);
		return newarrays;
	}

	private void reserve(int newsize) {
		int currentCapacity = ArrayValues.length;
		if (newsize < currentCapacity) {
			return;
		}
		int newCapacity = currentCapacity * 2;
		if (newCapacity < newsize) {
			newCapacity = newsize;
		}
		this.ArrayValues = newArray(currentSize, newCapacity);
	}

	@Override
	public final void add(int index, T Value) {
		reserve(currentSize + 1);
		ArrayValues[index] = Value;
		this.currentSize = currentSize + 1;
	}

	public void clear(int index) {
		assert (index <= currentSize);
		this.currentSize = index;
	}

	@Override
	public void clear() {
		clear(0);
	}

	public final T pop() {
		this.currentSize -= 1;
		return ArrayValues[currentSize];
	}

	public final T[] compactArray() {
		if (currentSize == ArrayValues.length) {
			return ArrayValues;
		} else {
			@SuppressWarnings("unchecked")
			T[] newValues = (T[]) Array.newInstance(ArrayValues.getClass().getComponentType(), currentSize);
			System.arraycopy(ArrayValues, 0, newValues, 0, currentSize);
			return newValues;
		}
	}

	@Override
	public boolean add(T e) {
		reserve(currentSize + 1);
		ArrayValues[currentSize] = e;
		this.currentSize = currentSize + 1;
		return true;
	}

	@Override
	public T remove(int index) {
		T e = get(index);
		if (currentSize > 1) {
			System.arraycopy(ArrayValues, index + 1, ArrayValues, index, currentSize - 1);
		}
		this.currentSize = currentSize - 1;
		return e;
	}

	@Override
	public T get(int index) {
		return ArrayValues[index];
	}

}
