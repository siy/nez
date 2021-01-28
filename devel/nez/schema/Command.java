package nez.schema;

import java.io.IOException;
import java.lang.reflect.Type;

import nez.ParserGenerator;
import nez.ast.Source;
import nez.ast.Tree;
import nez.lang.Grammar;
import nez.parser.ParserStrategy;
import nez.parser.io.CommonSource;
import nez.type.Schema;
import nez.type.Schema.ObjectType;
import nez.type.SchemaTransformer;

public class Command extends nez.main.Command {
	@Override
	public void exec() throws IOException {
		/* Setting required options */
		strategy.Optimization = false;
		Grammar grammar = getSpecifiedGrammar();
		SchemaTransformer conv = new SchemaTransformer();
		Schema schema = new Schema();
		Type topType = loadSchema(schema);
		grammar = conv.transform(grammar.getStartProduction(), schema, (ObjectType) topType);
		grammar.dump();
	}

	// for test, return top type
	public final Type loadSchema(Schema schema) throws IOException {
		ParserGenerator pg = new ParserGenerator();
		Grammar nezSchemaGrammar = pg.loadGrammar("./devel/nez/schema/nez-schema.nez");
		Source sc = CommonSource.newFileSource("./Person.schema");
		Tree<?> node = nezSchemaGrammar.newParser(ParserStrategy.newDefaultStrategy()).parse(sc);
		NezSchemaLoader loader = new NezSchemaLoader(schema);
		return loader.load(node);
	}
}
