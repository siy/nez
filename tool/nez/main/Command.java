package nez.main;

import nez.ParserGenerator;
import nez.Version;
import nez.ast.Source;
import nez.lang.Grammar;
import nez.lang.Production;
import nez.lang.ast.NezGrammarCombinator;
import nez.parser.Parser;
import nez.parser.ParserStrategy;
import nez.parser.io.CommonSource;
import nez.tool.ast.TreeJSONWriter;
import nez.tool.ast.TreeWriter;
import nez.tool.ast.TreeXMLWriter;
import nez.util.ConsoleUtils;
import nez.util.FileBuilder;
import nez.util.UList;
import nez.util.Verbose;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

public abstract class Command {
	public static final String ProgName = "Nez";
	public static final String CodeName = "beta";

	public static final int MajorVersion = 1;
	public static final int MinorVersion = 0;
	public static final int PatchLevel = nez.Version.REV;

	private static final Map<String, Supplier<Command>> COMMANDS = new HashMap<>();

	static {
		COMMANDS.put("bench", Cbench::new);
		COMMANDS.put("code", Ccode::new);
		COMMANDS.put("dump", Cdump::new);
		COMMANDS.put("format", Cformat::new);
		COMMANDS.put("match", Cmatch::new);
		COMMANDS.put("parse", Cparse::new);
		COMMANDS.put("test", Ctest::new);
		COMMANDS.put("compile", Ccompile::new);
		COMMANDS.put("example", Cexample::new);
		COMMANDS.put("inez", Cinez::new);
		COMMANDS.put("peg", Cpeg::new);
	}

	public static void main(String[] args) {
		try {
			Command com = newCommand(args);
			if (Verbose.enabled) {
				Verbose.println("nez-%d.%d.%d %s", MajorVersion, MinorVersion, PatchLevel, com.getClass().getName());
				Verbose.println("strategy: %s", com.strategy);
			}
			com.exec();
		} catch (IOException e) {
			ConsoleUtils.println(e);
			Verbose.traceException(e);
			System.exit(1);
		}
	}

	private static Command newCommand(String[] args) {
		return ofNullable(args)
			.flatMap(arg -> ofNullable(arg.length > 0 ? arg[0] : null))
			.flatMap(arg -> ofNullable(COMMANDS.get(arg.toLowerCase())))
			.map(Supplier::get)
			.map(cmd -> {cmd.parseCommandOption(args); return cmd;})
			.orElse(new Command() {
				@Override
				public void exec() {
					assert args != null;
					String cmd = args.length > 0 ? args[0] : "";
					showUsage("Unknown command " + cmd);
				}
			});
	}

	public void exec() throws IOException {
		System.out.println(strategy);
	}

	protected String command;
	protected ParserStrategy strategy = new ParserStrategy();
	protected String grammarFile;
	protected String grammarSource;
	protected String grammarType;
	protected UList<String> grammarFiles = new UList<>(new String[4]);
	protected String startProduction;
	protected String inputText;
	protected UList<String> inputFiles = new UList<>(new String[4]);
	protected String outputFormat;
	protected String outputDirectory;

	private void parseCommandOption(String[] args) {
		for (int index = 1; index < args.length; index++) {
			String as = args[index];
			if (index + 1 < args.length) {
				if (as.equals("-g") || as.equals("--grammar") || as.equals("-p")) {
					grammarFile = args[index + 1];
					grammarSource = null;
					index++;
					continue;
				}
				if (as.equals("-e") || as.equals("--expression")) {
					grammarFile = null;
					grammarSource = args[index + 1];
					grammarType = "nez";
					index++;
					continue;
				}
				if (as.equals("-a") || as.equals("--aux")) {
					grammarFiles.add(args[index + 1]);
					index++;
					continue;
				}
				if (as.equals("-s") || as.equals("--start")) {
					startProduction = args[index + 1];
					index++;
					continue;
				}
				if (as.equals("--input")) {
					inputText = args[index + 1];
					index++;
					continue;
				}
				if (as.equals("-f") || as.equals("--format")) {
					outputFormat = args[index + 1];
					index++;
					continue;
				}
				if (as.equals("-d") || as.equals("--dir")) {
					outputDirectory = args[index + 1];
					index++;
					continue;
				}
			}
			if (as.equals("--verbose")) {
				Verbose.enabled = true;
				continue;
			}
			if (!strategy.setOption(as)) {
				if (as.startsWith("-") && as.length() > 1) {
					showUsage("undefined option: " + as);
				}
				inputFiles.add(as);
			}
		}
	}

	public static void displayVersion() {
		ConsoleUtils.bold();
		ConsoleUtils.println(ProgName + "-" + nez.Version.Version + " (" + CodeName + ") on Java JVM-" + System.getProperty("java.version"));
		ConsoleUtils.end();
		ConsoleUtils.println(Version.Copyright);
	}

