package nez.debugger;

@SuppressWarnings("serial")
public class MachineExitException extends Exception {
	boolean result;

	public MachineExitException(boolean result) {
		this.result = result;
	}
}
