package io.github.akiraisozaki.hamon.judgment;

import java.util.Map;

/**
 * あいまいな辺の確からしさを判定する。本番は Jev（管理外の外部 API）。
 * テストでは固定値を返す実装に差し替える
 */
@FunctionalInterface
public interface DependencyJudge {

	/** ambiguity の候補それぞれについて「dependent が本当にそれを使っている確率」。キーは候補の dependency ノード名 */
	Map<String, Double> judge(Ambiguity ambiguity);
}
