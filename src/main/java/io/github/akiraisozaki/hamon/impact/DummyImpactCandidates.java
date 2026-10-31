package io.github.akiraisozaki.hamon.impact;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.akiraisozaki.hamon.api.model.ImpactItem;

/**
 * 影響を受ける候補を、確からしさの高い順に返す。
 * shortcut: どんな変更点にも同じ固定の候補を返すダミー, Phase 2 で Oracle のグラフを逆向きに辿る実装に置き換える
 */
@Component
class DummyImpactCandidates {

	/** 変更点から候補へ至る経路（変更点自身は含まない。末尾が候補ノード）と、その確からしさ */
	private record Candidate(double probability, List<String> pathAfterTarget) {

		ImpactItem toItem(String target) {
			List<String> path = Stream.concat(Stream.of(target), pathAfterTarget.stream()).toList();
			return new ImpactItem(path.getLast(), probability, path);
		}
	}

	private static final List<Candidate> CANDIDATES = List.of(
			new Candidate(1.00, List.of("com.example.order.OrderController#cancel")),
			new Candidate(0.95, List.of("com.example.order.OrderFacade#cancelWithRefund")),
			new Candidate(0.80, List.of("com.example.order.OrderController#cancel", "web/order/cancel.html")),
			new Candidate(0.72, List.of("com.example.batch.OrderCancelJob#run")),
			new Candidate(0.60, List.of("com.example.order.OrderFacade#cancelWithRefund", "com.example.payment.RefundService#refund")),
			new Candidate(0.45, List.of("com.example.order.OrderFacade#cancelWithRefund", "com.example.payment.RefundService#refund", "PAYMENT.REFUND_STATUS")),
			new Candidate(0.30, List.of("com.example.batch.OrderCancelJob#run", "batch/order-cancel.properties")),
			new Candidate(0.12, List.of("com.example.common.MethodInvoker#invoke")),
			new Candidate(0.04, List.of("com.example.common.MethodInvoker#invoke", "com.example.report.DailyReportJob#run")));

	List<ImpactItem> survey(String target, double minProbability) {
		return CANDIDATES.stream()
				.filter(candidate -> candidate.probability() >= minProbability)
				.sorted(Comparator.comparingDouble(Candidate::probability).reversed())
				.map(candidate -> candidate.toItem(target))
				.toList();
	}
}
