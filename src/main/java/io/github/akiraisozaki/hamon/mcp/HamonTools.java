package io.github.akiraisozaki.hamon.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import io.github.akiraisozaki.hamon.analysis.Analysis;
import io.github.akiraisozaki.hamon.api.model.AnalysisResult;
import io.github.akiraisozaki.hamon.api.model.ImpactItem;
import io.github.akiraisozaki.hamon.graph.DependencyGraph;
import io.github.akiraisozaki.hamon.impact.ImpactSurvey;

/**
 * Claude Code に見せる MCP ツール。REST API と同じ 2 つの操作を、同じ裏側で公開する（ADR-0006）。
 * 引数は MCP 経由で外から来るので、ここで検証する。RuntimeException はツールのエラー結果としてモデルに返る
 */
@Component
class HamonTools {

	/** openapi.yaml の minProbability の既定値と揃える */
	private static final double DEFAULT_MIN_PROBABILITY = 0.05;

	private final ImpactSurvey survey;
	private final Analysis analysis;

	HamonTools(ImpactSurvey survey, Analysis analysis) {
		this.survey = survey;
		this.analysis = analysis;
	}

	@McpTool(name = "survey_impact", description = "変更点（クラス#メソッド、TABLE、TABLE.COLUMN）を変えたときに影響を受ける候補を、確からしさの高い順に返す。"
			+ "probability は経路上の辺の確からしさの積、path は変更点から候補までのノード列")
	List<ImpactItem> surveyImpact(
			@McpToolParam(description = "変更点のノード名。例: io.github.akiraisozaki.hamon.impact.ImpactRow#toItem, EDGE.PROBABILITY", required = true) String target,
			@McpToolParam(description = "この確からしさ未満の候補は返さない（漏れの許容度、0〜1）。省略時 0.05", required = false) Double minProbability) {
		if (target == null || target.isBlank()) {
			throw new IllegalArgumentException("target を指定してください");
		}
		double threshold = minProbability == null ? DEFAULT_MIN_PROBABILITY : minProbability; // 省略可の引数は MCP から null で来る
		if (threshold < 0 || threshold > 1) {
			throw new IllegalArgumentException("minProbability は 0〜1: " + threshold);
		}
		return survey.surveyImpact(target, threshold);
	}

	@McpTool(name = "analyze_sources", description = "設定されたソース（Java、MyBatis XML）と接続中の DB のスキーマを解析し、依存グラフを作り直す。"
			+ "あいまいな辺は Jev が判定する。ソースを変更した後に呼ぶ")
	AnalysisResult analyzeSources() {
		DependencyGraph graph = analysis.run();
		return new AnalysisResult(graph.nodes().size(), graph.edges().size());
	}
}
