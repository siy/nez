package nez.util;

public class CharReader {
	String text;
	int pos;

	public CharReader(String text) {
		this.text = text;
		this.pos = 0;
	}

	final boolean hasChar() {
		return (pos < text.length());
	}

	public final char readChar() {
		if (pos < text.length()) {
			char ch = read(pos);
			if (ch == '\\') {
				char ch1 = read(pos + 1);
				if (ch1 == 'u' || ch1 == 'U') {
					ch = StringUtils.parseHex4(read(pos + 2), read(pos + 3), read(pos + 4), read(pos + 5));
					this.pos = pos + 5;
				} else {
					ch = readEsc(ch1);
					this.pos = pos + 1;
				}
			}
			this.pos = pos + 1;
			return ch;
		}
		return '\0';
	}

	private char read(int pos) {
		if (pos < text.length()) {
			return text.charAt(pos);
		}
		return 0;
	}

	private char readEsc(char ch1) {
		switch (ch1) {
		case 'a':
			return '\007'; /* bel */
		case 'b':
			return '\b'; /* bs */
		case 'e':
			return '\033'; /* esc */
		case 'f':
			return '\f'; /* ff */
		case 'n':
			return '\n'; /* nl */
		case 'r':
			return '\r'; /* cr */
		case 't':
			return '\t'; /* ht */
		case 'v':
			return '\013'; /* vt */
		}
		return ch1;
	}

}
