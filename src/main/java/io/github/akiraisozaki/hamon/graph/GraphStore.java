package io.github.akiraisozaki.hamon.graph;

/** 依存グラフの保存。実装は MyBatis / Spring Data JDBC をプロファイルで選ぶ（ADR-0001） */
public interface GraphStore {

	/** 保存済みのグラフを全部消して graph に置き換える。1 トランザクションで行う */
	void replaceWith(DependencyGraph graph);
}
