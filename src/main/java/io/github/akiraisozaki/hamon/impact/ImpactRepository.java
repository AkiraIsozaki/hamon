package io.github.akiraisozaki.hamon.impact;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC 版のグラフ問い合わせ。SQL は ImpactMapper.xml と同じもの（比較のため意図的に揃えている）。
 * Repository を継承するだけで実装は Spring Data が生成する。CRUD が要るなら CrudRepository に変える。
 */
interface ImpactRepository extends Repository<Node, Long> {

	@Query("""
			WITH ripple (node_id, probability, node_path) AS (
			    SELECT e.dependent_id,
			           e.probability,
			           changed.name || CHR(10) || dependent.name
			      FROM node changed
			      JOIN edge e         ON e.dependency_id = changed.id
			      JOIN node dependent ON dependent.id = e.dependent_id
			     WHERE changed.name = :target
			       AND e.probability >= :minProbability
			    UNION ALL
			    SELECT e.dependent_id,
			           r.probability * e.probability,
			           r.node_path || CHR(10) || dependent.name
			      FROM ripple r
			      JOIN edge e         ON e.dependency_id = r.node_id
			      JOIN node dependent ON dependent.id = e.dependent_id
			     WHERE r.probability * e.probability >= :minProbability
			)
			CYCLE node_id SET is_cycle TO 'Y' DEFAULT 'N'
			SELECT n.name AS node, best.probability, best.node_path
			  FROM (SELECT r.*,
			               ROW_NUMBER() OVER (PARTITION BY r.node_id ORDER BY r.probability DESC) AS path_rank
			          FROM ripple r
			         WHERE r.is_cycle = 'N') best
			  JOIN node n ON n.id = best.node_id
			 WHERE best.path_rank = 1
			 ORDER BY best.probability DESC, n.name
			""")
	List<ImpactRow> traceUpstream(String target, double minProbability);
}
