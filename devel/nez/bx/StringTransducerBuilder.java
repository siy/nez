package nez.bx;

import nez.ast.Tree;

public interface StringTransducerBuilder {
	<E extends Tree<E>> StringTransducer lookup(Tree<E> sub);

	void write(String text);

	void writeNewLineIndent();

	void incIndent();

	void decIndent();
}
