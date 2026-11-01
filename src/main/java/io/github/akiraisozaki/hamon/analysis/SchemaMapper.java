package io.github.akiraisozaki.hamon.analysis;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/** 接続中のスキーマにある表と列を Oracle の辞書ビューから読む。SQL は SchemaMapper.xml */
@Mapper
interface SchemaMapper {

	List<TableColumn> selectColumns();
}
