package nez.lang.ast;

import nez.ast.Symbol;

public interface NezSymbols {
	Symbol _name = Symbol.unique("name");
	Symbol _expr = Symbol.unique("expr");
	Symbol _symbol = Symbol.unique("symbol");
	Symbol _mask = Symbol.unique("mask"); // <scanf >
	Symbol _hash = Symbol.unique("hash"); // example
	Symbol _name2 = Symbol.unique("name2"); // example
	Symbol _text = Symbol.unique("text"); // example
	Symbol _case = Symbol.unique("case");

	Symbol _String = Symbol.unique("String");
	Symbol _Integer = Symbol.unique("Integer");
	Symbol _List = Symbol.unique("List");
	Symbol _Name = Symbol.unique("Name");
	Symbol _Format = Symbol.unique("Format");
	Symbol _Class = Symbol.unique("Class");

	Symbol _anno = Symbol.unique("anno");

}
