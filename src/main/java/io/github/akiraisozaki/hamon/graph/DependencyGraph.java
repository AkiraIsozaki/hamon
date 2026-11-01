package io.github.akiraisozaki.hamon.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 保存前の依存グラフ。辺の両端は必ず nodes にある */
public record DependencyGraph(Map<String, NodeKind> nodes, List<DependencyEdge> edges) {

	public DependencyGraph {
		nodes = Map.copyOf(nodes);
		edges = List.copyOf(edges);
		for (DependencyEdge edge : edges) {
			if (!nodes.containsKey(edge.dependent()) || !nodes.containsKey(edge.dependency())) {
				throw new IllegalArgumentException("辺の端がノードにない: " + edge);
			}
		}
	}

	/** other のノードと辺を足した新しいグラフ。同名のノードは other の種類が勝つ */
	public DependencyGraph merge(DependencyGraph other) {
		Map<String, NodeKind> mergedNodes = new LinkedHashMap<>(nodes);
		mergedNodes.putAll(other.nodes);
		List<DependencyEdge> mergedEdges = new ArrayList<>(edges);
		mergedEdges.addAll(other.edges);
		return new DependencyGraph(mergedNodes, mergedEdges);
	}
}
