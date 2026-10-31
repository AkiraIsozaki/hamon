package io.github.akiraisozaki.hamon.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.akiraisozaki.hamon.api.AnalysisApi;
import io.github.akiraisozaki.hamon.api.model.AnalysisResult;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.GraphStore;

/** openapi.yaml から生成された {@link AnalysisApi} の実装 */
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {

	private final AnalysisProperties properties;
	private final JavaSourceAnalysis analysis;
	private final GraphStore store;

	AnalysisController(AnalysisProperties properties, JavaSourceAnalysis analysis, GraphStore store) {
		this.properties = properties;
		this.analysis = analysis;
		this.store = store;
	}

	@Override
	public ResponseEntity<AnalysisResult> analyzeSources() {
		DependencyGraph graph = analysis.analyze(properties.sourceRoots());
		store.replaceWith(graph);
		return ResponseEntity.ok(new AnalysisResult(graph.nodes().size(), graph.edges().size()));
	}
}
