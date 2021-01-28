package nez.tool.peg;

import java.util.List;

import nez.lang.Expression;
import nez.lang.Expressions;
import nez.lang.Grammar;
import nez.lang.Nez;
import nez.lang.NonTerminal;
import nez.lang.Production;
import nez.parser.Parser;
import nez.tool.parser.ParserGrammarWriter;
import nez.util.FileBuilder;
import nez.util.StringUtils;

public class PEGTranslator extends ParserGrammarWriter {
	public PEGTranslator() {
		super(".peg");
	}

	@Override
	public void generate() {
		new PEGTranslatorVisitor(file, parser).generate();
	}

	static class PEGTranslatorVisitor extends GrammarTranslatorVisitor {

		public PEGTranslatorVisitor(FileBuilder file, Parser parser) {
			super(file, parser);
		}

		@Override
		public void visitProduction(Grammar gg, Production p) {
			Expression e = p.getExpression();
			L(name(p.getLocalName()));
			Begin("");
			L("<- ");
			if (e instanceof Nez.Choice) {
				for (int i = 0; i < e.size(); i++) {
					if (i > 0) {
						L("/ ");
					}
					visitExpression(e.get(i));
				}
			} else {
				visitExpression(e);
			}
			End("");
		}

		@Override
		public void visitEmpty(Expression p) {
			W("''");
		}

		@Override
		public void visitFail(Expression p) {
			W("!''");
		}

		@Override
		public void visitAny(Nez.Any p) {
			W(".");
		}

		@Override
		public void visitByte(Nez.Byte p) {
			W(StringUtils.stringfyByte(p.byteChar));
		}

		@Override
		public void visitByteSet(Nez.ByteSet p) {
			W(StringUtils.stringfyByteSet(p.byteset));
		}

		@Override
		public void visitString(Nez.MultiByte p) {
			W(p.toString()); // FIXME
		}

		protected void visitUnary(String prefix, Nez.Unary e, String suffix) {
			if (prefix != null) {
				W(prefix);
			}
			Expression inner = e.get(0);
			if (inner instanceof Nez.Choice || inner instanceof Nez.Sequence) {
				W("(");
				visitExpression(e.get(0));
				W(")");
			} else {
				visitExpression(e.get(0));
			}
			if (suffix != null) {
				W(suffix);
			}
		}

		@Override
		public void visitOption(Nez.Option e) {
			visitUnary(null, e, "?");
		}

		@Override
		public void visitZeroMore(Nez.ZeroMore e) {
			visitUnary(null, e, "*");
		}

		@Override
		public void visitOneMore(Nez.OneMore e) {
			visitUnary(null, e, "+");
		}

		@Override
		public void visitAnd(Nez.And e) {
			visitUnary("&", e, null);
		}

		@Override
		public void visitNot(Nez.Not e) {
			visitUnary("!", e, null);
		}

		@Override
		public void visitPair(Nez.Pair p) {
			int c = 0;
			List<Expression> l = Expressions.flatten(p);
			for (Expression e : l) {
				if (c > 0) {
					W(" ");
				}
				if (e instanceof Nez.Choice) {
					W("(");
					visitExpression(e);
					W(")");
				} else {
					visitExpression(e);
				}
				c++;
			}
		}

		@Override
		public void visitChoice(Nez.Choice p) {
			for (int i = 0; i < p.size(); i++) {
				if (i > 0) {
					W(" / ");
				}
				visitExpression(p.get(i));
			}
		}

		@Override
		public void visitNonTerminal(NonTerminal p) {
			W(name(p.getProduction()));
		}

		protected void SemanticAction(String code) {
			W("{");
			W(code);
			W("} ");
		}

		protected void SemanticPredicate(String code) {
			W("&{");
			W(code);
			W("} ");
		}

		@Override
		public void visitLink(Nez.LinkTree p) {
			SemanticAction("start()");
			visitExpression(p.get(0));
			SemanticAction("commit()");
		}

		@Override
		public void visitPreNew(Nez.BeginTree p) {
			SemanticAction("new()");
		}

		@Override
		public void visitLeftFold(Nez.FoldTree p) {
			SemanticAction("lfold(" + p.label + ")");
		}

		@Override
		public void visitNew(Nez.EndTree p) {
			SemanticAction("capture()");
		}

		@Override
		public void visitTag(Nez.Tag p) {
			SemanticAction("tag(" + StringUtils.quoteString('"', p.tag.toString(), '"') + ")");
		}

		@Override
		public void visitReplace(Nez.Replace p) {
			SemanticAction("replace(" + StringUtils.quoteString('"', p.value, '"') + ")");
		}

		@Override
		public void visitDetree(Nez.Detree p) {
			SemanticAction("start()");
			visitExpression(p.get(0));
			SemanticAction("abort()");
		}

		@Override
		public void visitBlockScope(Nez.BlockScope p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitLocalScope(Nez.LocalScope p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitSymbolAction(Nez.SymbolAction p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitSymbolExists(Nez.SymbolExists p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitSymbolMatch(Nez.SymbolMatch p) {
			SemanticPredicate("match(" + p.tableName + ")");
		}

		@Override
		public void visitSymbolPredicate(Nez.SymbolPredicate p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitIf(Nez.IfCondition p) {
			// TODO Auto-generated method stub

		}

		@Override
		public void visitOn(Nez.OnCondition p) {
			// TODO Auto-generated method stub

		}

	}
}
