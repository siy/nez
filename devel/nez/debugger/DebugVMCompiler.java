package nez.debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import nez.ast.CommonTree;
import nez.junks.ParserGrammar;
import nez.lang.Bytes;
import nez.lang.Expression;
import nez.lang.Expressions;
import nez.lang.FunctionName;
import nez.lang.Nez;
import nez.lang.Nez.Dispatch;
import nez.lang.Nez.Label;
import nez.lang.Nez.Repeat;
import nez.lang.Nez.Scan;
import nez.lang.Nez.Sequence;
import nez.lang.NonTerminal;
import nez.lang.Production;
import nez.parser.ParserStrategy;
import nez.parser.vm.MozInst;

public class DebugVMCompiler extends Expression.Visitor {
	ParserGrammar peg;
	IRBuilder builder;
	GrammarAnalyzer analyzer;
	HashMap<Expression, DebugVMInstruction> altInstructionMap = new HashMap<>();
	ParserStrategy strategy;

	public DebugVMCompiler(ParserStrategy option) {
		this.strategy = option;
		this.builder = new IRBuilder(new Module());
	}

	public Module compile(ParserGrammar grammar) {
		builder.setGrammar(grammar);
		this.analyzer = new GrammarAnalyzer(grammar);
		analyzer.analyze();
		for (Production p : grammar) {
			visitProduction(p);
		}

		return builder.buildInstructionSequence();
	}

	public Module getModule() {
		return builder.getModule();
	}

	public MozInst visitProduction(Production p) {
		builder.setFunction(new Function(p));
		builder.setInsertPoint(new BasicBlock());
		BasicBlock fbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		p.getExpression().visit(this, null);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIret(p);
		return null;
	}

	public MozInst visitExpression(Expression e, Object next) {
		return (MozInst) e.visit(this, next);
	}

	ArrayList<Byte> charList = new ArrayList<>();

	public boolean optimizeString(Nez.Pair seq) {
		for (Expression e : seq) {
			if (e instanceof Nez.Byte) {
				charList.add((byte) ((Nez.Byte) e).byteChar);
			} else if (e instanceof Sequence) {
				if (!optimizeString((Nez.Pair) e)) {
					return false;
				}
			} else {
				return false;
			}
		}
		return true;
	}

	boolean checkUnreachableChoice = true;

	public boolean optimizeCharSet(Nez.Choice p) {
		boolean[] map = Bytes.newMap(false);
		for (Expression e : p) {
			if (e instanceof Nez.Byte) {
				map[((Nez.Byte) e).byteChar] = true;
			} else if (e instanceof Nez.ByteSet) {
				Nez.ByteSet bmap = (Nez.ByteSet) e;
				for (int j = 0; j < bmap.byteset.length; j++) {
					if (bmap.byteset[j]) {
						map[j] = true;
					}
				}
			} else {
				return false;
			}
		}
		builder.createIcharclass(p, builder.jumpFailureJump(), map);
		return true;
	}

	@Override
	public MozInst visitNonTerminal(NonTerminal p, Object next) {
		BasicBlock rbb = new BasicBlock();
		builder.createIcall(p, rbb, builder.jumpFailureJump());
		builder.setInsertPoint(rbb);
		return null;
	}

	@Override
	public MozInst visitEmpty(Nez.Empty e, Object next) {
		return (MozInst) next;
	}

	@Override
	public MozInst visitFail(Nez.Fail p, Object next) {
		builder.createIfail(p);
		return null;
	}

