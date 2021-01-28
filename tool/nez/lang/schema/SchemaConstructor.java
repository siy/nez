package nez.lang.schema;

import nez.ast.Symbol;
import nez.ast.Tree;
import nez.lang.Grammar;
import nez.lang.ast.GrammarVisitorMap;
import nez.parser.ParserStrategy;

public abstract class SchemaConstructor extends GrammarVisitorMap<SchemaTransducer> {
	public SchemaConstructor(Grammar grammar, ParserStrategy strategy) {
		super(grammar, strategy);
	}

	public Schema newSchema(Tree<?> node) {
		return find(key(node)).accept(node);
	}

}

interface SchemaTransducer {
	Schema accept(Tree<?> node);
}

interface SchemaSymbol {
	Symbol _Key = Symbol.unique("key");
	Symbol _Value = Symbol.unique("value");
	Symbol _Member = Symbol.unique("member");
	Symbol _Name = Symbol.unique("name");
	Symbol _Type = Symbol.unique("type");
	Symbol _List = Symbol.unique("list");
}
