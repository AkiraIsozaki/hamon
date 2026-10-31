package io.github.akiraisozaki.hamon.impact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ImpactController.class)
@Import(DummyImpactCandidates.class)
class ImpactControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void 変更点を指定すると影響候補の一覧がJSONで返る() {
		var result = mvc.get().uri("/api/impact").param("target", "com.example.order.OrderService#cancel").exchange();

		assertThat(result).hasStatus(HttpStatus.OK).bodyJson()
				.extractingPath("$[0].path[0]").isEqualTo("com.example.order.OrderService#cancel");
	}

	@Test
	void 確率の範囲外を指定すると400になる() {
		var result = mvc.get().uri("/api/impact").param("target", "x").param("minProbability", "1.5").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}
}
