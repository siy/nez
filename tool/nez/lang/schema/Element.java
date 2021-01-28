package nez.lang.schema;

import nez.lang.Expression;

public class Element extends Schema {

	private boolean optional;
	private final String elementName;
	private final String structName;

	public Element(String elementName, String structName, Expression e, boolean optional) {
		super(e);
		this.elementName = elementName;
		this.structName = structName;
		this.optional = optional;
	}

	public String getElementName() {
		return elementName;
	}

	public String getUniqueName() {
		return String.format("%s_%s", structName, elementName);
	}

	public void setOptional(boolean optional) {
		this.optional = optional;
	}

	public boolean isOptional() {
		return optional;
	}

}