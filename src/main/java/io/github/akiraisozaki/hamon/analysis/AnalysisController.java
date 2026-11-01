package io.github.akiraisozaki.hamon.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.akiraisozaki.hamon.api.AnalysisApi;
import io.github.akiraisozaki.hamon.api.model.AnalysisResult;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;

/** openapi.yaml から生成された {@link AnalysisApi} の実装 */
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {

	private final Analysis analysis;

	AnalysisController(Analysis analysis) {
		this.analysis = analysis;
	}

	@Override
	public ResponseEntity<AnalysisResult> analyzeSources() {
		DependencyGraph graph = analysis.run();
		return ResponseEntity.ok(new AnalysisResult(graph.nodes().size(), graph.edges().size()));
	}
}
