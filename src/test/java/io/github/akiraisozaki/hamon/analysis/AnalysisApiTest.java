package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** 解析 → 保存 → 影響調査を、フィクスチャのソースで通す。書き込みはテストのトランザクションで巻き戻す */
@SpringBootTest(properties = "hamon.analysis.source-roots=src/test/resources/analysis-fixture")
@AutoConfigureMockMvc
@Transactional
class AnalysisApiTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void 解析するとノードと辺の数が返る() {
		var result = mvc.post().uri("/api/analysis").exchange();

		var body = assertThat(result).hasStatus(HttpStatus.OK).bodyJson();
		body.extractingPath("$.nodeCount").isEqualTo(5);
		body.extractingPath("$.edgeCount").isEqualTo(5);
	}

	@Test
	void 解析したグラフで影響調査ができる() {
		mvc.post().uri("/api/analysis").exchange();

		var result = mvc.get().uri("/api/impact").param("target", "fixture.OrderService#cancel").param("minProbability", "0").exchange();

		assertThat(result).hasStatus(HttpStatus.OK).bodyJson()
				.extractingPath("$[*].node").asArray()
				.containsExactly("fixture.OrderController#cancel", "fixture.Invoker#run");
	}

	@Test
	void もう一度解析しても同じ大きさになる() {
		mvc.post().uri("/api/analysis").exchange();

		var result = mvc.post().uri("/api/analysis").exchange();

		assertThat(result).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.nodeCount").isEqualTo(5);
	}
}
