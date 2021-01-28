package nez.parser;

@SuppressWarnings("serial")
public class TerminationException extends Exception {
	public boolean status;

	public TerminationException(boolean status) {
		this.status = status;
	}
}
