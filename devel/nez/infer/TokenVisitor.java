package nez.infer;

import java.util.ArrayList;
import java.util.List;

import nez.ast.Symbol;
import nez.ast.Tree;
import nez.ast.TreeVisitorMap;
import nez.util.ConsoleUtils;

public class TokenVisitor extends TreeVisitorMap<InferenceVisitor> {
	List<TokenSequence> sequenceList;
	int currentSequenceNumber;
	int totalNumOfChunk;

	public TokenVisitor() {
		init(TokenVisitor.class, new Undefined());
		this.sequenceList = new ArrayList<>();
		this.currentSequenceNumber = 0;
	}

	public static class Undefined implements InferenceVisitor, InferenceTokenSymbol {
		@Override
		public void accept(Tree<?> node) {
			ConsoleUtils.println(node.formatSourceMessage("error", "unsupproted tag in PEG Learning System #" + node));
		}

		@Override
		public void accept(Tree<?> node, TokenSequence seq) {
			accept(node);
		}
	}

	private void visit(Tree<?> node) {
		find(node.getTag().toString()).accept(node);
	}

	private void visit(Tree<?> node, TokenSequence seq) {
		find(node.getTag().toString()).accept(node, seq);
	}

	public List<TokenSequence> parse(Tree<?> node) {
		this.totalNumOfChunk = node.size();
		for (Tree<?> chunk : node) {
			visit(chunk);
			currentSequenceNumber = 0;
		}
		return sequenceList;
	}

	public class Chunk extends Undefined {
		@Override
		public void accept(Tree<?> node) {
			extendSequenceList(node.size());
			for (Tree<?> seq : node) {
				visit(seq);
			}
		}
	}

	public class _Sequence extends Undefined {
		@Override
		public void accept(Tree<?> node) {
			TokenSequence seq = sequenceList.get(currentSequenceNumber++);
			for (Tree<?> tokenNode : node) {
				visit(tokenNode, seq);
			}
			seq.commitAllHistograms();
		}
	}

	public class _MetaToken extends Undefined {
		@Override
		public void accept(Tree<?> node) {
			TokenSequence seq = sequenceList.get(currentSequenceNumber++);
			String label = String.format("%s*%s", node.get(_open).toText(), node.get(_close).toText());
			seq.transaction(label, new MetaToken(label, totalNumOfChunk, node));
			seq.commitAllHistograms();
		}
	}

	public class Delim extends Undefined {
		@Override
		public void accept(Tree<?> node, TokenSequence seq) {
			String label = node.toText();
			seq.transaction(label, new DelimToken(label, totalNumOfChunk));
		}
	}

	public class SimpleToken extends Undefined {
		@Override
		public void accept(Tree<?> node, TokenSequence seq) {
			String label = node.getTag().toString();
			seq.transaction(label, new Token(label, totalNumOfChunk));
		}
	}

	public class _Integer extends SimpleToken {
	}

	public class _Float extends SimpleToken {
	}

	public class _String extends SimpleToken {
	}

	public class IPv6 extends SimpleToken {
	}

	public class IPv4 extends SimpleToken {
	}

	public class Email extends SimpleToken {
	}

	public class Path extends SimpleToken {
	}

	public class Date extends SimpleToken {
	}

	public class Time extends SimpleToken {
	}

	private void extendSequenceList(int newSize) {
		int listSize = sequenceList.size();
		while (newSize > listSize++) {
			sequenceList.add(new TokenSequence());
		}
	}

}

interface InferenceVisitor {
	void accept(Tree<?> node);

	void accept(Tree<?> node, TokenSequence seq);
}

interface InferenceTokenSymbol {
	Symbol _name = Symbol.unique("name");
	Symbol _value = Symbol.unique("value");
	Symbol _open = Symbol.unique("open");
	Symbol _close = Symbol.unique("close");
}
