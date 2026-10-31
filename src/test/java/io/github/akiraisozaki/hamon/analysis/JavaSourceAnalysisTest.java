package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;

class JavaSourceAnalysisTest {

	private static DependencyGraph analyzeFixture() {
		return new JavaSourceAnalysis().analyze(List.of(Path.of("src/test/resources/analysis-fixture")));
	}

	@Test
	void 宣言されたメソッドがすべてノードになる() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.nodes().keySet()).containsExactlyInAnyOrder(
				"fixture.OrderRepository#updateStatus",
				"fixture.OrderService#cancel",
				"fixture.OrderService#audit",
				"fixture.OrderController#cancel",
				"fixture.Invoker#run");
	}

	@Test
	void 型解決できた呼び出しは確からしさ1の辺になる() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.edges()).contains(
				new DependencyEdge("fixture.OrderController#cancel", "fixture.OrderService#cancel", 1.0),
				new DependencyEdge("fixture.OrderService#cancel", "fixture.OrderRepository#updateStatus", 1.0),
				new DependencyEdge("fixture.OrderService#cancel", "fixture.OrderService#audit", 1.0));
	}

	@Test
	void 型解決できない呼び出しは同名のメソッド全部へのあいまいな辺になる() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.edges()).filteredOn(edge -> edge.dependent().equals("fixture.Invoker#run")).containsExactlyInAnyOrder(
				new DependencyEdge("fixture.Invoker#run", "fixture.OrderService#cancel", 0.5),
				new DependencyEdge("fixture.Invoker#run", "fixture.OrderController#cancel", 0.5));
	}

	@Test
	void 解析対象の外への呼び出しは辺にもノードにもならない() {
		DependencyGraph graph = analyzeFixture();

		assertThat(graph.nodes().keySet()).noneMatch(node -> node.startsWith("java."));
		assertThat(graph.edges()).filteredOn(edge -> edge.dependent().equals("fixture.OrderController#cancel")).hasSize(1);
	}
}
