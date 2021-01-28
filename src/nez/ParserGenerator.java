package nez;

import java.io.IOException;
import java.util.HashMap;

import nez.ast.Source;
import nez.ast.Tree;
import nez.lang.Grammar;
import nez.lang.ast.GrammarLoader;
import nez.lang.ast.NezGrammarCombinator;
import nez.parser.Parser;
import nez.parser.ParserException;
import nez.parser.ParserStrategy;
import nez.parser.io.CommonSource;
import nez.parser.io.StringSource;
import nez.util.FileBuilder;
import nez.util.Verbose;

/**
 * ParserGenerator is an implementation of Nez dynamic parser generator.
 * 
 * <pre>
 * ParserGenerator generator = new ParserGenerator();
 * Parser parser = generator.newParser("grammar.nez");
 * Tree<?> tree = parser.parse(Source, ...);
 * </pre>
 * 
 * @author kiki
 *
 */

public class ParserGenerator {
	public abstract static class GrammarExtension {
		ParserGenerator nez;
		String path;
		Parser parser;

		protected GrammarExtension(String path) {
			this.path = path;
		}

		public void init(ParserGenerator nez) {
			this.nez = nez;
		}

		public Parser getParser() throws IOException {
			if (parser == null) {
				parser = ParserStrategy.newDefaultStrategy().newParser(nez.loadGrammar(path));
			}
			return parser;
		}

		public abstract String getExtension();

		public abstract void updateGrammarLoader(GrammarLoader loader);

		public GrammarExtension newState() {
			return this; // in case of stateless
		}
	}

	private final String[] classPath;
	private final HashMap<String, GrammarExtension> extensionMap = new HashMap<>();

	public ParserGenerator(String path) {
		this.classPath = path.split(":");
	}

	public ParserGenerator() {
		this("nez/lib");
		loadExtension("nez.lang.schema.DTDGrammarExtension");
		loadExtension("nez.lang.schema.CeleryGrammarExtension");
	}

	public final void loadExtension(String classPath) {
		try {
			loadExtension(Class.forName(classPath));
		} catch (ClassNotFoundException e) {
			Verbose.traceException(e);
		}
	}

	public final void loadExtension(Class<?> c) {
		try {
			GrammarExtension ext = (GrammarExtension) c.getDeclaredConstructor().newInstance();
			ext.init(this);
			extensionMap.put(ext.getExtension(), ext);
			Verbose.println("added GrammarExtension : " + ext.getClass().getName());
		} catch (Exception e) {
			Verbose.traceException(e);
		}
	}

	public final Grammar newGrammar(Source source, String ext) throws IOException {
		Grammar grammar = new Grammar(ext);
		updateGrammar(grammar, source, ext);
		return grammar;
	}

	public final Grammar loadGrammar(String fileName) throws IOException {
		Grammar grammar = new Grammar(FileBuilder.extractFileExtension(fileName));
		grammar.setURN(fileName);
		Source source = StringSource.loadClassPath(fileName, classPath);
		String ext = FileBuilder.extractFileExtension(fileName);
		updateGrammar(grammar, source, ext);
		grammar.setDesc(GrammarLoader.parseGrammarDescription(source));
		return grammar;
	}

	public final void updateGrammar(Grammar grammar, String fileName) throws IOException {
		CommonSource source = StringSource.loadClassPath(fileName, classPath);
		String ext = FileBuilder.extractFileExtension(fileName);
		updateGrammar(grammar, source, ext);
	}

	public final void updateGrammar(Grammar grammar, Source source, String ext) throws IOException {
		GrammarExtension grammarExtention = lookupGrammarExtension(ext);
		Parser parser = grammarExtention.getParser();
		Tree<?> node = parser.parse(source);
		parser.ensureNoErrors();
		GrammarLoader loader = new GrammarLoader(grammar, ParserStrategy.newDefaultStrategy());
		grammarExtention.updateGrammarLoader(loader);
		loader.load(node);
	}

	/* parsedTree */

	public final Grammar newGrammar(Tree<?> parsedTree, String ext) throws IOException {
		Grammar grammar = new Grammar(ext);
		updateGrammar(grammar, parsedTree, ext);
		return grammar;
	}

	public final void updateGrammar(Grammar grammar, Tree<?> parsedTree, String ext) throws IOException {
		GrammarExtension grammarExtention = lookupGrammarExtension(ext);
		GrammarLoader loader = new GrammarLoader(grammar, ParserStrategy.newDefaultStrategy());
		grammarExtention.updateGrammarLoader(loader);
		loader.load(parsedTree);
	}

	/* Utils */

	private GrammarExtension lookupGrammarExtension(String fileExtension) throws IOException {
		GrammarExtension ext = getGrammarExtension(fileExtension);
		if (ext == null) {
			if (!fileExtension.equals("nez")) {
				throw new ParserException("undefined grammar extension: " + fileExtension);
			}
			class P extends GrammarExtension {
				P(ParserGenerator factory) {
					super(null);
					init(factory);
				}

				@Override
				public Parser getParser() {
					if (parser == null) {
						Grammar g = new Grammar("nez");
						parser = new NezGrammarCombinator().load(g, "File").newParser(ParserStrategy.newSafeStrategy());
					}
					return parser;
				}

				@Override
				public String getExtension() {
					return "nez";
				}

				@Override
				public void updateGrammarLoader(GrammarLoader loader) {

				}
			}
			ext = new P(this);
			extensionMap.put(ext.getExtension(), ext);
		}
		return ext;
	}

	/* Parser */

	private GrammarExtension getGrammarExtension(String fileExtension) {
		GrammarExtension ext = extensionMap.get(fileExtension);
		if (ext != null) {
			ext = ext.newState();
		}
		return ext;
	}

	public final Parser newParser(String fileName, ParserStrategy strategy) throws IOException {
		return ParserStrategy.nullCheck(strategy).newParser(loadGrammar(fileName));
	}

	public final Parser newParser(String fileName) throws IOException {
		return newParser(fileName, ParserStrategy.newDefaultStrategy());
	}
}
