package io.github.akiraisozaki.hamon.graph;

/** 保存前の辺。ノードを名前で指す。dependent が dependency を使っていて、その確からしさが probability */
public record DependencyEdge(String dependent, String dependency, double probability) {

	/** 静的に断定できない辺に与える仮の確からしさ。Phase 5 で Jev が付け直す */
	static final double AMBIGUOUS_PROBABILITY = 0.5;

	public DependencyEdge {
		if (probability < 0 || probability > 1) {
			throw new IllegalArgumentException("確からしさは 0〜1: " + probability);
		}
	}

	/** 確実な辺（型解決できた呼び出しなど） */
	public static DependencyEdge certain(String dependent, String dependency) {
		return new DependencyEdge(dependent, dependency, 1.0);
	}

	/** あいまいな辺（同名メソッド、複数の表にある列名など） */
	public static DependencyEdge ambiguous(String dependent, String dependency) {
		return new DependencyEdge(dependent, dependency, AMBIGUOUS_PROBABILITY);
	}
}
