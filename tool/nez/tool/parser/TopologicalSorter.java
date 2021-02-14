package nez.tool.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nez.util.Verbose;

public class TopologicalSorter {
	private final Map<String, Set<String>> nodes;
	private final Set<String> crossRefNames;
	private final List<String> result;
	private final Map<String, Short> visited;

	TopologicalSorter(Map<String, Set<String>> nodes, Set<String> crossRefNames) {
		this.nodes = nodes;
		this.crossRefNames = crossRefNames;
		this.result = new LinkedList<>();
		this.visited = new HashMap<>();
		for (var e : this.nodes.entrySet()) {
			if (visited.get(e.getKey()) == null) {
				visit(e.getKey(), e.getValue());
			}
		}
	}

	private void visit(String key, Set<String> nextNodes) {
		short visiting = 1;
		visited.put(key, visiting);
		if (nextNodes != null) {
			for (String nextNode : nextNodes) {
				var v = visited.get(nextNode);
				if (v == null) {
					visit(nextNode, nodes.get(nextNode));
				} else if (visiting == v) {
					if (!key.equals(nextNode)) {
						Verbose.println("Cyclic " + key + " => " + nextNode);
						crossRefNames.add(nextNode);
					}
				}
			}
		}
		Short visited1 = 2;
		visited.put(key, visited1);
		result.add(key);
	}

	public ArrayList<String> getResult() {
		return new ArrayList<>(result);
	}
}
