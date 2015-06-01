package nez.lang;

import java.util.TreeMap;

import nez.ast.SourcePosition;
import nez.util.StringUtils;
import nez.util.UFlag;
import nez.util.UList;
import nez.util.UMap;
import nez.vm.Instruction;
import nez.vm.NezCompiler;

public class Sequence extends Multinary {
	Sequence(SourcePosition s, UList<Expression> l) {
		super(s, l, l.size());
	}
	@Override
	public String getPredicate() {
		return "seq";
	}	
	@Override
	public String key() {
		return " ";
	}
	
	@Override
	protected final void format(StringBuilder sb) {
		for(int i = 0; i < this.size(); i++) {
			if(i > 0) {
				sb.append(" ");
			}
			int n = appendAsString(sb, i);
			if(n > i) {
				i = n;
				continue;
			}
			Expression e = this.get(i);
			if(e instanceof Choice || e instanceof Sequence) {
				sb.append("( ");
				e.format(sb);
				sb.append(" )");
				continue;
			}
			e.format(sb);
		}
	}

	private int appendAsString(StringBuilder sb, int start) {
		int end = this.size();
		String s = "";
		for(int i = start; i < end; i++) {
			Expression e = this.get(i);
			if(e instanceof ByteChar) {
				char c = (char)(((ByteChar) e).byteChar);
				if(c >= ' ' && c < 127) {
					s += c;
					continue;
				}
			}
			end = i;
			break;
		}
		if(s.length() > 1) {
			sb.append(StringUtils.quoteString('\'', s, '\''));
		}
		return end - 1;
	}

	@Override
	public Expression reshape(GrammarReshaper m) {
		return m.reshapeSequence(this);
	}

