package nez.debugger;

import java.io.File;
import java.io.IOException;

import nez.ast.Source;
import nez.parser.io.StringSource;
import nez.util.ConsoleUtils;

public abstract class DebugSourceContext extends Context {

	private final String fileName;
	protected long startLineNum;

	protected DebugSourceContext(String fileName, long linenum) {
		this.fileName = fileName;
		this.startLineNum = linenum;
	}

	@Override
	public abstract int byteAt(long pos);

	@Override
	public abstract long length();

	@Override
	public abstract boolean match(long pos, byte[] text);

	@Override
	public abstract String subString(long startIndex, long endIndex);

	@Override
	public abstract long linenum(long pos);

	/* handling input stream */

	@Override
	public final String getResourceName() {
		return fileName;
	}

	@Override
	public Source subSource(long startIndex, long endIndex) {
		return new StringSource(getResourceName(), linenum(startIndex), subByte(startIndex, endIndex), false);
	}

	private long getLineStartPosition(long fromPostion) {
		long startIndex = fromPostion;
		if (!(startIndex < length())) {
			startIndex = length() - 1;
		}
		if (startIndex < 0) {
			startIndex = 0;
		}
		while (startIndex > 0) {
			int ch = byteAt(startIndex);
			if (ch == '\n') {
				startIndex = startIndex + 1;
				break;
			}
			startIndex = startIndex - 1;
		}
		return startIndex;
	}

	public final String getIndentText(long fromPosition) {
		long startPosition = getLineStartPosition(fromPosition);
		long i;
		StringBuilder indent = new StringBuilder();
		for (i = startPosition; i < fromPosition; i++) {
			int ch = byteAt(i);
			if (ch != ' ' && ch != '\t') {
				if (i + 1 != fromPosition) {
					for (long j = i; j < fromPosition; j++) {
						indent.append(" ");
					}
				}
				break;
			}
		}
		indent.insert(0, subString(startPosition, i));
		return indent.toString();
	}

	public final String formatPositionMessage(String messageType, long pos, String message) {
		return "(" + getResourceName() + ":" + linenum(pos) + ") [" + messageType + "] " + message;
	}

	@Override
	public final String formatPositionLine(String messageType, long pos, String message) {
		return formatPositionMessage(messageType, pos, message) + getTextAround(pos, "\n ");
	}

	public final String formatDebugPositionMessage(long pos, String message) {
		return "(" + getResourceName() + ":" + linenum(pos) + ")" + message;
	}

	// @Override
	// public final String formatDebugLine(long pos) {
	// return "(" + this.getResourceName() + ":" + this.linenum(pos) + ")" +
	// this.getTextLine(pos);
	// }
	// FIXME

	public final String formatDebugPositionLine(long pos, String message) {
		return formatDebugPositionMessage(pos, message) + getTextAround(pos, "\n ");
	}

	public final String getTextLine(long pos) {
		int ch;
		if (pos < 0) {
			pos = 0;
		}
		while (byteAt(pos) == 0 && pos > 0) {
			pos -= 1;
		}
		long startIndex = pos;
		while (startIndex > 0) {
			ch = byteAt(startIndex);
			if (ch == '\n' && pos - startIndex > 0) {
				startIndex = startIndex + 1;
				break;
			}
			if (pos - startIndex > 60 && ch < 128) {
				break;
			}
			startIndex = startIndex - 1;
		}
		long endIndex = pos + 1;
		if (endIndex < length()) {
			while ((ch = byteAt(endIndex)) != 0) {
				if (ch == '\n' || endIndex - startIndex > 78 && ch < 128) {
					break;
				}
				endIndex = endIndex + 1;
			}
		} else {
			endIndex = length();
		}
		StringBuilder source = new StringBuilder();
		for (long i = startIndex; i < endIndex; i++) {
			ch = byteAt(i);
			if (ch == '\t') {
				source.append("    ");
			} else {
				source.append((char) ch);
			}
		}
		return source.toString();
	}

	private String getTextAround(long pos, String delim) {
		int ch;
		if (pos < 0) {
			pos = 0;
		}
		while (byteAt(pos) == 0 && pos > 0) {
			pos -= 1;
		}
		long startIndex = pos;
		while (startIndex > 0) {
			ch = byteAt(startIndex);
			if (ch == '\n' && pos - startIndex > 0) {
				startIndex = startIndex + 1;
				break;
			}
			if (pos - startIndex > 60 && ch < 128) {
				break;
			}
			startIndex = startIndex - 1;
		}
		long endIndex = pos + 1;
		if (endIndex < length()) {
			while ((ch = byteAt(endIndex)) != 0) {
				if (ch == '\n' || endIndex - startIndex > 78 && ch < 128) {
					break;
				}
				endIndex = endIndex + 1;
			}
		} else {
			endIndex = length();
		}
		StringBuilder source = new StringBuilder();
		StringBuilder marker = new StringBuilder();
		for (long i = startIndex; i < endIndex; i++) {
			ch = byteAt(i);
			if (ch == '\n') {
				source.append("\\N");
				if (i == pos) {
					marker.append("^^");
				} else {
					marker.append("\\N");
				}
			} else if (ch == '\t') {
				source.append("    ");
				if (i == pos) {
					marker.append("^^^^");
				} else {
					marker.append("    ");
				}
			} else {
				source.append((char) ch);
				if (i == pos) {
					marker.append("^");
				} else {
					marker.append(" ");
				}
			}
		}
		return delim + source + delim + marker;
	}

	public static DebugSourceContext newStringContext(String str) {
		return new DebugStringContext(str);
	}

	public static DebugSourceContext newStringSourceContext(String resource, long linenum, String str) {
		return new DebugStringContext(resource, linenum, str);
	}

	public static DebugSourceContext newDebugFileContext(String fileName) throws IOException {
		File f = new File(fileName);
		if (!f.isFile()) {
			ConsoleUtils.exit(1, "error: Input of Debugger is file");
		}
		return new DebugFileContext(fileName);
	}
}
