package nez.ast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import nez.util.StringUtils;

public class TreeUtils {

	public static String digestString(Tree<?> node) {
		StringBuilder sb = new StringBuilder();
		byte[] hash = digest(node);
		for (byte b : hash) {
			int d = b & 0xff;
			if (d < 16) {
				sb.append("0");
			}
			sb.append(Integer.toString(d, 16));
		}
		return sb.toString();
	}

	public static byte[] digest(Tree<?> node) {
		try {
			MessageDigest md;
			md = MessageDigest.getInstance("MD5");
			updateDigest(node, md);
			return md.digest();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return new byte[16];
	}

	static void updateDigest(Tree<?> node, MessageDigest md) {
		md.update((byte) '#');
		md.update(StringUtils.utf8(node.getTag().getSymbol()));
		for (int i = 0; i < node.size(); i++) {
			Symbol label = node.getLabel(i);
			if (label != null) {
				md.update((byte) '$');
				md.update(StringUtils.utf8(label.getSymbol()));
			}
			updateDigest(node.get(i), md);
		}
		if (node.size() == 0) {
			md.update(StringUtils.utf8(node.toText()));
		}
	}

}
