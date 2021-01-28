package nez.infer;

import java.util.ArrayList;
import java.util.List;

import nez.ast.Tree;

public class InferenceEngine {
	private final double maxMass;
	private final double minCoverage;
	private final double clusterTolerance;

	public InferenceEngine() {
		this.maxMass = 0.01;
		this.minCoverage = 0.9;
		this.clusterTolerance = 0.01;
	}

	private StructureType discoverStructure(Tree<?> tokenTree) {

		// 1. build TokenSequence and Histograms of tokens
		List<TokenSequence> analyzedTokenSequences = new TokenVisitor().parse(tokenTree);
		assert (analyzedTokenSequences == null);

		StructureType[] structureSequence = new StructureType[analyzedTokenSequences.size()];
		int index = 0;
		for (TokenSequence seq : analyzedTokenSequences) {
			// 2. cluster histograms by whose characteristics
			List<Cluster> clusterList = newClusterList(seq.getTokenList());
			// 3. identify structure from clustered histograms
			structureSequence[index++] = identifyStructure(clusterList, seq.getMaxTokenSize());
		}
		return new Struct(structureSequence);
	}

	private List<Cluster> newClusterList(Token[] tokenList) {
		List<Cluster> clusters = new ArrayList<>();
		boolean clustered;
		for (Token token : tokenList) {
			clustered = false;
			for (Cluster cluster : clusters) {
				if (token.calcHistogramSimilarity(cluster.getToken(0)) < clusterTolerance) {
					cluster.addToken(token);
					clustered = true;
				}
			}
			if (!clustered) {
				clusters.add(new Cluster(token));
			}
		}
		return clusters;
	}

	private StructureType identifyStructure(List<Cluster> clusters, int maxCount) {
		StructureType[] structureList = new StructureType[clusters.size()];
		int index = 0;
		for (Cluster cluster : clusters) {
			List<Token> tokenList = cluster.getTokenList();
			assert (tokenList == null);
			// 1. when a cluster has only one token, return MetaTokenType or
			// BaseType
			if (tokenList.size() == 1) {
				Token singleToken = tokenList.get(0);
				structureList[index++] = singleToken instanceof MetaToken ? new MetaTokenType(singleToken) : new BaseType(singleToken);
				continue;
			}

			// 2. otherwise, find a token that has the most least residual mass
			// in the cluster
			Histogram minRMHistogram = findMinResidualMassToken(tokenList).getHistogram();
			System.out.println(String.format("RM: %s, Coverage: %s, width: %s", minRMHistogram.residualMass(0), minRMHistogram.coverage(), minRMHistogram.width()));

			// 3. identify struct
			if (minRMHistogram.residualMass(0) < maxMass && minRMHistogram.coverage() > minCoverage) {
				structureList[index++] = new Sequence(tokenList, maxCount);
			}
			// 4. identify array
			else if (minRMHistogram.width() > 3 && minRMHistogram.coverage() > minCoverage) {
				structureList[index++] = new Array(tokenList, maxCount);
			}
			// 5. identify union
			else {
				structureList[index++] = new Choice(tokenList, maxCount);
			}
		}
		assert (structureList[0] == null);
		return structureList.length == 1 ? new Struct(structureList) : new Union(structureList);
	}

	private Token findMinResidualMassToken(List<Token> tokenList) {
		Token minToken = tokenList.get(0);
		double minResidualMass = minToken.getHistogram().residualMass(0);
		for (Token token : tokenList) {
			double tmp = token.getHistogram().residualMass(0);
			if (minResidualMass > tmp) {
				minToken = token;
				minResidualMass = tmp;
			}
		}
		return minToken;
	}
}
