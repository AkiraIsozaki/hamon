package io.github.akiraisozaki.hamon.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractObjectArrayAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import io.github.akiraisozaki.hamon.judgment.DependencyJudge;
import io.github.akiraisozaki.hamon.judgment.FixedDependencyJudge;

/**
 * 解析 → 保存 → 影響調査を、フィクスチャのソースと実 DB のスキーマで通す。書き込みはテストのトランザクションで巻き戻す。
 * Jev は管理外の外部 API なので、固定値を返す判定に差し替える
 */
@SpringBootTest(properties = {
		"hamon.analysis.source-roots=src/test/resources/analysis-fixture",
		"hamon.analysis.mapper-roots=src/test/resources/analysis-fixture" })
@AutoConfigureMockMvc
@Transactional
class AnalysisApiTest {

	@TestConfiguration
	static class FixedJudgeConfiguration {

		@Bean
		@Primary // 本物の JevDependencyJudge も Bean として居るので、こちらを優先させる
		DependencyJudge fixedJudge() {
			return new FixedDependencyJudge(0.5);
		}
	}

	@Autowired
	private MockMvcTester mvc;

	private AbstractObjectArrayAssert<?, Object> impactOf(String target) {
		var result = mvc.get().uri("/api/impact").param("target", target).param("minProbability", "0").exchange();
		return assertThat(result).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[*].node").asArray();
	}

	@Test
	void 解析したグラフでメソッドの影響調査ができる() {
		mvc.post().uri("/api/analysis").exchange();

		impactOf("fixture.OrderService#cancel").containsExactly("fixture.OrderController#cancel", "fixture.Invoker#run");
	}

	@Test
	void 列を変更点にするとSQL文からコントローラまで辿れる() {
		mvc.post().uri("/api/analysis").exchange();

		impactOf("NODE.NAME").containsExactly(
				"fixture.OrderController#cancel",
				"fixture.OrderRepository#updateStatus",
				"fixture.OrderService#cancel",
				"fixture.Invoker#run");
	}

	@Test
	void もう一度解析しても同じ大きさになる() {
		var first = mvc.post().uri("/api/analysis").exchange();

		var second = mvc.post().uri("/api/analysis").exchange();

		assertThat(second).hasStatus(HttpStatus.OK).hasBodyTextEqualTo(new String(first.getResponse().getContentAsByteArray()));
	}
}
