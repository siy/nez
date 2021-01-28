package nez.util;


import java.lang.reflect.InvocationTargetException;

public class ExtensionLoader {
	public static Object newInstance(String loadPoint, String ext) {
		try {
			Class<?> c = Class.forName(loadPoint + ext);
			return c.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			Verbose.traceException(e);
		}
		return null;
	}
}
