package io.github.akiraisozaki.hamon.impact;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.akiraisozaki.hamon.api.model.ImpactItem;

/** 影響調査。変更点から逆向きに辿った影響候補を API の形で返す。REST と MCP の両方から呼ばれる */
@Component
public class ImpactSurvey {

	private final ImpactMapper mapper;

	ImpactSurvey(ImpactMapper mapper) {
		this.mapper = mapper;
	}

	public List<ImpactItem> surveyImpact(String target, double minProbability) {
		return mapper.traceUpstream(target, minProbability).stream().map(ImpactRow::toItem).toList();
	}
}
