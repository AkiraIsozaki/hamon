package io.github.akiraisozaki.hamon.graph;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("mybatis")
class MyBatisGraphStore implements GraphStore {

	private final GraphMapper mapper;

	MyBatisGraphStore(GraphMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	@Transactional
	public void replaceWith(DependencyGraph graph) {
		mapper.deleteAllEdges();
		mapper.deleteAllNodes();
		graph.nodes().forEach(mapper::insertNode);
		Map<String, Long> idOf = mapper.selectAllNodes().stream().collect(Collectors.toMap(Node::name, Node::id));
		for (DependencyEdge edge : graph.edges()) {
			mapper.insertEdge(idOf.get(edge.dependent()), idOf.get(edge.dependency()), edge.probability());
		}
	}
}
