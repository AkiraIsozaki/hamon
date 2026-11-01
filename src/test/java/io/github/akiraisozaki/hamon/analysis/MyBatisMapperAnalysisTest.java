package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.graph.NodeKind;

class MyBatisMapperAnalysisTest {

	private static final String UPDATE_STATUS = "fixture.OrderRepository#updateStatus";
	private static final String COUNT_BY_KIND = "fixture.OrderRepository#countByKind";

	private static DependencyGraph analyzeFixture() {
		DatabaseSchema schema = new DatabaseSchema(Map.of(
				"NODE", Set.of("ID", "NAME", "KIND"),
				"EDGE", Set.of("ID", "DEPENDENT_ID", "DEPENDENCY_ID", "PROBABILITY")));
		return new MyBatisMapperAnalysis().analyze(List.of(Path.of("src/test/resources/analysis-fixture")), schema);
	}

	@Test
	void SQL文はnamespaceとidを繋いだMETHODノードになり_resultMapやマッパー以外のXMLはノードにならない() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.nodes()).containsEntry(UPDATE_STATUS, NodeKind.METHOD).containsEntry(COUNT_BY_KIND, NodeKind.METHOD);
		assertThat(graph.nodes().keySet()).filteredOn(node -> node.contains("#")).hasSize(2);
	}

	@Test
	void SQL文からそれが使う表と列へ辺が張られる() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.edges()).filteredOn(edge -> edge.dependent().equals(UPDATE_STATUS)).containsExactlyInAnyOrder(
				DependencyEdge.certain(UPDATE_STATUS, "NODE"),
				DependencyEdge.certain(UPDATE_STATUS, "NODE.KIND"),
				DependencyEdge.certain(UPDATE_STATUS, "NODE.NAME"));
		assertThat(graph.edges()).contains(
				DependencyEdge.certain(COUNT_BY_KIND, "EDGE.DEPENDENT_ID"),
				DependencyEdge.ambiguous(COUNT_BY_KIND, "NODE.ID"),
				DependencyEdge.ambiguous(COUNT_BY_KIND, "EDGE.ID"));
	}
}
