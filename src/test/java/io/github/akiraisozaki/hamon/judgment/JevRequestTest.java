package io.github.akiraisozaki.hamon.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JevRequestTest {

	private static Ambiguity twoCandidates() {
		Map<String, String> declarations = new LinkedHashMap<>();
		declarations.put("fixture.OrderService#cancel", "void cancel()");
		declarations.put("fixture.OrderController#cancel", "void cancel(long id)");
		return Ambiguity.methodCall("fixture.Invoker#run", "void run() { Unknown.cancel(); }", "Unknown.cancel()", declarations);
	}

	@Test
	void 候補ごとに番号付きのNoul質問になり_質問文と候補の説明がinstructionsに入る() {
		JevRequest request = JevRequest.of("jev-1.14.0", twoCandidates());

		assertThat(request.model()).isEqualTo("jev-1.14.0");
		assertThat(request.state()).containsEntry("call", "Unknown.cancel()").containsKey("caller_source");
		assertThat(request.questions()).containsOnlyKeys("candidate_0", "candidate_1");
		JevRequest.Question first = request.questions().get("candidate_0");
		assertThat(first.type()).isEqualTo("noul");
		assertThat(first.instructions()).containsEntry("question", "Is this the method that `call` in `caller_source` invokes?")
				.containsEntry("method", "fixture.OrderService#cancel")
				.containsEntry("declaration", "void cancel()");
	}

	@Test
	void 答えは同じ番号で候補のノード名に戻る() {
		JevRequest.Response response = new JevRequest.Response("jev-1.14.0",
				Map.of("candidate_0", new JevRequest.Answer(0.92), "candidate_1", new JevRequest.Answer(0.08)));

		Map<String, Double> probabilities = JevRequest.probabilities(twoCandidates(), response);

		assertThat(probabilities).containsExactly(
				Map.entry("fixture.OrderService#cancel", 0.92),
				Map.entry("fixture.OrderController#cancel", 0.08));
	}

	@Test
	void 答えが欠けていたら例外になる() {
		JevRequest.Response response = new JevRequest.Response("jev-1.14.0", Map.of("candidate_0", new JevRequest.Answer(0.92)));

		assertThatThrownBy(() -> JevRequest.probabilities(twoCandidates(), response)).isInstanceOf(IllegalStateException.class);
	}
}
