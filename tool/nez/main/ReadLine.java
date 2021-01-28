package nez.main;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Scanner;


public class ReadLine {

	public static Object getConsoleReader() {
		if (ReadLine.console == null) {
			try {
				ReadLine.console = Class.forName("jline.ConsoleReader").getDeclaredConstructor().newInstance();
			} catch (Exception e) {
				System.err.println("CHECK: " + e);
			}
		}
		return ReadLine.console;
	}

	public static Object console;

	public static String readSingleLine(Object console, String prompt) {
		if (!(console instanceof Scanner)) {
			try {
				Method m = console.getClass().getMethod("readLine", String.class);
				return (String) m.invoke(console, prompt);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.print(prompt);
		System.out.flush();
		return new Scanner(System.in).nextLine();
	}

	public static void addHistory(Object console, String text) {
		if (console != null) {
			try {
				Method m = console.getClass().getMethod("getHistory");
				Object hist = m.invoke(console);
				m = hist.getClass().getMethod("addToHistory", String.class);
				m.invoke(hist, text);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void addCompleter(List<String> list) {
	}

	public static String readMultiLine(String prompt, String prompt2) {
		Object console = getConsoleReader();
		StringBuilder sb = new StringBuilder();
		String line;
		while (true) {
			line = readSingleLine(console, prompt);
			if (line == null) {
				return null;
			}
			if (line.equals("")) {
				break;
			}
			sb.append(line);
			sb.append("\n");
			prompt = prompt2;
		}
		line = sb.toString();
		addHistory(console, line);
		return line;
	}
}
