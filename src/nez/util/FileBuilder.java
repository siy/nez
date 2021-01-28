package nez.util;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileBuilder {

	protected String fileName;
	private OutputStream out;

	public static final String TAB = "   ";
	public static final String LF = "\n";
	public static final String CRLF = "\r\n";

	public FileBuilder() {
		this.out = null;
	}

	public FileBuilder(String fileName) {
		try {
			this.out = new BufferedOutputStream(new FileOutputStream(fileName));
			this.fileName = fileName;
		} catch (NullPointerException e) {
			this.out = null;
		} catch (FileNotFoundException e) {
			ConsoleUtils.notice(e.getMessage());
			this.out = null;
		}
	}

	public final void write(String text) {
		try {
			if (out == null) {
				System.out.print(text);
			} else {
				String CHARSET = "UTF8";
				out.write(text.getBytes(CHARSET));
			}
		} catch (IOException e) {
			ConsoleUtils.exit(1, "IO error: " + e.getMessage());
		}
	}

	public final void flush() {
		try {
			if (out == null) {
				System.out.flush();
			} else {
				out.flush();
			}
		} catch (IOException e) {
			ConsoleUtils.exit(1, "IO error: " + e.getMessage());
		}
	}

	public final void close() {
		flush();
		if (fileName != null) {
			Verbose.println("generating: " + fileName);
		}
	}

	public String Tab() {
		return TAB;
	}

	public String NewLine() {
		return LF;
	}

	int IndentLevel;
	String currentIndentString = "";

	public final void incIndent() {
		this.IndentLevel = IndentLevel + 1;
		this.currentIndentString = null;
	}

	public final void decIndent() {
		this.IndentLevel = IndentLevel - 1;
		assert (IndentLevel >= 0);
		this.currentIndentString = null;
	}

	private String Indent() {
		if (currentIndentString == null) {
			StringBuilder indentBuilder = new StringBuilder(64);
			for (int i = 0; i < IndentLevel; ++i) {
				indentBuilder.append(Tab());
			}
			this.currentIndentString = indentBuilder.toString();
		}
		return currentIndentString;
	}

	public final void writeNewLine() {
		write(NewLine());
		flush();
	}

	public final void writeIndent() {
		write(NewLine());
		flush();
		write(Indent());
	}

	public final void writeIndent(String text) {
		write(NewLine());
		flush();
		write(Indent());
		write(text);
	}

	public final void write(String fmt, Object... args) {
		write(String.format(fmt, args));
	}

	public final void writeIndent(String fmt, Object... args) {
		writeIndent(String.format(fmt, args));
	}

	public void writeMultiLine(String sub) {
		int start = 0;
		boolean empty = true;
		for (int i = 0; i < sub.length(); i++) {
			char ch = sub.charAt(i);
			if (ch == ' ' || ch == '\t') {
				continue;
			}
			if (ch == '\n') {
				if (!empty) {
					writeIndent(sub.substring(start, i));
				}
				start = i + 1;
				empty = true;
				continue;
			}
			empty = false;
		}
	}

	/* Utils */

	public static String toFileName(String urn, String dir, String ext) {
		if (urn == null) {
			urn = "stdout.out";
		}
		int loc = urn.lastIndexOf('.');
		if (loc > 0) {
			urn = urn.substring(0, loc);
			if (ext != null) {
				urn = urn + "." + ext;
			}
		}
		if (dir != null) {
			dir = dir + "/";
		} else {
			dir = "";
		}
		loc = urn.lastIndexOf('/');
		if (loc > 0) {
			urn = dir + urn.substring(loc + 1);
		}
		return urn;
	}

	public static String extractFileName(String path) {
		int loc = path.lastIndexOf('/');
		if (loc > 0) {
			return path.substring(loc + 1);
		}
		loc = path.lastIndexOf('\\');
		if (loc > 0) {
			return path.substring(loc + 1);
		}
		return path;
	}

	public static String extractFileExtension(String path) {
		int loc = path.lastIndexOf('.');
		if (loc > 0) {
			return path.substring(loc + 1);
		}
		return path;
	}

	public static String changeFileExtension(String path, String ext) {
		int loc = path.lastIndexOf('.');
		if (loc > 0) {
			return path.substring(0, loc + 1) + ext;
		}
		return path + "." + ext;
	}

}
