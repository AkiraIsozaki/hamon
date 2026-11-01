package io.github.akiraisozaki.hamon.graph;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.akiraisozaki.hamon.api.NodesApi;
import io.github.akiraisozaki.hamon.api.model.NodeSummary;

/** openapi.yaml から生成された {@link NodesApi} の実装。変更点の入力補完に使う */
@RestController
@RequestMapping("/api")
class NodeSearchController implements NodesApi {

	private final GraphMapper mapper;

	NodeSearchController(GraphMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public ResponseEntity<List<NodeSummary>> searchNodes(String q, Integer limit) {
		return ResponseEntity.ok(mapper.searchNodes(q, limit).stream().map(Node::toSummary).toList());
	}
}
