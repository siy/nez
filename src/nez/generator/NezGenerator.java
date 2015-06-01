package nez.generator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.TreeMap;

import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.Production;
import nez.main.Verbose;
import nez.util.FileBuilder;

public abstract class NezGenerator {
	public abstract String getDesc();

	public final static String GeneratorLoaderPoint = "nez.main.ext.L";
	static private TreeMap<String, Class<?>> classMap = new TreeMap<String, Class<?>>();

	public static void regist(String key, Class<?> c) {
		classMap.put(key, c);
	}

	public final static boolean supportedGenerator(String key) {
		if(!classMap.containsKey(key)) {
			try {
				Class.forName(GeneratorLoaderPoint + key);
			} catch (ClassNotFoundException e) {
			}
		}
		return classMap.containsKey(key);
	}

	public final static NezGenerator newNezGenerator(String key) {
		Class<?> c = classMap.get(key);
		if(c != null) {
			try {
				return (NezGenerator) c.newInstance();
			} catch (InstantiationException e) {
				Verbose.traceException(e);
			} catch (IllegalAccessException e) {
				Verbose.traceException(e);
			}
		}
		return null;
	}

	public final static NezGenerator newNezGenerator(String key, String fileName) {
		Class<?> c = classMap.get(key);
		if(c != null) {
			try {
				Constructor<?> ct = c.getConstructor(String.class);
				return (NezGenerator) ct.newInstance(fileName);
			} catch (InstantiationException e) {
				Verbose.traceException(e);
			} catch (IllegalAccessException e) {
				Verbose.traceException(e);
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

//	public final static NezGenerator newGenerator(String key, String path) {
//		Class<?> c = classMap.get(key);
//		if(c != null) {
//			try {
//				return (NezGenerator)c.newInstance();
//			} catch (InstantiationException e) {
//				Verbose.traceException(e);
//			} catch (IllegalAccessException e) {
//				Verbose.traceException(e);
//			}
//		}
//		return null;
//	}
	final protected FileBuilder file;

	public NezGenerator() {
		this.file = new FileBuilder();
	}

	public NezGenerator(String fileName) {
		this.file = new FileBuilder(fileName);
	}

	HashMap<Class<?>, Method> methodMap = new HashMap<Class<?>, Method>();

	public final void visit(Expression p) {
		Method m = lookupMethod("visit", p.getClass());
		if(m != null) {
			try {
				m.invoke(this, p);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		else {
			visitUndefined(p);
		}
	}

	void visitUndefined(Expression p) {
		Verbose.todo("undefined: " + p.getClass());
	}

	protected final Method lookupMethod(String method, Class<?> c) {
		Method m = this.methodMap.get(c);
		if(m == null) {
			String name = method + c.getSimpleName();
			try {
				m = this.getClass().getMethod(name, c);
			} catch (NoSuchMethodException e) {
				return null;
			} catch (SecurityException e) {
				return null;
			}
			this.methodMap.put(c, m);
		}
		return m;
	}

	// ---------------------------------------------------------------------
	public void generate(Grammar grammar) {
		makeHeader(grammar);
		for(Production p : grammar.getProductionList()) {
			visitProduction(p);
		}
		makeFooter(grammar);
		file.writeNewLine();
		file.flush();
	}

	public void makeHeader(Grammar g) {
	}

	public abstract void visitProduction(Production r);

	public void makeFooter(Grammar g) {
	}
}