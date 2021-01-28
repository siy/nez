package nez.x.generator;

import java.lang.reflect.InvocationTargetException;
import java.util.TreeMap;

import nez.tool.peg.GrammarTranslatorVisitor;
import nez.util.Verbose;

public class GeneratorLoader {
	public static final String GeneratorLoaderPoint = "nez.main.ext.L";
	static TreeMap<String, Class<?>> classMap = new TreeMap<>();

	public static void regist(String key, Class<?> c) {
		classMap.put(key, c);
	}

	public static boolean isSupported(String key) {
		if (!classMap.containsKey(key)) {
			try {
				Class.forName(GeneratorLoaderPoint + key);
			} catch (ClassNotFoundException e) {
			}
		}
		return classMap.containsKey(key);
	}

	public static GrammarTranslatorVisitor load(String key) {
		Class<?> c = classMap.get(key);
		if (c != null) {
			try {
				return (GrammarTranslatorVisitor) c.getDeclaredConstructor().newInstance();
			} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
				Verbose.traceException(e);
			}
		}
		return null;
	}
}