	protected static void showUsage(String msg) {
		displayVersion();
		ConsoleUtils.println("Usage: nez <command> options inputs");
		ConsoleUtils.println("  -g | --grammar <file>      Specify a grammar file");
		ConsoleUtils.println("  -f | --format <string>     Specify an output format");
		ConsoleUtils.println("  -s | --start <NAME>        Specify a starting production");
		ConsoleUtils.println("  -d | --dir <dirname>       Specify an output dir");
		ConsoleUtils.println("Example:");
		ConsoleUtils.println("  nez parse -g js.nez jquery.js --format json");
		ConsoleUtils.println("  nez match -g js.nez *.js");
		ConsoleUtils.println("  nez parser -g math.nez --format c");
		ConsoleUtils.println("");

		ConsoleUtils.println("The most commonly used nez commands are:");
		ConsoleUtils.println("  parse      parse inputs and construct ASTs");
		ConsoleUtils.println("  match      match inputs without ASTs");
		ConsoleUtils.println("  inez       an interactive parser");
		ConsoleUtils.println("  code       generate a parser source code for --format");
		ConsoleUtils.println("  cnez       generate a C-based fast parser");
		ConsoleUtils.println("  peg        translate a grammar into PEG specified with --format");
		ConsoleUtils.println("  compile    compile a grammar into Nez bytecode .moz");
		ConsoleUtils.println("  bench      perform benchmark tests");
		ConsoleUtils.println("  example    display examples in a grammar");
		ConsoleUtils.println("  test       perform grammar tests");
		ConsoleUtils.exit(0, msg);
	}

	public final Grammar getSpecifiedGrammar() throws IOException {
		if (grammarFile != null) {
			ParserGenerator pg = new ParserGenerator();
			Grammar grammar = pg.loadGrammar(grammarFile);
			for (String f : grammarFiles) {
				pg.updateGrammar(grammar, f);
			}
			if (startProduction != null) {
				if (!grammar.hasProduction(startProduction)) {
					String s = startProduction.substring(0, 1);
					StringBuilder sb = new StringBuilder();
					for (Production p : grammar) {
						if (p.getLocalName().startsWith(s)) {
							sb.append(" ");
							sb.append(p.getLocalName());
						}
					}
					ConsoleUtils.println("No such production -s " + startProduction + ". Try " + sb);
					throw new IOException("No such start production: " + startProduction);
				}
				grammar.setStartProduction(startProduction);
			}
			return grammar;
		}
		return new Grammar();
	}

	public final Parser newParser() throws IOException {
		return strategy.newParser(getSpecifiedGrammar());
	}

	public final Parser getNezParser() {
		Grammar grammar = new Grammar("nez");
		return new NezGrammarCombinator().load(grammar, "File").newParser(ParserStrategy.newSafeStrategy());
	}

	private int fileIndex;

	public final void checkInputSource() {
		if (inputFiles.size() == 0 && inputText == null) {
			ConsoleUtils.exit(1, "no input specified");
		}
	}

	public final boolean hasInputSource() {
		return fileIndex < inputFiles.size() || inputText != null;
	}

	public final Source nextInputSource() throws IOException {
		if (hasInputSource()) {
			if (inputText != null) {
				Source s = CommonSource.newStringSource(inputText);
				inputText = null;
				return s;
			}
			String path = inputFiles.ArrayValues[fileIndex];
			fileIndex++;
			return CommonSource.newFileSource(path);
		}
		return CommonSource.newStringSource(""); // empty input
	}

	public final String getOutputFileName(Source input, String ext) {
		if (outputDirectory != null) {
			return FileBuilder.toFileName(input.getResourceName(), outputDirectory, ext);
		} else {
			return null; // stdout
		}
	}

	public final TreeWriter getTreeWriter(String options, String defaultFormat) {
		if (outputFormat == null) {
			outputFormat = defaultFormat;
		}
		switch (outputFormat) {
		case "ast":
			return new TreeWriter.AstWriter();
		case "line":
			return new TreeWriter.LineWriter();
		case "xml":
			return new TreeXMLWriter();
		case "json":
			return new TreeJSONWriter();
		}
		return (TreeWriter) newExtendedOutputHandler("", options);
	}

	public final Object newExtendedOutputHandler(String classPath, String options) {
		if (outputFormat.indexOf('.') > 0) {
			classPath = outputFormat;
		} else {
			classPath += outputFormat;
		}
		try {
			return Class.forName(classPath).getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
			Verbose.traceException(e);
			if (options != null) {
				ConsoleUtils.println("Available format: " + options);
				ConsoleUtils.exit(1, "undefined format: " + outputFormat);
			}
		}
		return null;
	}
}
