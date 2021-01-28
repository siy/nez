package nez.lang;

import java.util.Arrays;

public class Bytes {

	// Utils
	public static boolean[] newMap(boolean initValue) {
		boolean[] b = new boolean[257];
		if (initValue) {
			Arrays.fill(b, initValue);
		}
		return b;
	}

	public static boolean[] parseByteClass(String octet) {
		boolean[] b = newMap(true);
		while (octet.length() < 8) {
			octet = "0" + octet;
		}
		for (int i = 0; i < 8; i++) {
			int position = 0x80 >> i;
			switch (octet.charAt(i)) {
			case '0':
				for (int j = 0; j < 256; j++) {
					if ((j & position) == 0) {
						continue;
					}
					b[j] = false;
				}
				break;
			case '1':
				for (int j = 0; j < 256; j++) {
					if ((j & position) != 0) {
						continue;
					}
					b[j] = false;
				}
				break;
			case 'x':
			default:
				break;
			}
		}
		b[256] = false;
		return b;
	}

	public static void clear(boolean[] byteMap) {
		Arrays.fill(byteMap, false);
	}

	public static void appendRange(boolean[] b, int beginChar, int endChar) {
		for (int c = beginChar; c <= endChar; c++) {
			b[c] = true;
		}
	}

	public static void appendBitMap(boolean[] dst, boolean[] src) {
		for (int i = 0; i < 256; i++) {
			if (src[i]) {
				dst[i] = true;
			}
		}
	}
}
