package io.github.akiraisozaki.hamon.judgment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** テスト用。全候補に同じ確からしさを返し、何を聞かれたかを覚えておく（Jev は管理外の外部 API なので本物は使わない） */
public final class FixedDependencyJudge implements DependencyJudge {

	private final double probability;
	private final List<Ambiguity> asked = new ArrayList<>();

	public FixedDependencyJudge(double probability) {
		this.probability = probability;
	}

	@Override
	public Map<String, Double> judge(Ambiguity ambiguity) {
		asked.add(ambiguity);
		return ambiguity.candidates().stream().collect(Collectors.toMap(Ambiguity.Candidate::dependency, candidate -> probability, (a, b) -> a, LinkedHashMap::new));
	}

	public List<Ambiguity> asked() {
		return List.copyOf(asked);
	}
}
