package nez.tool.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nez.ast.Symbol;
import nez.lang.Bitmap;
import nez.lang.ByteConsumption;
import nez.lang.Expression;
import nez.lang.FunctionName;
import nez.lang.Grammar;
import nez.lang.Nez;
import nez.lang.Nez.ChoicePrediction;
import nez.lang.Nez.IfCondition;
import nez.lang.Nez.Label;
import nez.lang.Nez.LocalScope;
import nez.lang.Nez.OnCondition;
import nez.lang.Nez.SymbolAction;
import nez.lang.Nez.SymbolExists;
import nez.lang.Nez.SymbolMatch;
import nez.lang.Nez.SymbolPredicate;
import nez.lang.NonTerminal;
import nez.lang.Production;
import nez.lang.SymbolDependency;
import nez.lang.SymbolDependency.SymbolDependencyAnalyzer;
import nez.lang.SymbolMutation;
import nez.lang.SymbolMutation.SymbolMutationAnalyzer;
import nez.lang.Typestate;
import nez.lang.Typestate.TypestateAnalyzer;
import nez.parser.MemoPoint;
import nez.parser.ParserCode;
import nez.util.StringUtils;
import nez.util.UList;
import nez.util.Verbose;

public abstract class CommonParserGenerator extends ParserGrammarWriter {
	public CommonParserGenerator(String fileExt) {
		super(fileExt);
	}

	protected boolean UniqueNumberingSymbol = true;
	protected boolean SupportedSwitchCase = true;
	protected boolean UsingBitmap;
	protected boolean SupportedRange;
	protected boolean SupportedMatch2;
	protected boolean SupportedMatch3;
	protected boolean SupportedMatch4;
	protected boolean SupportedMatch5;
	protected boolean SupportedMatch6;
	protected boolean SupportedMatch7;
	protected boolean SupportedMatch8;

	//
	protected ParserCode<?> code;
	//
	protected ByteConsumption consumption = new ByteConsumption();
	protected TypestateAnalyzer typeState = Typestate.newAnalyzer();
	protected SymbolMutationAnalyzer symbolMutation = SymbolMutation.newAnalyzer();
	protected SymbolDependencyAnalyzer symbolDeps = SymbolDependency.newAnalyzer();

	@Override
	public void generate() {
		Grammar g = parser.getCompiledGrammar();
		this.code = parser.getParserCode();
		initLanguageSpec();

		generateHeader(g);
		SymbolAnalysis constDecl = new SymbolAnalysis();
		constDecl.decl(g.getStartProduction());
		sortFuncList(_funcname(g.getStartProduction()));
		generateSymbolTables();
		generatePrototypes();

		new ParserGeneratorVisitor().generate();
		generateFooter(g);
		file.writeNewLine();
		file.flush();
	}

	protected abstract void generateHeader(Grammar g);

	protected abstract void generateFooter(Grammar g);

	protected void generatePrototypes() {

	}

	protected String _funcname(Production p) {
		return _funcname(p.getUniqueName());
	}

	protected String _funcname(String uname) {
		return "p" + uname.replace("!", "NOT").replace("~", "_").replace("&", "AND");
	}

	/* Types */

	protected Map<String, String> typeMap = new HashMap<>();

	protected abstract void initLanguageSpec();

	protected void addType(String name, String type) {
		typeMap.put(name, type);
	}

	protected String type(String name) {
		return typeMap.get(name);
	}

	/* Symbols */

	protected Map<String, String> nameMap = new HashMap<>();

	protected UList<String> tagList = new UList<>(new String[8]);
	protected Map<String, Integer> tagMap = new HashMap<>();

	protected Map<String, Integer> labelMap = new HashMap<>();
	protected UList<String> labelList = new UList<>(new String[8]);

	protected UList<String> tableList = new UList<>(new String[8]);
	protected Map<String, Integer> tableMap = new HashMap<>();

	final String _set(boolean[] b) {
		String key = StringUtils.stringfyBitmap(b);
		return nameMap.get(key);
	}

	final String _range(boolean[] b) {
		String key = StringUtils.stringfyBitmap(b) + "*";
		return nameMap.get(key);
	}

	final void DeclSet(boolean[] b, boolean Iteration) {
//		if (Iteration && strategy.SSE) {
//			byte[] range = rangeSEE(b);
//			if (range != null) {
//				String key = StringUtils.stringfyBitmap(b) + "*";
//				String name = nameMap.get(key);
//				if (name == null) {
//					name = _range() + nameMap.size();
//					nameMap.put(key, name);
//					DeclConst(type("$range"), name, range.length, _initByteArray(range));
//				}
//				return;
//			}
//		}
		if (SupportedRange && range(b) != null) {
			return;
		}
		String key = StringUtils.stringfyBitmap(b);
		String name = nameMap.get(key);
		if (name == null) {
			name = _set() + nameMap.size();
			nameMap.put(key, name);
			DeclConst(type("$set"), name, UsingBitmap ? 8 : 256, _initBooleanArray(b));
		}
	}

	final String _index(byte[] b) {
		String key = key(b);
		return nameMap.get(key);
	}

	final void DeclIndex(byte[] b) {
		String key = key(b);
		String name = nameMap.get(key);
		if (name == null) {
			name = _index() + nameMap.size();
			nameMap.put(key, name);
			DeclConst(type("$index"), name, b.length, _initByteArray(b));
		}
	}

	private String key(byte[] b) {
		StringBuilder sb = new StringBuilder();
		for (byte c : b) {
			sb.append(c);
			sb.append(",");
		}
		return sb.toString();
	}

	protected String _text(byte[] text) {
		String key = new String(text);
		return nameMap.get(key);
	}

	protected String _text(String key) {
		if (key == null) {
			return _Null();
		}
		return nameMap.get(key);
	}

	final void DeclText(byte[] text) {
		String key = new String(text);
		String name = nameMap.get(key);
		if (name == null) {
			name = _text() + nameMap.size();
			nameMap.put(key, name);
			DeclConst(type("$text"), name, text.length, _initByteArray(text));
		}
	}

	private int[] range(boolean[] b) {
		int start = 0;
		for (int i = 0; i < 256; i++) {
			if (b[i]) {
				start = i;
				break;
			}
		}
		int end = 256;
		for (int i = start; i < 256; i++) {
			if (!b[i]) {
				end = i;
				break;
			}
		}
		for (int i = end; i < 256; i++) {
			if (b[i]) {
				return null;
			}
		}
		if (start < end) {
			return new int[]{start, end};
		}
		return null;
	}

	private byte[] rangeSEE(boolean[] b) {
		if (b[0]) {
			return null;
		}
		ArrayList<Integer> l = new ArrayList<>();
		for (int i = 0; i < 256; i++) {
			if (!b[i]) {
				int start = i;
				int end = start;
				for (int j = start; j < 256 && !b[j]; j++) {
					end = j;
				}
				l.add(start);
				l.add(end);
				i = end;
			}
		}
		if (l.size() <= 16) {
			byte[] res = new byte[l.size()];
			for (int i = 0; i < l.size(); i++) {
				res[i] = (byte) ((int) l.get(i));
			}
			return res;
		}
		return null;
	}

