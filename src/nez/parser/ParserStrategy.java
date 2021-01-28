package nez.parser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;

import nez.ast.Source;
import nez.ast.SourceLocation;
import nez.ast.Tree;
import nez.lang.Grammar;
import nez.parser.vm.MozMachine;
import nez.parser.vm.ParserMachineCompiler;
import nez.util.ConsoleUtils;
import nez.util.Verbose;

public class ParserStrategy {

	/* Grammars */
	public boolean TreeConstruction = true;
	public boolean DefaultCondition;
	// public boolean SymbolTable = true;
	public boolean BinaryGrammar;
	public boolean PEGCompatible;

	/* Optimization */
	public boolean Optimization = true;
	public boolean Oinline = true;
	public boolean Oalias;

	public boolean Olex = true;
	public boolean Ostring = true;
	public int Prediction = 2;
	public boolean Odfa;

	public boolean Oorder = true;
	public boolean Detree;
	/* Classic */
	public boolean Moz;

	/* PackratParsing */
	public boolean PackratParsing = true;
	public int SlidingWindow = 64;
	public double TreeFactor = 3.00;
	public double MemoLimit = 0.5;
	public boolean StatefulPackratParsing;

	/* Generator */
	public boolean VerboseCode = true;
	public boolean SSE;

	/* Profiling */
	public boolean Coverage;
	public boolean Profiling;
	public boolean Wnone;
	public boolean Wall;

	public ParserStrategy() {
		init();
	}

	public ParserStrategy(String arguments) {
		init();
		for (String option : arguments.split(" ")) {
			setOption(option);
		}
	}

	public final boolean setOption(String option) {
		int loc = option.indexOf('=');
		if (loc > 0) {
			String name = option.substring(0, loc);
			String value = option.substring(loc + 1);
			if (value.equals("true")) {
				return setValue(name, true);
			}
			if (value.equals("false")) {
				return setValue(name, false);
			}
			try {
				int nvalue = Integer.parseInt(value);
				return setValue(name, nvalue);
			} catch (Exception e) {
			}
			try {
				double nvalue = Double.parseDouble(value);
				return setValue(name, nvalue);
			} catch (Exception e) {
			}
			return setValue(name, value);
		}
		if (option.startsWith("-")) {
			return setValue(option.substring(1), false);
		}
		if (option.startsWith("+")) {
			return setValue(option.substring(1), true);
		}
		return false;
	}

	private boolean setValue(String name, Object value) {
		try {
			Field f = getClass().getField(name);
			f.set(this, value);
			return true;
		} catch (Exception e) {
			ConsoleUtils.println("cannot set %s = %s", name, value);
			Verbose.traceException(e);
			return true; // this is not a file
		}
	}

	@Override
	public final String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		int c = 0;
		Field[] fields = getClass().getFields();
		for (Field f : fields) {
			if (c > 0) {
				sb.append(",");
			}
			if (Modifier.isPublic(f.getModifiers())) {
				sb.append(f.getName());
				sb.append("=");
				try {
					sb.append(f.get(this));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					Verbose.traceException(e);
				}
				c++;
			}
		}
		sb.append("]");
		return sb.toString();
	}

	@Override
	public final ParserStrategy clone() {
		ParserStrategy s = new ParserStrategy();
		Field[] fields = getClass().getFields();
		for (Field f : fields) {
			if (Modifier.isPublic(f.getModifiers())) {
				try {
					f.set(s, f.get(this));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					Verbose.traceException(e);
				}
			}
		}
		return s;
	}

	public static ParserStrategy nullCheck(ParserStrategy strategy) {
		return strategy == null ? newDefaultStrategy() : strategy;
	}

	public static ParserStrategy newDefaultStrategy() {
		return new ParserStrategy();
	}

	public static ParserStrategy newSafeStrategy() {
		ParserStrategy s = new ParserStrategy();
		s.Moz = false;
		s.Prediction = 1;
		// s.PackratParsing = false;
		return s;
	}

	// ----------------------------------------------------------------------
	// reporter

	private ArrayList<String> logs;
	private HashSet<String> checks;

	void init() {
		if (Wnone) {
			this.logs = new ArrayList<>();
			this.checks = new HashSet<>();
		} else {
			this.logs = null;
			this.checks = null;
		}
	}

	private void log(String msg) {
		if (checks != null && !checks.contains(msg)) {
			checks.add(msg);
			logs.add(msg);
		}
	}

	public void report() {
		for (String s : logs) {
			if (!Wall) {
				if (s.contains("notice")) {
					continue; // skip notice
				}
			}
			ConsoleUtils.println(s);
		}
		init();
	}

	public final void reportError(SourceLocation s, String message) {
		if (s != null) {
			log(s.formatSourceMessage("error", message));
		}
	}

	public final void reportWarning(SourceLocation s, String message) {
		if (s != null) {
			log(s.formatSourceMessage("warning", message));
		}
	}

	public final void reportNotice(SourceLocation s, String message) {
		if (s != null) {
			log(s.formatSourceMessage("notice", message));
		}
	}

	public final void reportError(SourceLocation s, String fmt, Object... args) {
		if (s != null) {
			log(s.formatSourceMessage("error", String.format(fmt, args)));
		}
	}

	public final void reportWarning(SourceLocation s, String fmt, Object... args) {
		if (s != null) {
			log(s.formatSourceMessage("warning", String.format(fmt, args)));
		}
	}

	public final void reportNotice(SourceLocation s, String fmt, Object... args) {
		if (s != null) {
			log(s.formatSourceMessage("notice", String.format(fmt, args)));
		}
	}

	/* -------------------------------------------------------------------- */

	public final Parser newParser(Grammar grammar) {
		return grammar.newParser(this);
	}

	public ParserCode<?> newParserCode(Grammar pgrammar) {
		ParserMachineCompiler bc = ParserMachineCompiler.newCompiler(this);
		return bc.compile(pgrammar);
	}

	public ParserInstance newParserContext(Source source, int memoPointSize, Tree<?> prototype) {
		MemoTable table = MemoTable.newTable(SlidingWindow, memoPointSize);
		MozMachine machine = new MozMachine(source);
		machine.init(table, prototype);
		return new ParserInstance(source, machine);
	}

	/* Profiler */

	private CoverageProfiler cov;

	public final CoverageProfiler getCoverageProfiler() {
		if (Coverage) {
			if (cov == null) {
				cov = new CoverageProfiler();
			}
			return cov;
		}
		return null;
	}

}
