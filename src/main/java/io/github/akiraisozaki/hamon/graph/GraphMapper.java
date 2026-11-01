package io.github.akiraisozaki.hamon.graph;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis 版の書き込み。SQL は GraphMapper.xml */
@Mapper
interface GraphMapper {

	void deleteAllEdges();

	void deleteAllNodes();

	void insertNode(@Param("name") String name, @Param("kind") NodeKind kind);

	/** 採番された id を名前と一緒に引く（insert の戻りで受け取る代わり） */
	List<Node> selectAllNodes();

	/** 名前に q を含むノード（大文字小文字を区別しない）を、名前の短い順に limit 件まで */
	List<Node> searchNodes(@Param("q") String q, @Param("limit") int limit);

	void insertEdge(@Param("dependentId") long dependentId, @Param("dependencyId") long dependencyId, @Param("probability") double probability);
}