	private boolean isMatchText(byte[] t, int n) {
		if (t.length == n) {
			for (int i = 0; i < n; i++) {
				if (t[i] == 0) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	final void DeclMatchText(byte[] text) {
		if ((SupportedMatch2 && isMatchText(text, 2))
			|| (SupportedMatch3 && isMatchText(text, 3))
			|| (SupportedMatch4 && isMatchText(text, 4))
			|| (SupportedMatch5 && isMatchText(text, 5))
			|| (SupportedMatch6 && isMatchText(text, 6))
			|| (SupportedMatch7 && isMatchText(text, 7))
			|| (SupportedMatch8 && isMatchText(text, 8))) {
			return;
		}
		DeclText(text);
	}

	final String _tag(Symbol s) {
		if (!UniqueNumberingSymbol && s == null) {
			return _Null();
		}
		return _tagname(s == null ? "" : s.getSymbol());
	}

	final void DeclTag(String s) {
		if (!tagMap.containsKey(s)) {
			int n = tagMap.size();
			tagMap.put(s, n);
			tagList.add(s);
			DeclConst(type("$tag"), _tagname(s), _initTag(n, s));
		}
	}

	final String _label(Symbol s) {
		if (!UniqueNumberingSymbol && s == null) {
			return _Null();
		}
		return _labelname(s == null ? "" : s.getSymbol());
	}

	final void DeclLabel(String s) {
		if (!labelMap.containsKey(s)) {
			int n = labelMap.size();
			labelMap.put(s, n);
			labelList.add(s);
			if (UniqueNumberingSymbol || !s.equals("_")) {
				DeclConst(type("$label"), _labelname(s), _initLabel(n, s));
			}
		}
	}

	final String _table(Symbol s) {
		if (!UniqueNumberingSymbol && s.getSymbol().equals("")) {
			return _Null();
		}
		return _tablename(s == null ? "" : s.getSymbol());
	}

	final void DeclTable(Symbol t) {
		String s = t.getSymbol();
		if (!tableMap.containsKey(s)) {
			int n = tableMap.size();
			tableMap.put(s, n);
			tableList.add(s);
			DeclConst(type("$table"), _tablename(s), _initTable(n, s));
		}
	}

	final void generateSymbolTables() {
		if (UniqueNumberingSymbol) {
			generateSymbolTable("_tags", tagList);
			generateSymbolTable("_labels", labelList);
			generateSymbolTable("_tables", tableList);
		}
	}

	private void generateSymbolTable(String name, UList<String> l) {
		if (l.size() > 0) {
			DeclConst(type("$string"), name, l.size(), _initStringArray(l.ArrayValues, l.size()));
		}
	}

	protected String _basename() {
		return fileBase;
	}

	protected String _ns() {
		return fileBase + "_";
	}

	protected String _quote(String s) {
		if (s == null) {
			return "\"\"";
		}
		return StringUtils.quoteString('"', s, '"');
	}

	protected String _initBooleanArray(boolean[] b) {
		StringBuilder sb = new StringBuilder();
		sb.append(_BeginArray());
		if (UsingBitmap) {
			Bitmap bits = new Bitmap();
			for (int i = 0; i < 256; i++) {
				if (b[i]) {
					bits.set(i, true);
					assert (bits.is(i));
				}
			}
			for (int i = 0; i < 8; i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(_hex(bits.n(i)));
			}
		} else {
			for (int i = 0; i < 256; i++) {
				if (i > 0) {
					sb.append(",");
				}
				if (b[i]) {
					sb.append(_True());
				} else {
					sb.append(_False());
				}
			}
		}
		sb.append(_EndArray());
		return sb.toString();
	}

	protected String _initByteArray(byte[] b) {
		StringBuilder sb = new StringBuilder();
		sb.append(_BeginArray());
		for (int i = 0; i < b.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(_int(b[i] & 0xff));
		}
		sb.append(_EndArray());
		return sb.toString();
	}

	protected String _initStringArray(String[] a, int size) {
		StringBuilder sb = new StringBuilder();
		sb.append(_BeginArray());
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(_quote(a[i]));
		}
		sb.append(_EndArray());
		return sb.toString();
	}

	protected String _tagname(String name) {
		return "_T" + name;
	}

	protected String _labelname(String name) {
		return "_L" + name;
	}

	protected String _tablename(String name) {
		return "_S" + name;
	}

	protected String _initTag(int id, String s) {
		return UniqueNumberingSymbol ? "" + id : _quote(s);
	}

	protected String _initLabel(int id, String s) {
		return UniqueNumberingSymbol ? "" + id : _quote(s);
	}

	protected String _initTable(int id, String s) {
		return UniqueNumberingSymbol ? "" + id : _quote(s);
	}

	/* function */
	Map<String, String> exprMap = new HashMap<>();
	Map<String, Expression> funcMap = new HashMap<>();
	List<String> funcList = new ArrayList<>();
	Set<String> crossRefNames = new HashSet<>();
	Map<String, Integer> memoPointMap = new HashMap<>();

	private String _funcname(Expression e) {
		if (e instanceof NonTerminal) {
			return _funcname(((NonTerminal) e).getUniqueName());
		}
		String key = e.toString();
		return exprMap.get(key);
	}

	Map<String, Set<String>> nodes = new HashMap<>();

	private void addEdge(String sour, String dest) {
		if (sour != null) {
			nodes.computeIfAbsent(sour, k -> new HashSet<>()).add(dest);
		}
	}

	void sortFuncList(String start) {
		TopologicalSorter sorter = new TopologicalSorter(nodes, crossRefNames);
		funcList = sorter.getResult();
		if (!funcList.contains(start)) {
			funcList.add(start);
		}
		nodes.clear();
	}

	private class SymbolAnalysis extends Expression.Visitor {

		SymbolAnalysis() {
			DeclTag("");
			DeclLabel("");
			DeclTable(Symbol.Null);
		}

		private Object decl(Production p) {
			if (checkFuncName(p)) {
				p.getExpression().visit(this, null);
			}
			return null;
		}

		String cur; // name

		private boolean checkFuncName(Production p) {
			String f = _funcname(p.getUniqueName());
			if (!funcMap.containsKey(f)) {
				String stacked2 = cur;
				cur = f;
				funcMap.put(f, p.getExpression());
				funcList.add(f);
				MemoPoint memoPoint = code.getMemoPoint(p.getUniqueName());
				if (memoPoint != null) {
					memoPointMap.put(f, memoPoint.id);
					String stacked = cur;
					cur = f;
					checkInner(p.getExpression());
					cur = stacked;
					addEdge(cur, f);
				} else {
					p.getExpression().visit(this, null);
				}
				cur = stacked2;
				addEdge(cur, f);
				return true;
			}
			addEdge(cur, f);
			return false;
		}

		private void checkInner(Expression e) {
			if (e instanceof NonTerminal) {
				e.visit(this, null);
				return;
			}
			String key = e.toString();
			String f = exprMap.get(key);
			if (f == null) {
				f = "e" + exprMap.size();
				exprMap.put(key, f);
				funcList.add(f);
				funcMap.put(f, e);
				String stacked = cur;
				cur = f;
				e.visit(this, null);
				cur = stacked;
			}
			addEdge(cur, f);
		}

		private void checkNonLexicalInner(Expression e) {
			if (strategy.Olex) {
				if (e instanceof Nez.Byte || e instanceof Nez.ByteSet || e instanceof Nez.MultiByte || e instanceof Nez.Any) {
					e.visit(this, null);
					return;
				}
			}
			checkInner(e);
		}

		private Object visitAll(Expression e) {
			for (Expression sub : e) {
				sub.visit(this, null);
			}
			return null;
		}

		@Override
		public Object visitNonTerminal(NonTerminal e, Object a) {
			return decl(e.getProduction());
		}

		@Override
		public Object visitEmpty(Nez.Empty e, Object a) {
			return null;
		}

		@Override
		public Object visitFail(Nez.Fail e, Object a) {
			return null;
		}

		@Override
		public Object visitByte(Nez.Byte e, Object a) {
			return null;
		}

		@Override
		public Object visitByteSet(Nez.ByteSet e, Object a) {
			DeclSet(e.byteset, false);
			return null;
		}

		@Override
		public Object visitAny(Nez.Any e, Object a) {
			return null;
		}

		@Override
		public Object visitMultiByte(Nez.MultiByte e, Object a) {
			DeclMatchText(e.byteseq);
			return null;
		}

		@Override
		public Object visitPair(Nez.Pair e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitSequence(Nez.Sequence e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitChoice(Nez.Choice e, Object a) {
			if (e.predicted != null) {
				DeclIndex(e.predicted.indexMap);
			}
			if (e.size() == 1) {
				// single selection
				e.get(0).visit(this, a);
			} else {
				for (Expression sub : e) {
					checkInner(sub);
				}
			}
			return null;
		}

		@Override
		public Object visitDispatch(Nez.Dispatch e, Object a) {
			DeclIndex(e.indexMap);
			for (Expression sub : e) {
				checkInner(sub);
			}
			return null;
		}

		@Override
		public Object visitOption(Nez.Option e, Object a) {
			checkNonLexicalInner(e.get(0));
			return null;
		}

		@Override
		public Object visitZeroMore(Nez.ZeroMore e, Object a) {
			if (strategy.Olex && e.get(0) instanceof Nez.ByteSet) {
				DeclSet(((Nez.ByteSet) e.get(0)).byteset, true);
				return null;
			}
			checkNonLexicalInner(e.get(0));
			return null;
		}

		@Override
		public Object visitOneMore(Nez.OneMore e, Object a) {
			if (strategy.Olex && e.get(0) instanceof Nez.ByteSet) {
				DeclSet(((Nez.ByteSet) e.get(0)).byteset, true);
				return null;
			}
			checkNonLexicalInner(e.get(0));
			return null;
		}

		@Override
		public Object visitAnd(Nez.And e, Object a) {
			checkNonLexicalInner(e.get(0));
			return null;
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			checkNonLexicalInner(e.get(0));
			return null;
		}

		@Override
		public Object visitBeginTree(Nez.BeginTree e, Object a) {
			return null;
		}

		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			if (e.tag != null) {
				DeclTag(e.tag.getSymbol());
			}
			if (e.value != null) {
				DeclText(StringUtils.utf8(e.value));
			}
			return null;
		}

		@Override
		public Object visitFoldTree(Nez.FoldTree e, Object a) {
			if (e.label != null) {
				DeclLabel(e.label.getSymbol());
			}
			return visitAll(e);
		}

		@Override
		public Object visitLinkTree(Nez.LinkTree e, Object a) {
			if (e.label != null) {
				DeclLabel(e.label.getSymbol());
			}
			return visitAll(e);
		}

		@Override
		public Object visitTag(Nez.Tag e, Object a) {
			DeclTag(e.tag.getSymbol());
			return null;
		}

		@Override
		public Object visitReplace(Nez.Replace e, Object a) {
			DeclText(StringUtils.utf8(e.value));
			return null;
		}

		@Override
		public Object visitDetree(Nez.Detree e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitBlockScope(Nez.BlockScope e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitLocalScope(LocalScope e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitSymbolAction(SymbolAction e, Object a) {
			DeclTable(e.tableName);
			return visitAll(e);
		}

		@Override
		public Object visitSymbolPredicate(SymbolPredicate e, Object a) {
			DeclTable(e.tableName);
			return visitAll(e);
		}

		@Override
		public Object visitSymbolMatch(SymbolMatch e, Object a) {
			DeclTable(e.tableName);
			return visitAll(e);
		}

		@Override
		public Object visitSymbolExists(SymbolExists e, Object a) {
			DeclTable(e.tableName);
			if (e.symbol != null) {
				DeclText(StringUtils.utf8(e.symbol));
			}
			return visitAll(e);
		}

		@Override
		public Object visitScan(Nez.Scan e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitRepeat(Nez.Repeat e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitIf(IfCondition e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitOn(OnCondition e, Object a) {
			return visitAll(e);
		}

		@Override
		public Object visitLabel(Label e, Object a) {
			return visitAll(e);
		}
	}

	class ParserGeneratorVisitor extends Expression.Visitor {
		void generate() {
			for (String f : funcList) {
				generateFunction(f, funcMap.get(f));
			}
		}

		private String _eval(Expression e) {
			return _eval(e, _pErr());
		}

		private String _eval(Expression e, String expr) {
			String f = _funcname(e);
			if (f == null) {
				return null;
			}
			return _funccall(f, expr);
		}

		private String _eval(String uname, String expr) {
			return _funccall(_funcname(uname), expr);
		}

		private void generateFunction(String name, Expression e) {
			Integer memoPoint = memoPointMap.get(name);
			NewLine();
			Verbose("");
			Verbose("Function which matches following rule: " + e.toString());
			Verbose("");
			initLocal();
			BeginFunc(name);
			{
				if (memoPoint != null) {
					String memoLookup = "memoLookupStateTree";
					String memoSucc = "memoStateTreeSucc";
					String memoFail = "memoStateFail";
					if (!typeState.isTree(e)) {
						memoLookup = memoLookup.replace("Tree", "");
						memoSucc = memoSucc.replace("Tree", "");
						memoFail = memoFail.replace("Tree", "");
					}
					if (!strategy.StatefulPackratParsing || !symbolDeps.isDependent(e)) {
						memoLookup = memoLookup.replace("State", "");
						memoSucc = memoSucc.replace("State", "");
						memoFail = memoFail.replace("State", "");
					}
					InitVal("memo", _Func(memoLookup, _int(memoPoint)));
					If("memo", _Eq(), "0");
					{
						String f = _eval(e);
						String[] n = SaveState(e);
						If(f);
						{
							Statement(_Func(memoSucc, _int(memoPoint), n[0]));
							Succ();
						}
						Else();
						{
							ReportError(891, _pErr(), name + " (" + e.toString() + ") {"  + name + "}");

							BackState(e, n);
							Statement(_Func(memoFail, _int(memoPoint)));
							Fail();
						}
						EndIf();
					}
					EndIf();
					Return(_Binary("memo", _Eq(), "1"));
				} else {
					visit(e, name);
					Succ();
				}
			}
			EndFunc();
		}

		int nested = -1;

		private void visit(Expression e, Object a) {
			int lnested = nested;
			this.nested++;
			e.visit(this, a);
			this.nested--;
			this.nested = lnested;
		}

		protected void BeginScope() {
			if (nested > 0) {
				BeginLocalScope();
			}
		}

		protected void EndScope() {
			if (nested > 0) {
				EndLocalScope();
			}
		}

		Map<String, String> localMap;

		private void initLocal() {
			localMap = new HashMap<>();
		}

		private String local(String name) {
			if (!localMap.containsKey(name)) {
				localMap.put(name, name);
				return name;
			}
			return local(name + localMap.size());
		}

		private String InitVal(String name, String expr) {
			String type = type(name);
			String lname = local(name);
			VarDecl(type, lname, expr);
			return lname;
		}

		private String SavePos() {
			return InitVal(_pos(), _Field(_state(), "pos"));
		}

		private void BackPos(String lname) {
			VarAssign(_Field(_state(), "pos"), lname);
		}

		private String SaveTree() {
			return InitVal(_tree(), _Func("saveTree"));
		}

		private void BackTree(String lname) {
			Statement(_Func("backTree", lname));
		}

		private String SaveLog() {
			return InitVal(_log(), _Func("saveLog"));
		}

		private void BackLog(String lname) {
			Statement(_Func("backLog", lname));
		}

		private String SaveSymbolTable() {
			return InitVal(_table(), _Func("saveSymbolPoint"));
		}

		private void BackSymbolTable(String lname) {
			Statement(_Func("backSymbolPoint", lname));
		}

		private String[] SaveState(Expression inner) {
			String[] names = new String[4];
			names[0] = SavePos();
			if (typeState.inferTypestate(inner) != Typestate.Unit) {
				names[1] = SaveTree();
				names[2] = SaveLog();
			}
			if (symbolMutation.isMutated(inner)) {
				names[3] = SaveSymbolTable();
			}
			return names;
		}

		private void BackState(Expression inner, String[] names) {
			BackPos(names[0]);
			if (names[1] != null) {
				BackTree(names[1]);
			}
			if (names[2] != null) {
				BackLog(names[2]);
			}
			if (names[3] != null) {
				BackSymbolTable(names[3]);
			}
		}

		@Override
		public Object visitNonTerminal(NonTerminal e, Object a) {
			Verbose("visitNonTerminal");
			String f = _eval(e.getUniqueName(), _pErr());
			If(_Not(f));
			{
				ReportError(1014, _pErr(), e.toString() + " {"  + a + "}");
				Fail();
			}
			EndIf();
			return null;
		}

		@Override
		public Object visitEmpty(Nez.Empty e, Object a) {
			Verbose("visitEmpty");
			return null;
		}

		@Override
		public Object visitFail(Nez.Fail e, Object a) {
			Verbose("visitFail");
			ReportError(1028, _pErr(), e.toString() + " {"  + a + "}");
			Fail();
			return null;
		}

		@Override
		public Object visitByte(Nez.Byte e, Object a) {
			Verbose("visitByte");
			If(_Func("read"), _NotEq(), _byte(e.byteChar));
			{
				ReportError(1037, _pErr(), e.toString() + " {"  + a + "}");
				Fail();
			}
			EndIf();
			checkBinaryEOF(e.byteChar == 0, a);
			return null;
		}

		@Override
		public Object visitByteSet(Nez.ByteSet e, Object a) {
			Verbose("visitByteSet");
			If(_Not(MatchByteArray(e.byteset, true)));
			{
				ReportError(1049, _pErr(), e.toString() + " {"  + a + "}");
				Fail();
			}
			EndIf();
			checkBinaryEOF(e.byteset[0], a);
			return null;
		}

		private void checkBinaryEOF(boolean checked, final Object a) {
			if (strategy.BinaryGrammar && checked) {
				If(_Func("eof"));
				{
					ReportError(1061, _pErr(), "EOF" + " {"  + a + "}");
					Fail();
				}
				EndIf();
			}
		}

		private String MatchByteArray(boolean[] byteMap, boolean inc) {
			String c = inc ? _Func("read") : _Func("prefetch");
			if (SupportedRange) {
				int[] range = range(byteMap);
				if (range != null) {
					return "(" + _Binary(_Binary(_byte(range[0]), "<=", _Func("prefetch")), _And(), _Binary(c, "<", _byte(range[1]))) + ")";
				}
			}
			if (UsingBitmap) {
				return _Func("bitis", _set(byteMap), c);
			} else {
				return _GetArray(_set(byteMap), c);
			}
		}

		@Override
		public Object visitAny(Nez.Any e, Object a) {
			Verbose("visitAny");

			if (strategy.BinaryGrammar) {
				Statement(_Func("move", "1"));
				If(_Func("eof"));
				{
					Fail();
				}
				EndIf();
			} else {
				//Check for EOF
				If(_Func("read"), _Eq(), "0");
				{
					Fail();
				}
				EndIf();
			}
			return null;
		}

		@Override
		public Object visitMultiByte(Nez.MultiByte e, Object a) {
			Verbose("visitMultiByte");
			If(_Not(_Match(e.byteseq)));
			{
				ReportError(1107, _pErr(), e.toString() + " {"  + a + "}");
				Fail();
			}
			EndIf();
			return null;
		}

		@Override
		public Object visitPair(Nez.Pair e, Object a) {
			Verbose("visitPair");

			Verbose("visitPair : first " + e.first.getClass().getSimpleName());
			visit(e.first, a);

			Verbose("visitPair : next " + e.next.getClass().getSimpleName());

			if (canMatchEmptyInput(e.first) /* || canMatchEmptyInput(e.next) */) {
				visit(e.next, a);
			} else {
				BeginLocalScope();
				String temp = InitVal(_temp(), _pErr());
				VarAssign(_pErr(), _True());
				visit(e.next, a);
				VarAssign(_pErr(), temp);
				EndLocalScope();
			}

			return null;
		}

		@Override
		public Object visitSequence(Nez.Sequence e, Object a) {
			Verbose("visitSequence");

			boolean enableErrors = false;

			for (Expression sub : e) {
				if (!enableErrors) {
					visit(sub, a);
					enableErrors = !canMatchEmptyInput(sub);
				} else {
					BeginLocalScope();
					String temp = InitVal(_temp(), _pErr());
					VarAssign(_pErr(), _True());
					visit(sub, a);
					VarAssign(_pErr(), temp);
					EndLocalScope();
				}
			}
			return null;
		}

		boolean canMatchEmptyInput(Expression e) {
			if (e instanceof Nez.Option || e instanceof Nez.ZeroMore || e instanceof NonTerminal || e instanceof Nez.LinkTree) {
				return true;
			}

			if (e instanceof Nez.BeginTree || e instanceof Nez.EndTree) {
				return true;
			}

			if (e instanceof Nez.Not) {
				return !canMatchEmptyInput(e.get(0));
			}

			if (e instanceof Nez.Pair) {
				return canMatchEmptyInput(e.get(0)) && canMatchEmptyInput(e.get(1));
			}

			if (e instanceof Nez.Sequence || e instanceof Nez.And) {
				for (var sub : e) {
					if (!canMatchEmptyInput(sub)) {
						return false;
					}
				}
				return true;
			}

			return false;
		}

		@Override
		public Object visitChoice(Nez.Choice e, Object a) {
			Verbose("visitChoice");
			if (e.predicted != null && SupportedSwitchCase) {
				Verbose("visitChoice: generateSwitch");
				generateSwitch(e, e.predicted, a);
			} else {
				Verbose("visitChoice: alternate");
				BeginScope();
				String temp = InitVal(_temp(), _True());
				String temp2 = InitVal(_temp(), _pErr());
				VarAssign(_pErr(), _False());
				for (Expression sub : e) {
					String f = _eval(sub);
					If(temp);
					{
						String[] n = SaveState(sub);
						Verbose(sub.toString());
						If(f);
						{
							VarAssign(temp, _False());
						}
						Else();
						{
							BackState(sub, n);
						}
						EndIf();
					}
					EndIf();
				}
				VarAssign(_pErr(), temp2);
				If(temp);
				{
					ReportError(1158, _pErr(), "one of " + e.toString() + " {"  + a + "}");
					Fail();
				}
				EndIf();
				EndScope();
			}
			return null;
		}

		private void generateSwitch(Nez.Choice choice, ChoicePrediction p, Object a) {
			if (choice.size() == 1) {
				Verbose.println("single choice: " + choice);
				choice.get(0).visit(this, a);
			} else {
				BeginScope();
				String temp = InitVal(_temp(), _True());
				String temp2 = InitVal(_temp(), _pErr());
				VarAssign(_pErr(), _False());

				Switch(_GetArray(_index(p.indexMap), _Func("prefetch")));
				Case("0");
				BeginLocalScope();
					ReportError(1175, _pErr(), "not being here {" + a + "}");
					Fail();
				EndLocalScope();
				for (int i = 0; i < choice.size(); i++) {
					Case(_int(i + 1));
					BeginLocalScope();
					Expression sub = choice.get(i);
					String f = _eval(sub);
					if (p.striped[i]) {
						Verbose(". " + sub);
						Statement(_Func("move", "1"));
					} else {
						Verbose(sub.toString());
					}
					VarAssign(temp, f);
					Break();
					EndLocalScope();
					EndCase();
				}
				EndSwitch();
				VarAssign(_pErr(), temp2);
				If(_Not(temp));
				{
					ReportError(1194, _pErr(), choice.toString() + " {"  + a + "}");
					Fail();
				}
				EndIf();
				EndScope();
			}
		}

		@Override
		public Object visitDispatch(Nez.Dispatch e, Object a) {
			Verbose("visitDispatch");
			String temp = InitVal(_temp(), _True());
			Switch(_GetArray(_index(e.indexMap), _Func("prefetch")));
			Case("0");
			ReportError(1206, _pErr(), "should not be here " + e.toString() + " {"  + a + "}");
			Fail();
			for (int i = 1; i < e.size(); i++) {
				Expression sub = e.get(i);
				Case(_int(i));
				String f = _eval(sub);
				Verbose(sub.toString());
				VarAssign(temp, f);
				Break();
				EndCase();
			}
			EndSwitch();
			If(_Not(temp));
			{
				ReportError(1220, _pErr(), e.toString() + " {"  + a + "}");
				Fail();
			}
			EndIf();
			return null;
		}

		@Override
		public Object visitOption(Nez.Option e, Object a) {
			Verbose("visitOption");
			BeginLocalScope();
			String temp = InitVal(_temp(), _pErr());
			VarAssign(_pErr(), _False());
			Expression sub = e.get(0);
			if (!tryOptionOptimization(sub)) {
				Verbose("visitOption : regular case");
				String f = _eval(sub);
				String[] n = SaveState(sub);
				Verbose(sub.toString());
				If(_Not(f));
				{
					BackState(sub, n);
				}
				EndIf();
			}
			VarAssign(_pErr(), temp);
			EndLocalScope();
			return null;
		}

		@Override
		public Object visitZeroMore(Nez.ZeroMore e, Object a) {
			Verbose("visitZeroMore");
			BeginLocalScope();
			String temp = InitVal(_temp(), _pErr());
			VarAssign(_pErr(), _False());
			if (!tryRepetitionOptimization(e.get(0), false, a)) {
				Verbose("visitZeroMore: generateWhile");
				generateWhile(e, false, a);
			}
			VarAssign(_pErr(), temp);
			EndLocalScope();
			return null;
		}

		private void generateWhile(Expression e, boolean oneMore, Object a) {
			Expression sub = e.get(0);
			String f = _eval(sub, _pErr());
			While(_True());
			{
				String[] n = SaveState(sub);
				Verbose(sub.toString());
				If(_Not(f));
				{
					if (oneMore) {
						ReportError(1257, _pErr(), " at least one " + f + " (" + sub + ")" + " {"  + a + "}");
					}
					BackState(sub, n);
					Break();
				}
				Else();
				{
					VarAssign(_pErr(), _False());
				}
				EndIf();
				CheckInfiniteLoop(sub, n[0]);
			}
			EndWhile();
		}

		@Override
		public Object visitOneMore(Nez.OneMore e, Object a) {
			Verbose("visitOneMore");
			BeginLocalScope();
			String temp = InitVal(_temp(), _pErr());
			if (!tryRepetitionOptimization(e.get(0), true, a)) {
				Verbose("visitOneMore : general case");
				String f = _eval(e.get(0));
				if (f != null) {
					If(_Not(f));
					{
						ReportError(1276, _pErr(), e.toString() + " {"  + a + "}");
						Fail();
					}
					Else();
					{
						VarAssign(_pErr(), _False());
					}
					EndIf();
				} else {
					visit(e.get(0), a);
				}
				//TODO: may trigger false error reporting, if first match is successful above?
				generateWhile(e, true, a);
			}
			VarAssign(_pErr(), temp);
			EndLocalScope();
			return null;
		}

		private void CheckInfiniteLoop(Expression e, String var) {
			if (!consumption.isConsumed(e)) {
				If(var, _Eq(), _Field(_state(), "pos"));
				{
					Break();
				}
				EndIf();
			}
		}

		@Override
		public Object visitAnd(Nez.And e, Object a) {
			Verbose("visitAnd");
			Expression sub = e.get(0);
			if (!tryAndOptimization(sub, a)) {
				String f = _eval(sub);
				BeginScope();
				String n = SavePos();
				Verbose(sub.toString());
				If(_Not(f));
				{
					ReportError(1308, _pErr(), e.toString() + " {"  + a + "}");
					Fail();
				}
				EndIf();
				BackPos(n);
				EndScope();
			}
			return null;
		}

		@Override
		public Object visitNot(Nez.Not e, Object a) {
			Verbose("visitNot");
//			BeginLocalScope();
//			String temp = InitVal(_temp(), _pErr());
//			VarAssign(_pErr(), _False());
			Expression sub = e.get(0);
			if (!tryNotOptimization(sub, a)) {
				Verbose("visitNot : regular case");
				String f = _eval(sub);
				BeginScope();
				String[] n = SaveState(sub);
				Verbose(sub.toString());
				If(f);
				{
					//ReportError(1370, temp, "not " + e.toString() + " {"  + a + "}");
					ReportError(1370, _pErr(), "not " + e.toString() + " {"  + a + "}");
					Fail();
				}
				EndIf();
				BackState(sub, n);
				EndScope();
			}
//			VarAssign(_pErr(), temp);
//			EndLocalScope();
			return null;
		}

		private boolean tryOptionOptimization(Expression inner) {
			if (strategy.Olex) {
				if (inner instanceof Nez.Byte) {
					Nez.Byte e = (Nez.Byte) inner;
					If(_Func("prefetch"), _Eq(), _byte(e.byteChar));
					{
						if (strategy.BinaryGrammar && e.byteChar == 0) {
							If(_Not(_Func("eof")));
							{
								Statement(_Func("move", "1"));
							}
							EndIf();
						} else {
							Statement(_Func("move", "1"));
						}
					}
					EndIf();
					return true;
				}
				if (inner instanceof Nez.ByteSet) {
					Nez.ByteSet e = (Nez.ByteSet) inner;
					If(MatchByteArray(e.byteset, false));
					{
						if (strategy.BinaryGrammar && e.byteset[0]) {
							If(_Not(_Func("eof")));
							{
								Statement(_Func("move", "1"));
							}
							EndIf();
						} else {
							Statement(_Func("move", "1"));
						}
					}
					EndIf();
					return true;
				}
				if (inner instanceof Nez.MultiByte) {
					Nez.MultiByte e = (Nez.MultiByte) inner;
					Statement(_Match(e.byteseq));
					return true;
				}
				if (inner instanceof Nez.Any) {
					// Nez.Any e = (Nez.Any) inner;
					If(_Not(_Func("eof")));
					{
						Statement(_Func("move", "1"));
					}
					EndIf();
					return true;
				}
			}
			return false;
		}

		private boolean tryRepetitionOptimization(Expression inner, boolean OneMore, Object a) {
			if (strategy.Olex) {
				if (inner instanceof Nez.Byte) {
					Nez.Byte e = (Nez.Byte) inner;
					if (OneMore) {
						visit(inner, a);
						VarAssign(_pErr(), _False());
					}
					While(_Binary(_Func("prefetch"), _Eq(), _byte(e.byteChar)));
					{
						if (strategy.BinaryGrammar && e.byteChar == 0) {
							If(_Func("eof"));
							{
								Break();
							}
							EndIf();
						}
						Statement(_Func("move", "1"));
					}
					EndWhile();
					return true;
				}
				if (inner instanceof Nez.ByteSet) {
					Nez.ByteSet e = (Nez.ByteSet) inner;
					if (OneMore) {
						visit(inner, a);
						VarAssign(_pErr(), _False());
					}
					While(MatchByteArray(e.byteset, false));
					{
						if (strategy.BinaryGrammar && e.byteset[0]) {
							If(_Func("eof"));
							{
								Break();
							}
							EndIf();
						}
						Statement(_Func("move", "1"));
					}
					EndWhile();
					return true;
				}
				if (inner instanceof Nez.MultiByte) {
					Nez.MultiByte e = (Nez.MultiByte) inner;
					if (OneMore) {
						visit(inner, a);
						VarAssign(_pErr(), _False());
					}
					While(_Match(e.byteseq));
					{
						EmptyStatement();
					}
					EndWhile();
					return true;
				}
				if (inner instanceof Nez.Any) {
					// Nez.Any e = (Nez.Any) inner;
					if (OneMore) {
						visit(inner, a);
						VarAssign(_pErr(), _False());
					}
					While(_Not(_Func("eof")));
					{
						Statement(_Func("move", "1"));
					}
					EndWhile();
					return true;
				}
			}
			return false;
		}

		private boolean tryAndOptimization(Expression inner, final Object a) {
			if (strategy.Olex) {
				if (inner instanceof Nez.Byte) {
					Nez.Byte e = (Nez.Byte) inner;
					If(_Func("prefetch"), _NotEq(), _byte(e.byteChar));
					{
						//ReportError(1485, _pErr(), e.toString() + " {"  + a + "}");
						Fail();
					}
					EndIf();
					checkBinaryEOF(e.byteChar == 0, a);
					return true;
				}
				if (inner instanceof Nez.ByteSet) {
					Nez.ByteSet e = (Nez.ByteSet) inner;
					If(_Not(MatchByteArray(e.byteset, false)));
					{
						//ReportError(1496, _pErr(), e.toString() + "(byte set)" + " {"  + a + "}");
						Fail();
					}
					EndIf();
					checkBinaryEOF(e.byteset[0], a);
					return true;
				}
				if (inner instanceof Nez.MultiByte) {
					Nez.MultiByte e = (Nez.MultiByte) inner;
					If(_Not(_Match(e.byteseq)));
					{
						//ReportError(1507, _pErr(), new String(e.byteseq, StandardCharsets.UTF_8) + " {"  + a + "}");
						Fail();
					}
					EndIf();
					return true;
				}
				if (inner instanceof Nez.Any) {
					// Nez.Any e = (Nez.Any) inner;
					If(_Func("eof"));
					{
						//ReportError(1517, _pErr(), "not an EOF" + " {"  + a + "}");
						Fail();
					}
					EndIf();
					return true;
				}
			}
			return false;
		}

		private boolean tryNotOptimization(Expression inner, final Object a) {
			if (strategy.Olex) {
				if (inner instanceof Nez.Byte) {
					Nez.Byte e = (Nez.Byte) inner;
					If(_Func("prefetch"), _Eq(), _byte(e.byteChar));
					{
						//ReportError(1533, _pErr(), "not " + Character.toString(e.byteChar) + " {"  + a + "}");
						Fail();
					}
					EndIf();
					checkBinaryEOF(e.byteChar != 0, a);
					return true;
				}
				if (inner instanceof Nez.ByteSet) {
					Nez.ByteSet e = (Nez.ByteSet) inner;
					If(MatchByteArray(e.byteset, false));
					{
						//ReportError(1544, _pErr(), "not " + e.byteset + " | " + e.toString() + " {"  + a + "}");
						Fail();
					}
					EndIf();
					checkBinaryEOF(!e.byteset[0], a);
					return true;
				}
				if (inner instanceof Nez.MultiByte) {
					Nez.MultiByte e = (Nez.MultiByte) inner;
					If(_Match(e.byteseq));
					{
						//ReportError(1555, _pErr(), "not " + new String(e.byteseq, StandardCharsets.UTF_8) + " {"  + a + "}");
						Fail();
					}
					EndIf();
					return true;
				}
				if (inner instanceof Nez.Any) {
					// Nez.Any e = (Nez.Any) inner;
					If(_Not(_Func("eof")));
					{
						//ReportError(1566, _pErr(), "EOF" + " {"  + a + "}");
						Fail();
					}
					EndIf();
					return true;
				}
			}
			return false;
		}

		/* Tree Construction */

		@Override
		public Object visitBeginTree(Nez.BeginTree e, Object a) {
			Verbose("visitBeginTree");
			Statement(_Func("beginTree", _int(e.shift)));
			return null;
		}

		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			Verbose("visitEndTree");
			Statement(_Func("endTree", _int(e.shift), _tag(e.tag), _text(e.value)));
			return null;
		}

		@Override
		public Object visitFoldTree(Nez.FoldTree e, Object a) {
			Verbose("visitFoldTree");
			Statement(_Func("foldTree", _int(e.shift), _label(e.label)));
			return null;
		}

		@Override
		public Object visitLinkTree(Nez.LinkTree e, Object a) {
			Verbose("visitLinkTree");
			BeginScope();
			String tree = SaveTree();
			visit(e.get(0), a);
			Statement(_Func("linkTree", /* _Null(), */_label(e.label)));
			BackTree(tree);
			EndScope();
			return null;
		}

		@Override
		public Object visitTag(Nez.Tag e, Object a) {
			Verbose("visitTag");
			Statement(_Func("tagTree", _tag(e.tag)));
			return null;
		}

		@Override
		public Object visitReplace(Nez.Replace e, Object a) {
			Verbose("visitReplace");
			Statement(_Func("valueTree", _text(e.value)));
			return null;
		}

		@Override
		public Object visitDetree(Nez.Detree e, Object a) {
			Verbose("visitDetree");
			BeginScope();
			String n1 = SaveTree();
			String n2 = SaveLog();
			visit(e.get(0), a);
			BackTree(n1);
			BackLog(n2);
			EndScope();
			return null;
		}

		@Override
		public Object visitBlockScope(Nez.BlockScope e, Object a) {
			Verbose("visitBlockScope");
			BeginScope();
			String n = SaveSymbolTable();
			visit(e.get(0), a);
			BackSymbolTable(n);
			EndScope();
			return null;
		}

		@Override
		public Object visitLocalScope(LocalScope e, Object a) {
			Verbose("visitLocalScope");
			BeginScope();
			String n = SaveSymbolTable();
			Statement(_Func("addSymbolMask", _table(e.tableName)));
			visit(e.get(0), a);
			BackSymbolTable(n);
			EndScope();
			return null;
		}

		@Override
		public Object visitSymbolAction(SymbolAction e, Object a) {
			Verbose("visitSymbolAction");
			BeginScope();
			String ppos = SavePos();
			visit(e.get(0), a);
			Statement(_Func("addSymbol", _table(e.tableName), ppos));
			EndScope();
			return null;
		}

		@Override
		public Object visitSymbolPredicate(SymbolPredicate e, Object a) {
			Verbose("visitSymbolPredicate");
			BeginScope();
			String ppos = SavePos();
			visit(e.get(0), a);
			if (e.op == FunctionName.is) {
				If(_Not(_Func("equals", _table(e.tableName), ppos)));
				{
					ReportError(1670, _pErr(), "equals " + _table(e.tableName) + " {"  + a + "}");
					Fail();
				}
				EndIf();
			} else {
				If(_Not(_Func("contains", _table(e.tableName), ppos)));
				{
					ReportError(1677, _pErr(), "contains" + _table(e.tableName) + " {"  + a + "}");
					Fail();
				}
				EndIf();
			}
			EndScope();
			return null;
		}

		@Override
		public Object visitSymbolMatch(SymbolMatch e, Object a) {
			Verbose("visitSymbolMatch");
			If(_Not(_Func("matchSymbol", _table(e.tableName))));
			{
				ReportError(1690, _pErr(), "matchSymbol" + _table(e.tableName) + " {"  + a + "}");
				Fail();
			}
			EndIf();
			return null;
		}

		@Override
		public Object visitSymbolExists(SymbolExists e, Object a) {
			Verbose("visitSymbolExists");
			if (e.symbol == null) {
				If(_Not(_Func("exists", _table(e.tableName))));
				{
					ReportError(1702, _pErr(), "exists" + _table(e.tableName) + " {"  + a + "}");
					Fail();
				}
				EndIf();
			} else {
				If(_Not(_Func("existsSymbol", _table(e.tableName), _text(e.symbol))));
				{
					ReportError(1709, _pErr(), "existsSymbol" + _table(e.tableName) + " {"  + a + "}");
					Fail();
				}
				EndIf();
			}
			return null;
		}

		@Override
		public Object visitScan(Nez.Scan e, Object a) {
			Verbose("visitScan");
			BeginScope();
			String ppos = SavePos();
			visit(e.get(0), a);
			Statement(_Func("scanCount", ppos, _long(e.mask), _int(e.shift)));
			EndScope();
			return null;
		}

		@Override
		public Object visitRepeat(Nez.Repeat e, Object a) {
			Verbose("visitRepeat");
			While(_Func("decCount"));
			{
				visit(e.get(0), a);
			}
			EndWhile();
			return null;
		}

		@Override
		public Object visitIf(IfCondition e, Object a) {
			Verbose("visitIf");
			return null;
		}

		@Override
		public Object visitOn(OnCondition e, Object a) {
			Verbose("visitOn");
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object visitLabel(Label e, Object a) {
			Verbose("visitLabel");
			// TODO Auto-generated method stub
			return null;
		}

	}

	/* Syntax */

	@Override
	protected String _LineComment() {
		return "//";
	}

	protected String _Comment(String c) {
		return "/*" + c + "*/";
	}

	protected String _And() {
		return "&&";
	}

	protected String _Or() {
		return "||";
	}

	protected String _Not(String expr) {
		return "!" + expr;
	}

	protected String _Eq() {
		return "==";
	}

	protected String _NotEq() {
		return "!=";
	}

	protected String _True() {
		return "true";
	}

	protected String _False() {
		return "false";
	}

	protected String _Null() {
		return "null";
	}

	/* Expression */

	private String _GetArray(String array, String c) {
		return array + "[" + c + "]";
	}

	protected String _BeginArray() {
		return "{";
	}

	protected String _EndArray() {
		return "}";
	}

	protected String _BeginBlock() {
		return " {";
	}

	protected String _EndBlock() {
		return "}";
	}

	protected String _Field(String o, String name) {
		return o + "." + name;
	}

	protected String _Func(String name, String... args) {
		StringBuilder sb = new StringBuilder();
		sb.append(_state());
		sb.append(".");
		sb.append(name);
		sb.append("(");
		for (int i = 0; i < args.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(args[i]);
		}
		sb.append(")");
		return sb.toString();
	}

	protected String _byte(int ch) {
		return "" + (ch & 0xff);
	}

	protected String _Match(byte[] t) {
		if (SupportedMatch2 && isMatchText(t, 2)) {
			return _Func("match2", _byte(t[0]), _byte(t[1]));
		}
		if (SupportedMatch3 && isMatchText(t, 3)) {
			return _Func("match3", _byte(t[0]), _byte(t[1]), _byte(t[2]));
		}
		if (SupportedMatch4 && isMatchText(t, 4)) {
			return _Func("match4", _byte(t[0]), _byte(t[1]), _byte(t[2]), _byte(t[3]));
		}
		if (SupportedMatch4 && isMatchText(t, 5)) {
			return _Func("match5", _byte(t[0]), _byte(t[1]), _byte(t[2]), _byte(t[3]), _byte(t[4]));
		}
		if (SupportedMatch4 && isMatchText(t, 6)) {
			return _Func("match6", _byte(t[0]), _byte(t[1]), _byte(t[2]), _byte(t[3]), _byte(t[4]), _byte(t[5]));
		}
		if (SupportedMatch4 && isMatchText(t, 7)) {
			return _Func("match7", _byte(t[0]), _byte(t[1]), _byte(t[2]), _byte(t[3]), _byte(t[4]), _byte(t[5]), _byte(t[6]));
		}
		if (SupportedMatch4 && isMatchText(t, 8)) {
			return _Func("match8", _byte(t[0]), _byte(t[1]), _byte(t[2]), _byte(t[3]), _byte(t[4]), _byte(t[5]), _byte(t[6]), _byte(t[7]));
		}
		return _Func("match", _text(t));
	}

	protected String _int(int n) {
		return "" + n;
	}

	protected String _hex(int n) {
		return String.format("0x%08x", n);
	}

	protected String _long(long n) {
		return "" + n + "L";
	}

	/* Expression */

	protected String _defun(String type, String name) {
		return "private static <T> " + type + "name";
	}

	protected String _argument(String var, String type) {
		if (type == null) {
			return var;
		}
		return type + " " + var;
	}

	protected String _argument() {
		return _argument(_state(), type(_state()));
	}

	protected String _printErr() {
		return _argument(_pErr(), type(_pErr()));
	}

	protected String _funccall(String name) {
		return _funccall(name, "1");
	}

	protected String _funccall(String name, String secondParam) {
		return name + "(" + _state() + ", " + secondParam + ")";
	}

	/* Statement */

	protected void BeginDecl(String line) {
		file.writeIndent(line);
		Begin();
	}

	protected void EndDecl() {
		End();
	}

	protected void Begin() {
		file.write(" {");
		file.incIndent();
	}

	protected void End() {
		file.decIndent();
		file.writeIndent();
		file.write("}");
	}

	protected void BeginFunc(String type, String name, String args) {
		file.writeIndent();
		file.write(_defun(type, name));
		file.write("(");
		file.write(args);
		file.write(")");
		Begin();
	}

	protected final void BeginFunc(String f, String args) {
		BeginFunc(type("$parse"), f, args);
	}

	protected final void BeginFunc(String f) {
		BeginFunc(type("$parse"), f, _argument() + ", " + _printErr());
	}

	protected void EndFunc() {
		End();
	}

	protected void BeginLocalScope() {
		file.writeIndent("{");
		file.incIndent();
	}

	protected void EndLocalScope() {
		file.decIndent();
		file.writeIndent();
		file.write("}");
	}

	protected void Statement(String stmt) {
		file.writeIndent(stmt);
		_Semicolon();
	}

	protected void EmptyStatement() {
		file.writeIndent();
		_Semicolon();
	}

	protected void _Semicolon() {
		file.write(";");
	}

	protected void Return(String expr) {
		Statement("return " + expr);
	}

	protected void Succ() {
		Return(_True());
	}

	protected void Fail() {
		Return(_False());
	}

	protected static String stringify(String input) {
		return "\"" + input
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			 + "\""; //"\\"+=\\""

	}

	protected void ReportError(int line, final String expr, String text) {
		var msg = stringify("CPG:" + line + ": Expecting " + text);

		Verbose(msg);
		If("p_err");
		{
			Statement(_Func("reportError", msg));
		}
		EndIf();
	}

	protected void If(String cond) {
		file.writeIndent("if (");
		file.write(cond);
		file.write(")");
		Begin();
	}

	protected String _Binary(String a, String op, String b) {
		return a + " " + op + " " + b;
	}

	protected void If(String a, String op, String b) {
		If(a + " " + op + " " + b);
	}

	protected void Else() {
		End();
		file.write(" else");
		Begin();
	}

	protected void EndIf() {
		End();
	}

	protected void While(String cond) {
		file.writeIndent();
		file.write("while (");
		file.write(cond);
		file.write(")");
		Begin();
	}

	protected void EndWhile() {
		End();
	}

	protected void Do() {
		file.writeIndent();
		file.write("do");
		Begin();
	}

	protected void DoWhile(String cond) {
		End();
		file.write("while (");
		file.write(cond);
		file.write(")");
		_Semicolon();
	}

	protected void Break() {
		file.writeIndent("break");
		_Semicolon();
	}

	protected void Switch(String c) {
		Line("switch(" + c + ")");
		Begin();
	}

	protected void EndSwitch() {
		End();
	}

	protected void Case(String n) {
		Line("case " + n + ": ");
	}

	protected void EndCase() {
	}

	protected void VarDecl(String name, String expr) {
		VarDecl(type(name), name, expr);
	}

	protected void VarDecl(String type, String name, String expr) {
		if (name == null) {
			VarAssign(name, expr);
		} else {
			Statement(type + " " + name + " = " + expr);
		}
	}

	protected void VarAssign(String v, String expr) {
		Statement(v + " = " + expr);
	}

	protected void DeclConst(String type, String name, String val) {
		if (type == null) {
			Statement("private final static " + name + " = " + val);
		} else {
			Statement("private final static " + type + " " + name + " = " + val);
		}
	}

	protected String _arity(int arity) {
		return "[" + arity + "]";
	}

	protected void DeclConst(String type, String name, int arity, String val) {
		if (type("$arity") != null) {
			DeclConst(type, name + _arity(arity), val);
		} else {
			DeclConst(type, name, val);
		}
	}

	/* Variables */

	protected String _state() {
		return "c";
	}

	protected String _pErr() {
		return "p_err";
	}

	protected String _pos() {
		return "pos";
	}

	protected String _last_error() {
		return "last_error";
	}

	protected String _inputs() {
		return "inputs";
	}

	protected String _cpos() {
		return _Field(_state(), "pos");
	}

	protected String _tree() {
		return "left";
	}

	protected String _log() {
		return "log";
	}

	protected String _table() {
		return "sym";
	}

	protected String _temp() {
		return "temp";
	}

	protected String _index() {
		return "_index";
	}

	protected String _set() {
		return "_set";
	}

	protected String _range() {
		return "_range";
	}

	protected String _text() {
		return "_text";
	}

	protected void InitMemoPoint() {
		if (code.getMemoPointSize() > 0) {
			Statement(_Func("initMemo", _int(strategy.SlidingWindow), _int(code.getMemoPointSize())));
		}
	}
}
