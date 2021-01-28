package nez.parser;

import nez.ast.Source;

public final class ParserInstance {
	Source source;
	ParserRuntime runtime;

	public ParserInstance(Source source, ParserRuntime runtime) {
		this.runtime = runtime;
		this.source = source;
	}

	public final Source getSource() {
		return source;
	}

	public final ParserRuntime getRuntime() {
		return runtime;
	}

	public boolean hasUnconsumed() {
		return runtime.hasUnconsumed();
	}

	public final long getPosition() {
		return runtime.getPosition();
	}

	public final long getMaximumPosition() {
		return runtime.getMaximumPosition();
	}

	public final String getErrorMessage(String errorType, String message) {
		return source.formatPositionLine(errorType, runtime.getMaximumPosition(), message);
	}

	public final String getSyntaxErrorMessage() {
		return source.formatPositionLine("error", getMaximumPosition(), "syntax error");
	}

	public final String getUnconsumedMessage() {
		return source.formatPositionLine("unconsumed", getPosition(), "");
	}

	public String getResourceName() {
		return source.getResourceName();
	}

	public void startProfiling(ParserProfiler prof) {
		// TODO Auto-generated method stub

	}

	public void doneProfiling(ParserProfiler prof) {
		// TODO Auto-generated method stub

	}

}
