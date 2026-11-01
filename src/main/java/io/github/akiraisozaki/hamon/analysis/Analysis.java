package io.github.akiraisozaki.hamon.analysis;

import org.springframework.stereotype.Component;

import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.GraphStore;

/** 解析（GLOSSARY）：Java と MyBatis XML と DB のスキーマを読み、保存済みのグラフを作り直す。REST と MCP の両方から呼ばれる */
@Component
public class Analysis {

	private final AnalysisProperties properties;
	private final JavaSourceAnalysis javaAnalysis;
	private final MyBatisMapperAnalysis mapperAnalysis;
	private final SchemaMapper schemaMapper;
	private final GraphStore store;

	Analysis(AnalysisProperties properties, JavaSourceAnalysis javaAnalysis, MyBatisMapperAnalysis mapperAnalysis,
			SchemaMapper schemaMapper, GraphStore store) {
		this.properties = properties;
		this.javaAnalysis = javaAnalysis;
		this.mapperAnalysis = mapperAnalysis;
		this.schemaMapper = schemaMapper;
		this.store = store;
	}

	/** 作り直したグラフを返す */
	public DependencyGraph run() {
		DatabaseSchema schema = DatabaseSchema.of(schemaMapper.selectColumns());
		DependencyGraph graph = javaAnalysis.analyze(properties.sourceRoots())
				.merge(mapperAnalysis.analyze(properties.mapperRoots(), schema));
		store.replaceWith(graph);
		return graph;
	}
}
