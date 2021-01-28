package nez.util;

public class UFlag {
	public static boolean is(int flag, int flag2) {
		return ((flag & flag2) == flag2);
	}

	public static int setFlag(int flag, int flag2) {
		return (flag | flag2);
	}

	public static int unsetFlag(int flag, int flag2) {
		return (flag & (~flag2));
	}
}
