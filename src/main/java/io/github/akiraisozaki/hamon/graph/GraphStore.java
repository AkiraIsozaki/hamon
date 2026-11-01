package io.github.akiraisozaki.hamon.graph;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 依存グラフの保存（MyBatis、ADR-0003）。SQL は GraphMapper.xml */
@Component
public class GraphStore {

	private final GraphMapper mapper;

	GraphStore(GraphMapper mapper) {
		this.mapper = mapper;
	}

	/** 保存済みのグラフを全部消して graph に置き換える。1 トランザクションで行う */
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
