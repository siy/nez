package nez.util;

import java.lang.reflect.InvocationTargetException;

import nez.Version;
import nez.lang.Expression;

public class Verbose {
	public static boolean enabled;
	/* obsolete */
	public static boolean BacktrackActivity;
	public static boolean PackratParsing;

	public static void println(String s) {
		if (enabled) {
			ConsoleUtils.begin(34);
			ConsoleUtils.println(s);
			ConsoleUtils.end();
		}
	}

	public static void println(String fmt, Object... args) {
		if (enabled) {
			println(String.format(fmt, args));
		}
	}

	public static void print(String s) {
		if (enabled) {
			ConsoleUtils.begin(34);
			ConsoleUtils.print(s);
			ConsoleUtils.end();
		}
	}

	public static void print(String fmt, Object... args) {
		if (enabled) {
			print(String.format(fmt, args));
		}
	}

	public static void traceException(Throwable e) {
		if (e instanceof InvocationTargetException) {
			Throwable e2 = ((InvocationTargetException) e).getTargetException();
			if (e2 instanceof RuntimeException) {
				throw (RuntimeException) e2;
			}
		}
		if (enabled) {
			ConsoleUtils.begin(ConsoleUtils.Red);
			e.printStackTrace();
			ConsoleUtils.end();
		}
	}

	public static void TODO(String s) {
		println("[TODO] " + s);
	}

	public static void TODO(String fmt, Object... args) {
		println("[TODO] " + String.format(fmt, args));
	}

	public static void printElapsedTime(String msg, long t1, long t2) {
		if (enabled) {
			double d = (t2 - t1) / 1_000_000.0;
			if (d > 0.1) {
				println("%s : %f[ms]", msg, d);
			}
		}
	}

	public static void noticeOptimize(String key, Expression p) {
	}

	public static void debug(Object s) {
		if (Version.ReleasePreview) {
			ConsoleUtils.println("debug: " + s);
		}
	}

	public static void FIXME(Object s) {
		if (Version.ReleasePreview) {
			ConsoleUtils.println("FIXME: " + s);
		}
	}

}
