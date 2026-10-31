package io.github.akiraisozaki.hamon.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.akiraisozaki.hamon.api.model.ImpactItem;
import tools.jackson.databind.json.JsonMapper;

/** V2__sample_graph.sql のグラフを前提にした、API から Oracle まで通す統合テスト */
@SpringBootTest
@AutoConfigureMockMvc
class ImpactApiTest {

	private static final String ORDER_SERVICE_CANCEL = "com.example.order.OrderService#cancel";

	@Autowired
	private MockMvcTester mvc;

	private final JsonMapper json = new JsonMapper();

	private MvcTestResult survey(String target, double minProbability) {
		return mvc.get().uri("/api/impact").param("target", target).param("minProbability", String.valueOf(minProbability)).exchange();
	}

	private List<ImpactItem> surveyItems(String target, double minProbability) {
		MvcTestResult result = survey(target, minProbability);
		assertThat(result).hasStatus(HttpStatus.OK);
		return List.of(json.readValue(result.getResponse().getContentAsByteArray(), ImpactItem[].class));
	}

	@Test
	void 変更点に依存するノードが確からしさの高い順に返る() {
		List<ImpactItem> items = surveyItems(ORDER_SERVICE_CANCEL, 0.5);

		assertThat(items).extracting(ImpactItem::getNode).containsExactly(
				"com.example.order.OrderController#cancel",
				"com.example.order.OrderFacade#cancelWithRefund",
				"com.example.api.OrderCancelEndpoint#post",
				"web/order/cancel.html",
				"com.example.batch.OrderCancelJob#run");
	}

	@Test
	void 確からしさは経路上の辺の確率の積になる() {
		List<ImpactItem> items = surveyItems(ORDER_SERVICE_CANCEL, 0.5);

		ImpactItem endpoint = items.stream().filter(i -> i.getNode().equals("com.example.api.OrderCancelEndpoint#post")).findFirst().orElseThrow();
		assertThat(endpoint.getProbability()).isCloseTo(0.95 * 0.90, within(1e-9));
		assertThat(endpoint.getPath()).containsExactly(ORDER_SERVICE_CANCEL, "com.example.order.OrderFacade#cancelWithRefund", "com.example.api.OrderCancelEndpoint#post");
	}

	@Test
	void 複数の経路で届くノードは最も確からしい経路だけ返る() {
		List<ImpactItem> items = surveyItems("ORDERS.STATUS", 0.0);

		List<ImpactItem> dailyReport = items.stream().filter(i -> i.getNode().equals("com.example.report.DailyReportJob#run")).toList();
		assertThat(dailyReport).hasSize(1);
		assertThat(dailyReport.getFirst().getProbability()).isCloseTo(0.90, within(1e-9));
		assertThat(dailyReport.getFirst().getPath()).containsExactly("ORDERS.STATUS", "com.example.report.DailyReportJob#run");
	}

	@Test
	void 閾値未満の候補は返さない() {
		List<ImpactItem> items = surveyItems(ORDER_SERVICE_CANCEL, 0.5);

		assertThat(items).extracting(ImpactItem::getNode).doesNotContain("com.example.common.MethodInvoker#invoke");
	}

	@Test
	void 誰も依存していないノードなら空になる() {
		assertThat(surveyItems("web/order/cancel.html", 0.0)).isEmpty();
	}

	@Test
	void 確率の範囲外を指定すると400になる() {
		assertThat(survey("x", 1.5)).hasStatus(HttpStatus.BAD_REQUEST);
	}
}
