package io.github.akiraisozaki.hamon.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.akiraisozaki.hamon.api.AnalysisApi;
import io.github.akiraisozaki.hamon.api.model.AnalysisResult;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.GraphStore;

/** openapi.yaml から生成された {@link AnalysisApi} の実装。Java の解析と MyBatis XML の解析を合わせて保存する */
@RestController
@RequestMapping("/api")
class AnalysisController implements AnalysisApi {

	private final AnalysisProperties properties;
	private final JavaSourceAnalysis javaAnalysis;
	private final MyBatisMapperAnalysis mapperAnalysis;
	private final SchemaMapper schemaMapper;
	private final GraphStore store;

	AnalysisController(AnalysisProperties properties, JavaSourceAnalysis javaAnalysis, MyBatisMapperAnalysis mapperAnalysis,
			SchemaMapper schemaMapper, GraphStore store) {
		this.properties = properties;
		this.javaAnalysis = javaAnalysis;
		this.mapperAnalysis = mapperAnalysis;
		this.schemaMapper = schemaMapper;
		this.store = store;
	}

	@Override
	public ResponseEntity<AnalysisResult> analyzeSources() {
		DatabaseSchema schema = DatabaseSchema.of(schemaMapper.selectColumns());
		DependencyGraph graph = javaAnalysis.analyze(properties.sourceRoots())
				.merge(mapperAnalysis.analyze(properties.mapperRoots(), schema));
		store.replaceWith(graph);
		return ResponseEntity.ok(new AnalysisResult(graph.nodes().size(), graph.edges().size()));
	}
}
