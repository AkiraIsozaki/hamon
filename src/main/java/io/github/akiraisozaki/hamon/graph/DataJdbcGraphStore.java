package io.github.akiraisozaki.hamon.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("datajdbc")
class DataJdbcGraphStore implements GraphStore {

	private final NodeRepository nodes;
	private final EdgeRepository edges;

	DataJdbcGraphStore(NodeRepository nodes, EdgeRepository edges) {
		this.nodes = nodes;
		this.edges = edges;
	}

	@Override
	@Transactional
	public void replaceWith(DependencyGraph graph) {
		edges.deleteAll();
		nodes.deleteAll();
		List<Node> unsaved = graph.nodes().entrySet().stream().map(e -> new Node(null, e.getKey(), e.getValue())).toList();
		Map<String, Long> idOf = new HashMap<>();
		nodes.saveAll(unsaved).forEach(saved -> idOf.put(saved.name(), saved.id())); // save は id の入った新しい Node を返す
		edges.saveAll(graph.edges().stream()
				.map(edge -> new Edge(null, idOf.get(edge.dependent()), idOf.get(edge.dependency()), edge.probability()))
				.toList());
	}
}