	@Override
	public MozInst visitAny(Nez.Any p, Object next) {
		builder.createIany(p, builder.jumpFailureJump());
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitByte(Nez.Byte p, Object next) {
		builder.createIchar(p, builder.jumpFailureJump());
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitByteSet(Nez.ByteSet p, Object next) {
		builder.createIcharclass(p, builder.jumpFailureJump());
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitMultiByte(Nez.MultiByte p, Object next) {
		builder.createIstr(p, builder.jumpFailureJump(), p.byteseq);
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitOption(Nez.Option p, Object next) {
		BasicBlock fbb = new BasicBlock();
		BasicBlock mergebb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.createIpop(p);
		builder.createIjump(p, mergebb);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIsucc(p);
		builder.createIpeek(p);
		builder.createIpop(p);
		builder.setInsertPoint(mergebb);
		return null;
	}

	@Override
	public MozInst visitZeroMore(Nez.ZeroMore p, Object next) {
		BasicBlock topBB = new BasicBlock();
		builder.setInsertPoint(topBB);
		BasicBlock fbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.createIpop(p);
		builder.createIjump(p, topBB);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIsucc(p);
		builder.createIpeek(p);
		builder.createIpop(p);
		return null;
	}

	@Override
	public MozInst visitOneMore(Nez.OneMore p, Object next) {
		p.get(0).visit(this, next);
		BasicBlock topBB = new BasicBlock();
		builder.setInsertPoint(topBB);
		BasicBlock fbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.createIpop(p);
		builder.createIjump(p, topBB);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIsucc(p);
		builder.createIpeek(p);
		builder.createIpop(p);
		return null;
	}

	@Override
	public MozInst visitAnd(Nez.And p, Object next) {
		BasicBlock fbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIpeek(p);
		builder.createIpop(p);
		builder.createIiffail(p, builder.jumpFailureJump());
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitNot(Nez.Not p, Object next) {
		BasicBlock fbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.createIfail(p);
		builder.createIpeek(p);
		builder.createIpop(p);
		builder.createIjump(p, builder.jumpPrevFailureJump());
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIsucc(p);
		builder.createIpeek(p);
		builder.createIpop(p);
		return null;
	}

	@Override
	public MozInst visitPair(Nez.Pair p, Object next) {
		charList.clear();
		boolean opt = optimizeString(p);
		if (opt) {
			byte[] utf8 = new byte[charList.size()];
			for (int i = 0; i < utf8.length; i++) {
				utf8[i] = charList.get(i);
			}
			builder.createIstr(p, builder.jumpFailureJump(), utf8);
			return null;
		}
		for (Expression expression : p) {
			expression.visit(this, next);
		}
		return null;
	}

	@Override
	public Object visitSequence(Sequence p, Object next) {
		for (Expression expression : p) {
			expression.visit(this, next);
		}
		return null;
	}

	@Override
	public MozInst visitChoice(Nez.Choice p, Object next) {
		if (!optimizeCharSet(p)) {
			BasicBlock fbb;
			BasicBlock mergebb = new BasicBlock();
			builder.createIaltstart(p);
			builder.createIpush(p.get(0));
			for (int i = 0; i < p.size(); i++) {
				fbb = new BasicBlock();
				builder.pushFailureJumpPoint(fbb);
				altInstructionMap.put(p.get(i), builder.createIalt(p));
				p.get(i).visit(this, next);
				builder.createIaltend(p, i == p.size() - 1, i);
				builder.createIpop(p.get(i));
				builder.createIjump(p.get(i), mergebb);
				builder.setInsertPoint(builder.popFailureJumpPoint());
				if (i == p.size() - 1) {
					builder.createIpop(p.get(i));
					builder.createIaltfin(p);
				} else {
					builder.createIsucc(p.get(i + 1));
					builder.createIpeek(p.get(i + 1));
				}
			}
			builder.createIjump(p.get(p.size() - 1), builder.jumpFailureJump());
			builder.setInsertPoint(mergebb);
			builder.createIaltfin(p);
		}
		return null;
	}

	@Override
	public MozInst visitBeginTree(Nez.BeginTree p, Object next) {
		leftedStack.push(false);
		if (strategy.TreeConstruction) {
			builder.createInew(p);
		}
		return null;
	}

	@Override
	public MozInst visitFoldTree(Nez.FoldTree p, Object next) {
		leftedStack.push(true);
		if (strategy.TreeConstruction) {
			BasicBlock fbb = new BasicBlock();
			builder.pushFailureJumpPoint(fbb);
			builder.createImark(p);
			builder.createIleftnew(p);
		}

		return null;
	}

	Stack<Boolean> leftedStack = new Stack<>();

	@Override
	public MozInst visitLinkTree(Nez.LinkTree p, Object next) {
		if (strategy.TreeConstruction) {
			BasicBlock fbb = new BasicBlock();
			BasicBlock endbb = new BasicBlock();
			builder.pushFailureJumpPoint(fbb);
			builder.createImark(p);
			p.get(0).visit(this, next);
			builder.createIcommit(p);
			builder.createIjump(p, endbb);
			builder.setInsertPoint(builder.popFailureJumpPoint());
			builder.createIabort(p);
			builder.createIjump(p, builder.jumpFailureJump());
			builder.setInsertPoint(endbb);
		} else {
			p.get(0).visit(this, next);
		}
		return null;
	}

	@Override
	public MozInst visitEndTree(Nez.EndTree p, Object next) {
		/* newNode is used in the debugger for rich view */
		CommonTree node = (CommonTree) p.getSourceLocation();
		int len = node.toText().length();
		CommonTree newNode = new CommonTree(node.getTag(), node.getSource(), node.getSourcePosition() + len - 1, (int) (node.getSourcePosition() + len), 0, null);
		p = (Nez.EndTree) Expressions.newEndTree(newNode, p.shift);
		if (strategy.TreeConstruction) {
			if (leftedStack.pop()) {
				BasicBlock endbb = new BasicBlock();
				builder.createIcapture(p);
				builder.createIpop(p);
				builder.createIjump(p, endbb);
				builder.setInsertPoint(builder.popFailureJumpPoint());
				builder.createIabort(p);
				builder.createIjump(p, builder.jumpFailureJump());
				builder.setInsertPoint(endbb);
			} else {
				builder.createIcapture(p);
			}
		}
		return null;
	}

	@Override
	public MozInst visitTag(Nez.Tag p, Object next) {
		if (strategy.TreeConstruction) {
			builder.createItag(p);
		}
		return null;
	}

	@Override
	public MozInst visitReplace(Nez.Replace p, Object next) {
		if (strategy.TreeConstruction) {
			builder.createIreplace(p);
		}
		return null;
	}

	@Override
	public MozInst visitDetree(Nez.Detree p, Object next) {
		throw new RuntimeException("undifined visit method " + p.getClass());
	}

	@Override
	public MozInst visitBlockScope(Nez.BlockScope p, Object next) {
		BasicBlock fbb = new BasicBlock();
		BasicBlock endbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIbeginscope(p);
		p.get(0).visit(this, next);
		builder.createIendscope(p);
		builder.createIjump(p, endbb);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIendscope(p);
		builder.createIjump(p, builder.jumpFailureJump());
		builder.setInsertPoint(endbb);
		return null;
	}

	@Override
	public MozInst visitSymbolAction(Nez.SymbolAction p, Object next) {
		BasicBlock fbb = new BasicBlock();
		BasicBlock endbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIpush(p);
		p.get(0).visit(this, next);
		builder.createIdef(p);
		builder.createIjump(p, endbb);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIpop(p);
		builder.createIjump(p, builder.jumpFailureJump());
		builder.setInsertPoint(endbb);
		return null;
	}

	@Override
	public MozInst visitSymbolMatch(Nez.SymbolMatch p, Object next) {
		throw new RuntimeException("undifined visit method " + p.getClass());
	}

	@Override
	public MozInst visitSymbolPredicate(Nez.SymbolPredicate p, Object next) {
		if (p.op == FunctionName.is) {
			builder.createIis(p, builder.jumpFailureJump());
		} else {
			builder.pushFailureJumpPoint(new BasicBlock());
			builder.createIpush(p);
			p.get(0).visit(this, next);
			builder.setInsertPoint(builder.popFailureJumpPoint());
			builder.createIisa(p, builder.jumpFailureJump());
		}
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitSymbolExists(Nez.SymbolExists existsSymbol, Object next) {
		builder.createIexists(existsSymbol, builder.jumpFailureJump());
		builder.setInsertPoint(new BasicBlock());
		return null;
	}

	@Override
	public MozInst visitLocalScope(Nez.LocalScope localTable, Object next) {
		BasicBlock fbb = new BasicBlock();
		BasicBlock endbb = new BasicBlock();
		builder.pushFailureJumpPoint(fbb);
		builder.createIbeginlocalscope(localTable);
		localTable.get(0).visit(this, next);
		builder.createIendscope(localTable);
		builder.createIjump(localTable, endbb);
		builder.setInsertPoint(builder.popFailureJumpPoint());
		builder.createIendscope(localTable);
		builder.createIjump(localTable, builder.jumpFailureJump());
		builder.setInsertPoint(endbb);
		return null;
	}

	@Override
	public Object visitIf(Nez.IfCondition e, Object a) {
		// TODO Auto-generated method stub
		return a;
	}

	@Override
	public Object visitOn(Nez.OnCondition e, Object a) {
		// TODO Auto-generated method stub
		return a;
	}

	@Override
	public Object visitScan(Scan scanf, Object a) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitRepeat(Repeat e, Object a) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitLabel(Label e, Object a) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitDispatch(Dispatch e, Object a) {
		// TODO Auto-generated method stub
		return null;
	}

}
