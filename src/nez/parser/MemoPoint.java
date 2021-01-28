package nez.parser;

import nez.lang.Expression;
import nez.lang.Typestate;

public final class MemoPoint {
	public final int id;
	public final String label;
	public final Expression e;
	public final Typestate typeState;
	final boolean contextSensitive;

	public MemoPoint(int id, String label, Expression e, Typestate typeState, boolean contextSensitive) {
		this.id = id;
		this.label = label;
		this.e = e;
		this.typeState = typeState;
		this.contextSensitive = contextSensitive;
	}

	public final boolean isStateful() {
		return contextSensitive;
	}

	public final Typestate getTypestate() {
		return typeState;
	}

	int memoHit;
	int memoFailHit;
	long hitLength;
	int maxLength;
	int memoMiss;

	public void memoHit(int consumed) {
		this.memoHit += 1;
		this.hitLength += consumed;
		if (maxLength < consumed) {
			this.maxLength = consumed;
		}
	}

	public void failHit() {
		this.memoFailHit += 1;
	}

	public void miss() {
		this.memoMiss++;
	}

	public final double hitRatio() {
		if (memoMiss == 0)
			return 0.0;
		return (double) memoHit / memoMiss;
	}

	public final double failHitRatio() {
		if (memoMiss == 0)
			return 0.0;
		return (double) memoFailHit / memoMiss;
	}

	public final double meanLength() {
		if (memoHit == 0)
			return 0.0;
		return (double) hitLength / memoHit;
	}

	public final int count() {
		return memoMiss + memoFailHit + memoHit;
	}

	protected final boolean checkDeactivation() {
		if (memoMiss == 32) {
			if (memoHit < 2) {
				return true;
			}
		}
		if (memoMiss % 64 == 0) {
			if (memoHit == 0) {
				return true;
			}

			return memoMiss / memoHit > 10;
		}
		return false;
	}

	@Override
	public String toString() {
		return label + "[id=" + id + "]";
	}

}
