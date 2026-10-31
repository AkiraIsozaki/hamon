package io.github.akiraisozaki.hamon.graph;

/** 保存前の辺。ノードを名前で指す。dependent が dependency を使っていて、その確からしさが probability */
public record DependencyEdge(String dependent, String dependency, double probability) {

	public DependencyEdge {
		if (probability < 0 || probability > 1) {
			throw new IllegalArgumentException("確からしさは 0〜1: " + probability);
		}
	}
}