	@Override
	public boolean isConsumed(Stacker stacker) {
		for(Expression e: this) {
			if(e.isConsumed(stacker)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean checkAlwaysConsumed(GrammarChecker checker, String startNonTerminal, UList<String> stack) {
		for(Expression e: this) {
			if(e.checkAlwaysConsumed(checker, startNonTerminal, stack)) {
				return true;
			}
		}
		return false;
	}
	@Override
	boolean setOuterLefted(Expression outer) { 
		for(Expression e: this) {
			if(e.setOuterLefted(outer)) {
				return true;
			}
		}
		return false;
	}
	@Override
	public int inferTypestate(UMap<String> visited) {
		for(Expression e: this) {
			int t = e.inferTypestate(visited);
			if(t == Typestate.ObjectType || t == Typestate.OperationType) {
				return t;
			}
		}
		return Typestate.BooleanType;
	}

	@Override
	public short acceptByte(int ch, int option) {
		return Prediction.acceptSequence(this, ch, option);
	}

	public final boolean isMultiChar() {
		return this.isMultiChar(0, this.size());
	}

	public final boolean isMultiChar(int start, int end) {
		for(int i = start; i < end; i++) {
			Expression p = this.get(i);
			if(!(p instanceof ByteChar)) {
				return false;
			}
		}
		return true;
	}
	
	public final byte[] extractMultiChar(int start, int end) {
		for(int i = start; i < end; i++) {
			Expression p = this.get(i);
			if(!(p instanceof ByteChar)) {
				end = i;
				break;
			}
		}
		byte[] b = new byte[end - start];
		for(int i = start; i < end; i++) {
			Expression p = this.get(i);
			if(p instanceof ByteChar) {
				b[i - start] = (byte)((ByteChar) p).byteChar;
			}
		}
		return b;
	}
	
	@Override
	void optimizeImpl(int option) {
		if(UFlag.is(option, Grammar.Optimization) && this.get(this.size() - 1) instanceof AnyChar) {
			boolean byteMap[] = ByteMap.newMap(false);
			if(isByteMap(option, byteMap)) {
				this.optimized = GrammarFactory.newByteMap(s, byteMap);
				return;
			}
			// (!'ab' !'ac' .) => (^[a]) / (!'ab' !'ac' .)
			if(UFlag.is(option, Grammar.Prediction)) {
				ByteMap.clear(byteMap);
				if(isPredictedNotByteMap(0, this.size() - 1, byteMap, option)) {
					this.optimized = GrammarFactory.newChoice(s, GrammarFactory.newByteMap(s, byteMap), this);
					return;
				}
			}
		}
		if(UFlag.is(option, Grammar.DFA) && needsReplaceOperation(option)) {
			this.optimized = operationReplacedSequence(option);
			//System.out.println("replaced: " + this + "\n => " + this.optimized);
		}
		else {
			this.optimized = this;
		}
	}

	private boolean needsReplaceOperation(int option) {
		for(int i = 1; i < this.size(); i++) {
			Expression p = this.get(i-1).optimize(option);
			Expression e = this.get(i).optimize(option);
			if(Expression.isByteConsumed(e)) {
				if(Expression.isPositionIndependentOperation(p) || p instanceof New || p instanceof Capture) {
					return true;
				}
			}
		}
		return false;
	}

	private Expression operationReplacedSequence(int option) {
		UList<Expression> l = this.toList();
		for(int i = 1; i < l.size(); i++) {
			Expression p = l.ArrayValues[i-1].optimize(option);
			Expression e = l.ArrayValues[i].optimize(option);
			if(Expression.isByteConsumed(e)) {
				if(Expression.isPositionIndependentOperation(p)) {
					l.ArrayValues[i-1] = e;
					l.ArrayValues[i]   = p;
					continue;
				}
				if(p instanceof New) {
					New n = (New)p;
					l.ArrayValues[i-1] = e;
					if(n.isInterned()) {
						l.ArrayValues[i] =  GrammarFactory.newNew(n.s, n.lefted, n.shift - 1);
					}
					else {
						n.shift -= 1;
						l.ArrayValues[i]   = n;
					}
					continue;
				}
				if(p instanceof Capture) {
					Capture n = (Capture)p;
					l.ArrayValues[i-1] = e;
					if(n.isInterned()) {
						l.ArrayValues[i] =  GrammarFactory.newCapture(n.s, n.shift - 1);
					}
					else {
						n.shift -= 1;
						l.ArrayValues[i]   = n;
					}
					continue;
				}
			}
		}
		return GrammarFactory.newSequence(s, l);
	}

	
	boolean isByteMap(int option, boolean[] byteMap) {
		for(int i = 0; i < this.size() - 1; i++) {
			Expression p = this.get(i).optimize(option);
			if(p instanceof Not) {
				p = p.get(i).optimize(option);
				if(p instanceof ByteChar) {
					byteMap[((ByteChar) p).byteChar] = true;
					continue;
				}
				if(p instanceof ByteMap) {
					ByteMap.appendBitMap(byteMap, ((ByteMap) p).byteMap);
					continue;
				}
			}
			return false;
		}
		ByteMap.reverse(byteMap, option);
		return true;
	}

	
	boolean isPredictedNotByteMap(int start, int end, boolean[] byteMap, int option) {
		for(int i = start; i < end; i++) {
			Expression p = this.get(i); //.optimize(option);
			if(p instanceof Not) {
				p = p.get(i).optimize(option);
				predictByte(p, byteMap, option);
				continue;
			}
			return false;
		}
		ByteMap.reverse(byteMap, option);
		return true;
	}

	void predictByte(Expression e, boolean[] byteMap, int option) {
		for(int c = 0; c < 256; c++) {
			if(e.acceptByte(c, option) != Prediction.Reject) {
				byteMap[c] = true;
			}
		}
	}
	
	@Override
	public Instruction encode(NezCompiler bc, Instruction next, Instruction failjump) {
		return bc.encodeSequence(this, next, failjump);
	}
	
	@Override
	protected int pattern(GEP gep) {
		int max = 0;
		for(Expression p: this) {
			int c = p.pattern(gep);
			if(c > max) {
				max = c;
			}
		}
		return max;
	}
	@Override
	protected void examplfy(GEP gep, StringBuilder sb, int p) {
		for(Expression e: this) {
			e.examplfy(gep, sb, p);
		}
	}


}