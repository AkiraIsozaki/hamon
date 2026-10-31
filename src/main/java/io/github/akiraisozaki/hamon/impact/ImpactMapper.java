package io.github.akiraisozaki.hamon.impact;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 依存グラフを Oracle で逆向きに辿る。SQL は同じパッケージの ImpactMapper.xml にある。 */
@Mapper
interface ImpactMapper {

	/** 変更点から影響が広がる先を、確からしさの高い順に返す。同じノードに複数の経路で届く場合は最も確からしい経路だけ。 */
	List<ImpactRow> traceUpstream(@Param("target") String target, @Param("minProbability") double minProbability);
}
