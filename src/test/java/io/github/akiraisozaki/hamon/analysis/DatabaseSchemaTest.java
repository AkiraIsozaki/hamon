package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.NodeKind;

class DatabaseSchemaTest {

	private static final String STATEMENT = "com.example.OrderMapper#find";

	private static DatabaseSchema schema() {
		return new DatabaseSchema(Map.of(
				"ORDERS", Set.of("ID", "STATUS", "CUSTOMER_ID"),
				"CUSTOMERS", Set.of("ID", "NAME")));
	}

	@Test
	void 表はTABLEノード_列は表ドット列のCOLUMNノードになる() {
		Map<String, NodeKind> nodes = schema().nodes();

		assertThat(nodes).containsEntry("ORDERS", NodeKind.TABLE)
				.containsEntry("ORDERS.STATUS", NodeKind.COLUMN)
				.containsEntry("CUSTOMERS.NAME", NodeKind.COLUMN)
				.hasSize(7);
	}

	@Test
	void SQLに出てくる表と_その表だけが持つ列は確実な辺になる() {
		var edges = schema().dependenciesOf(STATEMENT, "select status from orders where customer_id = ?");

		assertThat(edges).containsExactlyInAnyOrder(
				DependencyEdge.certain(STATEMENT, "ORDERS"),
				DependencyEdge.certain(STATEMENT, "ORDERS.STATUS"),
				DependencyEdge.certain(STATEMENT, "ORDERS.CUSTOMER_ID"));
	}

	@Test
	void 複数の参照表にある列名はそれぞれへのあいまいな辺になる() {
		var edges = schema().dependenciesOf(STATEMENT, "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id");

		assertThat(edges).contains(
				DependencyEdge.ambiguous(STATEMENT, "ORDERS.ID"),
				DependencyEdge.ambiguous(STATEMENT, "CUSTOMERS.ID"));
	}

	@Test
	void 参照していない表の列名は辺にならない() {
		var edges = schema().dependenciesOf(STATEMENT, "SELECT name FROM orders");

		assertThat(edges).containsExactly(DependencyEdge.certain(STATEMENT, "ORDERS"));
	}

	@Test
	void バインド変数の名前は列名と照合しない() {
		var edges = schema().dependenciesOf(STATEMENT, "INSERT INTO orders (id) VALUES (#{status})");

		assertThat(edges).extracting(DependencyEdge::dependency).doesNotContain("ORDERS.STATUS");
	}
}
