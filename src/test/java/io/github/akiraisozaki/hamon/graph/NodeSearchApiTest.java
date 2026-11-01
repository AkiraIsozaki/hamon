package io.github.akiraisozaki.hamon.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** sample-graph.sql のノードを前提にした、ノード検索の統合テスト */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/sample-graph.sql")
class NodeSearchApiTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void 名前の一部で大文字小文字を区別せずに探せて_種類が付く() {
		var result = mvc.get().uri("/api/nodes").param("q", "orders.").exchange();

		assertThat(result).hasStatus(HttpStatus.OK).bodyJson()
				.extractingPath("$[*].name").asArray().containsExactly("ORDERS.STATUS");
		assertThat(result).bodyJson().extractingPath("$[0].kind").isEqualTo("COLUMN");
	}

	@Test
	void 短い名前が先に来て_件数の上限で切れる() {
		var result = mvc.get().uri("/api/nodes").param("q", "cancel").param("limit", "2").exchange();

		assertThat(result).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[*].name").asArray()
				.containsExactly("web/order/cancel.html", "batch.order-cancel.cron");
	}

	@Test
	void 検索語が空なら400になる() {
		assertThat(mvc.get().uri("/api/nodes").param("q", "").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
	}
}
