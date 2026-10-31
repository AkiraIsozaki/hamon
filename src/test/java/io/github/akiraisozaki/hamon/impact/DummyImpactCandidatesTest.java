package io.github.akiraisozaki.hamon.impact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.akiraisozaki.hamon.api.model.ImpactItem;

class DummyImpactCandidatesTest {

	private static List<ImpactItem> survey(double minProbability) {
		return new DummyImpactCandidates().survey("com.example.order.OrderService#cancel", minProbability);
	}

	@Test
	void 候補は確からしさの高い順に並ぶ() {
		List<ImpactItem> items = survey(0.0);

		assertThat(items).extracting(ImpactItem::getProbability).isSortedAccordingTo((a, b) -> Double.compare(b, a));
	}

	@Test
	void 閾値未満の候補は返さない() {
		List<ImpactItem> items = survey(0.5);

		assertThat(items).allSatisfy(item -> assertThat(item.getProbability()).isGreaterThanOrEqualTo(0.5));
		assertThat(items).isNotEmpty();
	}

	@Test
	void 経路は変更点から始まり候補ノードで終わる() {
		List<ImpactItem> items = survey(0.0);

		assertThat(items).allSatisfy(item -> {
			assertThat(item.getPath().getFirst()).isEqualTo("com.example.order.OrderService#cancel");
			assertThat(item.getPath().getLast()).isEqualTo(item.getNode());
		});
	}
}
