package nez.util;

import nez.lang.Grammar;

public class ConsoleUtils {
	static boolean isColored;
	static {
		if (System.getenv("CLICOLOR") != null) {
			isColored = true;
		}
	}

	public static void exit(int status, String message) {
		println("EXIT " + message);
		System.exit(status);
	}

	// 31 :red 　　　32 green, 34 blue, 37 gray
	public static final int Red = 31;
	public static final int Green = 32;
	public static final int Yellow = 32;
	public static final int Blue_ = 33;
	public static final int Blue = 34;
	public static final int Magenta = 35;
	public static final int Cyan = 36;
	public static final int Gray = 37;

	public static void begin(int c) {
		if (isColored) {
			System.out.print("\u001b[00;" + c + "m");
		}
	}

	public static void bold() {
		if (isColored) {
			System.out.print("\u001b[1m");
		}
	}

	public static void end() {
		if (isColored) {
			System.out.print("\u001b[00m");
		}
	}

	public static String bold(String text) {
		if (isColored) {
			return "\u001b[1m" + text + "\u001b[00m";
		}
		return text;
	}

	public static void println(Object s) {
		System.out.println(s);
	}

	public static void print(Object s) {
		System.out.print(s);
	}

	public static void println(String format, Object... args) {
		System.out.println(String.format(format, args));
	}

	public static void print(String format, Object... args) {
		System.out.print(String.format(format, args));
	}

	public static void printIndent(String tab, Object o) {
		System.out.print(tab);
		String s = o.toString();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n') {
				System.out.println();
				System.out.print(tab);
			} else {
				System.out.print(c);
			}
		}
	}

	public static void printlnIndent(String tab, Object o) {
		printIndent(tab, o);
		System.out.println();
	}

	public static boolean isDebug() {
		return System.getenv("DEBUG") != null;
	}

	public static void debug(String s) {
		System.out.println(s);
	}

	public static void notice(String message) {
		System.out.println("NOTICE: " + message);
	}

	public static final int ErrorColor = 31;
	public static final int WarningColor = 35;
	public static final int NoticeColor = 36;

	public static void perror(Grammar g, int color, String msg) {
		begin(color);
		System.out.println(msg);
		end();
	}

	// console

}
