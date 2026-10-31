package io.github.akiraisozaki.hamon.impact;

import java.util.List;

/** 確率つき依存グラフへの問い合わせ。実装（MyBatis / Spring Data JDBC）は ImpactGraphConfiguration がプロファイルで選ぶ。 */
@FunctionalInterface
interface ImpactGraph {

	/** 変更点から影響が広がる先を、確からしさの高い順に返す。同じノードに複数の経路で届く場合は最も確からしい経路だけ。 */
	List<ImpactRow> traceUpstream(String target, double minProbability);
}
