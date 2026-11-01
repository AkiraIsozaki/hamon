package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.NodeKind;
import io.github.akiraisozaki.hamon.judgment.Ambiguity;
import io.github.akiraisozaki.hamon.judgment.FixedDependencyJudge;

class DatabaseSchemaTest {

	private static final String STATEMENT = "com.example.OrderMapper#find";

	private static final FixedDependencyJudge NEVER_ASKED = new FixedDependencyJudge(0.5);

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
		var edges = schema().dependenciesOf(STATEMENT, "select status from orders where customer_id = ?", NEVER_ASKED);

		assertThat(edges).containsExactlyInAnyOrder(
				DependencyEdge.certain(STATEMENT, "ORDERS"),
				DependencyEdge.certain(STATEMENT, "ORDERS.STATUS"),
				DependencyEdge.certain(STATEMENT, "ORDERS.CUSTOMER_ID"));
	}

	@Test
	void 複数の参照表にある列名はどの表の列かを判定に聞き_その確からしさで辺になる() {
		FixedDependencyJudge judge = new FixedDependencyJudge(0.8);

		var edges = schema().dependenciesOf(STATEMENT, "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id", judge);

		assertThat(edges).contains(
				new DependencyEdge(STATEMENT, "ORDERS.ID", 0.8),
				new DependencyEdge(STATEMENT, "CUSTOMERS.ID", 0.8));
		Ambiguity ambiguity = judge.asked().getFirst();
		assertThat(ambiguity.state()).containsEntry("statement", STATEMENT).containsKey("sql");
		assertThat(ambiguity.candidates()).extracting(Ambiguity.Candidate::dependency).containsExactlyInAnyOrder("ORDERS.ID", "CUSTOMERS.ID");
	}

	@Test
	void 参照していない表の列名は辺にならない() {
		var edges = schema().dependenciesOf(STATEMENT, "SELECT name FROM orders", NEVER_ASKED);

		assertThat(edges).containsExactly(DependencyEdge.certain(STATEMENT, "ORDERS"));
	}

	@Test
	void バインド変数の名前は列名と照合しない() {
		var edges = schema().dependenciesOf(STATEMENT, "INSERT INTO orders (id) VALUES (#{status})", NEVER_ASKED);

		assertThat(edges).extracting(DependencyEdge::dependency).doesNotContain("ORDERS.STATUS");
	}
}
