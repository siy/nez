package nez.debugger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.SimpleCompletor;
import nez.ast.CommonTree;
import nez.debugger.Context.FailOverInfo;
import nez.junks.ParserGrammar;
import nez.lang.Expression;
import nez.lang.Nez;
import nez.lang.NonTerminal;
import nez.lang.Production;
import nez.main.ReadLine;
import nez.util.ConsoleUtils;

public class NezDebugger {
	HashMap<String, BreakPoint> breakPointMap = new HashMap<>();
	HashMap<Nez.Choice, ChoicePoint> choicePointMap = new HashMap<>();
	HashMap<String, Production> ruleMap = new HashMap<>();
	List<String> nameList = new ArrayList<>();
	DebugOperator command;
	ParserGrammar peg;
	DebugVMCompiler compiler;
	Module module;
	DebugVMInstruction code;
	DebugSourceContext sc;
	String text;
	int linenum;
	boolean running;
	ConsoleReader cr;

	public NezDebugger(ParserGrammar peg, DebugVMInstruction code, DebugSourceContext sc, DebugVMCompiler c) {
		this.peg = peg;
		this.code = code;
		this.sc = sc;
		this.compiler = c;
		this.module = c.getModule();
		for (Production p : peg) {
			ruleMap.put(p.getLocalName(), p);
			nameList.add(p.getLocalName());
		}
		ReadLine.addCompleter(nameList);
		try {
			this.cr = new ConsoleReader();
			Completor[] br = { new SimpleCompletor(new String[] { "b", "break", "goto", "reach" }), new SimpleCompletor(nameList.toArray(new String[0])) };
			ArgumentCompletor abr = new ArgumentCompletor(br);
			ArrayList<String> printlist = new ArrayList<>();
			printlist.addAll(nameList);
			printlist.addAll(Arrays.asList("-pos", "-node", "-pr", "-call"));
			Completor[] print = { new SimpleCompletor(new String[] { "p", "print" }), new SimpleCompletor(printlist.toArray(new String[0])) };
			ArgumentCompletor apr = new ArgumentCompletor(print);
			Completor[] bt = { new SimpleCompletor(new String[] { "bt" }), new SimpleCompletor("-l") };
			ArgumentCompletor abt = new ArgumentCompletor(bt);
			ArgumentCompletor commands = new ArgumentCompletor(new SimpleCompletor(new String[] { "n", "s", "f", "c", "r", "q", "exit", "h", "start", "consume", "fover" }));
			MultiCompletor mc = new MultiCompletor(new ArgumentCompletor[] { abr, apr, abt, commands });
			cr.addCompletor(mc);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static class BreakPoint {
		Production pr;
		Integer id;

		public BreakPoint(Production pr, int id) {
			this.pr = pr;
			this.id = id;
		}
	}

	static class ChoicePoint {
		Nez.Choice e;
		int index;
		boolean first;
		boolean succ;

		public ChoicePoint(Nez.Choice e, int index) {
			this.e = e;
			this.index = index;
			this.first = true;
			this.succ = false;
		}
	}

	public boolean exec() {
		boolean result;
		showCurrentExpression();
		try {
			while (true) {
				readLine("(nezdb) ");
				command.exec(this);
				if (code instanceof Iexit) {
					code.exec(sc);
				}
				showCurrentExpression();
			}
		} catch (MachineExitException e) {
			result = e.result;
			if (result) {
				for (int i = 0; i < sc.failOverList.size(); i++) {
					FailOverInfo fover = sc.failOverList.get(i);
					ConsoleUtils.println("Code Layout bug " + i + ":");
					ConsoleUtils.println("Expression: " + fover.e.getSourceLocation().formatSourceMessage("", ""));
					ConsoleUtils.println("Input: " + sc.formatDebugPositionLine(fover.fail_pos, ""));
				}
			} else {
				if (sc.longestTrace != null) {
					for (int i = 1; i <= sc.longestStackTop; i++) {
						NonTerminal ne = (NonTerminal) sc.longestTrace[i].val;
						long pos = sc.longestTrace[i].pos;
						CommonTree tree = (CommonTree) ne.getSourceLocation();
						ConsoleUtils.println("[" + i + "] " + ne.getLocalName() + " (" + tree.getSource().linenum(tree.getSourcePosition()) + ")");
						ConsoleUtils.println(sc.formatDebugPositionLine(pos, ""));
					}
				}
				ConsoleUtils.println(sc.formatDebugPositionLine(sc.longest_pos, "[longest]"));
				ConsoleUtils.println(sc.formatDebugPositionLine(sc.pos, "[last]"));
			}
		}
		return result;
	}

	public boolean execCode() throws MachineExitException {
		if (code instanceof Icall) {
			if (breakPointMap.containsKey(((Icall) code).ne.getLocalName())) {
				this.code = code.exec(sc);
				return false;
			}
		}
		if (code instanceof Ialtstart) {
			this.code = code.exec(sc);
		}
		this.code = code.exec(sc);
		if (code instanceof AltInstruction) {
			if (code instanceof Ialtend) {
				execUnreachableChoiceCheck();
			}
			this.code = code.exec(sc);
		}
		return true;
	}

	// FIXME
	public void execUnreachableChoiceCheck() throws MachineExitException {
		ChoicePoint point = choicePointMap.get(((Ialtend) (code)).c);
		do {
			this.code = code.exec(sc);
		} while (point != null && ((!(code instanceof Ialtfin)) || !code.expr.equals(point.e)));
	}

	public void showCurrentExpression() {
		Expression e = null;
		if (code instanceof Icall) {
			e = ((Icall) code).ne;
		} else if (code instanceof Ialtstart) {
			e = code.expr.get(0);
		} else if (code instanceof Inop) {
			if (running) {
				ConsoleUtils.println(((Inop) code).p.getExpression().getSourceLocation().formatSourceMessage("debug", ""));
				return;
			}
		} else {
			e = code.getExpression();
		}
		if (running && e != null) {
			ConsoleUtils.println(e.getSourceLocation().formatSourceMessage("debug", ""));
		} else if (running) {
			ConsoleUtils.println("e = null");
		}
	}

	public void showDebugUsage() {
		ConsoleUtils.println("Nez Debugger support following commands:");
		ConsoleUtils.println("  p | print [-ctx field? | ProductionName]  Print");
		ConsoleUtils.println("  b | break [ProductionName]                BreakPoint");
		ConsoleUtils.println("  n                                         StepOver");
		ConsoleUtils.println("  s                                         StepIn");
		ConsoleUtils.println("  f | finish                                StepOut");
		ConsoleUtils.println("  c                                         Continue");
		ConsoleUtils.println("  r | run                                   Run");
		ConsoleUtils.println("  q | exit                                  Exit");
		ConsoleUtils.println("  h | help                                  Help");
	}

	private void readLine(String prompt) {
		while (true) {
			try {
				String line = cr.readLine(prompt);
				if (line == null || line.equals("")) {
					if (command == null) {
						continue;
					}
					return;
				}
				cr.getHistory().addToHistory(line);
				linenum++;
				String[] tokens = line.split("\\s+");
				String command = tokens[0];
				int pos = 1;
				switch (command) {
					case "p":
					case "print":
						Print p = new Print();
						if (tokens.length < 2) {
							showDebugUsage();
							return;
						}
						if (tokens[pos].startsWith("-")) {
							switch (tokens[pos]) {
								case "-pos":
									p.setType(Print.printPos);
									break;
								case "-node":
									p.setType(Print.printNode);
									break;
								case "-pr":
									p.setType(Print.printProduction);
									break;
								case "-call":
									p.setType(Print.printCallers);
									break;
							}
							pos++;
						}
						if (pos < tokens.length) {
							p.setCode(tokens[pos]);
						}
						this.command = p;
						return;
					case "bt":
						if (!running) {
							ConsoleUtils.println("error: invalid process");
						} else {
							this.command = new BackTrace();
							if (tokens.length != 1) {
								if (tokens[pos].equals("-l")) {
									((BackTrace) this.command).setType(BackTrace.longestTrace);
								}
							}
							return;
						}
						break;
					case "b":
					case "break":
						this.command = new Break();
						if (tokens.length < 2) {
							return;
						}
						this.command.setCode(tokens[pos]);
						return;
					case "n":
						if (!running) {
							ConsoleUtils.println("error: invalid process");
						} else {
							this.command = new StepOver();
							return;
						}
						break;
					case "s":
						if (!running) {
							ConsoleUtils.println("error: invalid process");
						} else {
							this.command = new StepIn();
							return;
						}
						break;
					case "f":
					case "finish":
						if (!running) {
							ConsoleUtils.println("error: invalid process");
						} else {
							this.command = new StepOut();
							return;
						}
						break;
					case "c":
						if (!running) {
							ConsoleUtils.println("error: invalid process");
						} else {
							this.command = new Continue();
							return;
						}
						break;
					case "r":
					case "run":
						if (!running) {
							this.command = new Run();
							running = true;
							return;
						} else {
							ConsoleUtils.println("error: now running");
						}
						break;
					case "q":
					case "exit":
						this.command = new Exit();
						return;
					case "start":
						if (tokens[pos].equals("pos")) {
							pos++;
							this.command = new StartPosition(Long.parseLong(tokens[pos]));
						}
						return;
					case "consume":
						this.command = new Consume(Long.parseLong(tokens[pos]));
						return;
					case "goto":
						this.command = new Goto(tokens[pos]);
						return;
					case "reach":
						if (tokens.length < 3) {
							System.out.println("error: this command is required 2 argument(name, path)");
							return;
						}
						this.command = new Reachable(tokens[pos], tokens[pos + 1]);
						return;
					case "fover":
						if (sc.failOver) {
							sc.failOver = false;
							System.out.println("finish FailOver mode");
						} else {
							sc.failOver = true;
							System.out.println("start FailOver mode");
						}
						break;
					case "h":
					case "help":
						showDebugUsage();
						break;
					default:
						ConsoleUtils.println("command not found: " + command);
						showDebugUsage();
						break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean exec(Print o) {
		if (o.type == Print.printPos || o.type == Print.printNode) {
			Context ctx = sc;

			if (o.type == Print.printPos) {
				ConsoleUtils.println("pos = " + ctx.getPosition());
				ConsoleUtils.println(sc.formatDebugPositionLine(sc.getPosition(), ""));
			} else if (o.type == Print.printNode) {
				ConsoleUtils.println("left = " + ctx.getLeftObject());
			}
		} else if (o.type == Print.printProduction) {
			Production rule = ruleMap.get(o.code);
			if (rule != null) {
				ConsoleUtils.println(rule.getLocalName());
				ConsoleUtils.println(rule.getExpression().getSourceLocation()); // FIXME
																				// debug
																				// message
			} else {
				ConsoleUtils.println("error: production not found '" + o.code + "'");
			}
		} else if (o.type == Print.printCallers) {
			Function f = module.get(o.code);
			if (f != null) {
				ConsoleUtils.println(f.funcName + " >>");
				for (int i = 0; i < f.callers.size(); i++) {
					ConsoleUtils.println("  [" + i + "] " + f.callers.get(i).funcName);
				}
			} else {
				ConsoleUtils.println("error: production not found '" + o.code + "'");
			}
		}
		return true;
	}

	public boolean exec(BackTrace o) {
		if (o.getType() == BackTrace.longestTrace) {
			if (sc.longestTrace != null) {
				for (int i = 1; i <= sc.longestStackTop; i++) {
					NonTerminal ne = (NonTerminal) sc.longestTrace[i].val;
					CommonTree tree = (CommonTree) ne.getSourceLocation();
					ConsoleUtils.println("[" + i + "] " + ne.getLocalName() + " (" + tree.getSource().linenum(tree.getSourcePosition()) + ")");
					ConsoleUtils.println(sc.formatDebugPositionLine(sc.longestTrace[i].pos, ""));
				}
			} else {
				ConsoleUtils.println("backtracking has not occurred");
			}
		} else {
			for (int i = 1; i <= sc.callStackTop; i++) {
				NonTerminal ne = (NonTerminal) sc.callStack[i].val;
				CommonTree tree = (CommonTree) ne.getSourceLocation();
				ConsoleUtils.println("[" + i + "] " + ne.getLocalName() + " (" + tree.getSource().linenum(tree.getSourcePosition()) + ")");
				ConsoleUtils.println(sc.formatDebugPositionLine(sc.callStack[i].pos, ""));
			}
		}
		return true;
	}

	public boolean exec(Break o) {
		if (command.code != null) {
			Production rule = ruleMap.get(command.code);
			if (rule != null) {
				breakPointMap.put(rule.getLocalName(), new BreakPoint(rule, breakPointMap.size() + 1));
				ConsoleUtils.println("breakpoint " + (breakPointMap.size()) + ": where = " + rule.getLocalName() + " " /*
																															 * FIXME
																															 * +
																															 * (
																															 * rule
																															 * .
																															 * getExpression
																															 * (
																															 * )
																															 * .
																															 * getSourcePosition
																															 * (
																															 * )
																															 * )
																															 * .
																															 * formatDebugSourceMessage
																															 * (
																															 * ""
																															 * )
																															 */);
			} else {
				ConsoleUtils.println("production not found");
			}
		} else {
			showBreakPointList();
		}
		return true;
	}

	public void showBreakPointList() {
		if (breakPointMap.isEmpty()) {
			ConsoleUtils.println("No breakpoints currently set");
		} else {
			List<Entry<String, BreakPoint>> mapValuesList = new ArrayList<>(breakPointMap.entrySet());
			mapValuesList.sort(Comparator.comparing(entry -> (entry.getValue().id)));
			for (Entry<String, BreakPoint> s : mapValuesList) {
				BreakPoint br = s.getValue();
				Production rule = (br.pr);
				ConsoleUtils.println(br.id + ": " + rule.getLocalName() + " " /*
																			 * +
																			 * FIXME
																			 * rule
																			 * .
																			 * getExpression
																			 * (
																			 * )
																			 * .
																			 * getSourcePosition
																			 * (
																			 * )
																			 * .
																			 * formatDebugSourceMessage
																			 * (
																			 * ""
																			 * )
																			 */);
			}
		}
	}

	public boolean exec(StepOver o) throws MachineExitException {
		if (code.getExpression() instanceof NonTerminal) {
			while (!code.op.equals(Opcode.Icall)) {
				execCode();
			}
			execCode();
			int stackTop = sc.callStackTop;
			while (stackTop <= sc.callStackTop) {
				if (!execCode()) {
					break;
				}
			}
		} else {
			if (code instanceof AltInstruction) {
				this.code = code.exec(sc);
			}
			Expression e = code.getExpression();
			Expression current = code.getExpression();
			while (e.equals(current)) {
				execCode();
				current = code.getExpression();
			}
		}
		if (code.op.equals(Opcode.Iret)) {
			this.code = code.exec(sc);
		}
		return true;
	}

	public boolean exec(StepIn o) throws MachineExitException {
		Expression e = code.getExpression();
		Expression current = code.getExpression();
		while (e.equals(current)) {
			execCode();
			current = code.getExpression();
		}
		if (code.op.equals(Opcode.Iret)) {
			this.code = code.exec(sc);
		}
		return true;
	}

	public boolean exec(StepOut o) throws MachineExitException {
		int stackTop = sc.callStackTop;
		while (stackTop <= sc.callStackTop) {
			if (!execCode()) {
				break;
			}
		}
		return true;
	}

	public boolean exec(Continue o) throws MachineExitException {
		while (true) {
			if (!execCode()) {
				break;
			}
		}
		return true;
	}

	public boolean exec(Run o) throws MachineExitException {
		// Production p = (Production) this.code.getExpression();
		// if (this.breakPointMap.containsKey(p.getLocalName())) {
		// this.code = this.code.exec(this.sc);
		// return false;
		// } FIXME
		while (true) {
			if (!execCode()) {
				return true;
			}
		}
	}

	public boolean exec(Exit o) {
		ConsoleUtils.exit(0, "debugger (status=0)");
		return false;
	}

	public boolean exec(StartPosition o) {
		sc.pos = o.pos;
		ConsoleUtils.println("set start position: " + sc.pos);
		return true;
	}

	public boolean exec(Consume o) throws MachineExitException {
		while (sc.pos < o.pos) {
			this.code = code.exec(sc);
		}
		ConsoleUtils.println("current position: " + sc.pos);
		return true;
	}

	public boolean exec(Goto o) throws MachineExitException {
		while (true) {
			if (code instanceof Icall) {
				if ((((Icall) code).ne.getLocalName()).equals(o.name)) {
					this.code = code.exec(sc);
					return false;
				}
			}
			this.code = code.exec(sc);
		}
	}

	public boolean exec(Reachable o) {
		Production rule = ruleMap.get(o.name);
		Expression e = rule.getExpression();
		Expression prev = null;
		String[] tokens = o.path.split("\\.");
		int index = 0;
		for (String indexString : tokens) {
			index = Integer.parseInt(indexString.substring(1)) - 1;
			prev = e;
			e = e.get(index);
		}
		if (prev instanceof Nez.Choice) {
			if (index < 0) {
				System.out.println("error: set number is unexpected");
				return true;
			}
			ChoicePoint c = new ChoicePoint((Nez.Choice) prev, index);
			choicePointMap.put((Nez.Choice) prev, c);
			Alt alt = new Alt(index, compiler.altInstructionMap.get(prev.get(index)));
			sc.altJumpMap.put(prev, alt);
			ConsoleUtils.print("check unreachable choice " + choicePointMap.size() + ": where = ");
			ConsoleUtils.println(e.getSourceLocation().formatSourceMessage("reach", ""));
		} else {
			System.out.println("error");
		}
		return true;
	}

}
