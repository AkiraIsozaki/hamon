package io.github.akiraisozaki.hamon.graph;

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
}
