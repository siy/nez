package nez.infer;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class TokenSequence {
	protected Map<String, Token> tokenMap;
	private int tokenCount;
	private int maxTokenCount;

	public TokenSequence() {
		this.tokenMap = new HashMap<>();
	}

	public Map<String, Token> getTokenMap() {
		return tokenMap;
	}

	public int getMaxTokenSize() {
		return maxTokenCount;
	}

	public final void transaction(String label, Token token) {
		if (!tokenMap.containsKey(label)) {
			token.getHistogram().update(tokenCount++);
			tokenMap.put(label, token);
		} else {
			tokenMap.get(label).getHistogram().update(tokenCount++);
		}
	}

	public final Token[] getTokenList() {
		Token[] tokenList = new Token[tokenMap.size()];
		int index = 0;
		for (Entry<String, Token> entry : tokenMap.entrySet()) {
			tokenList[index++] = entry.getValue();
		}
		normalizeAllHistograms(tokenList);
		return tokenList;
	}

	public final void commitAllHistograms() {
		for (Entry<String, Token> token : tokenMap.entrySet()) {
			token.getValue().getHistogram().commit();
		}
		this.maxTokenCount = Math.max(maxTokenCount, tokenCount);
		tokenCount = 0;
	}

	private void normalizeAllHistograms(Token[] tokenList) {
		for (Token token : tokenList) {
			token.getHistogram().normalize();
		}
	}

}
