package nez.infer;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
	private final List<Token> tokenList;

	public Cluster(Token firstToken) {
		this.tokenList = new ArrayList<>();
		tokenList.add(firstToken);
	}

	public Token getToken(int index) {
		return tokenList.get(index);
	}

	public List<Token> getTokenList() {
		return tokenList;
	}

	public void addToken(Token t) {
		tokenList.add(t);
	}

}
