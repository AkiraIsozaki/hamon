-- V2 のサンプルグラフは解析結果（POST /api/analysis）で置き換わるので消す。
-- サンプルはテスト用に src/test/resources/sample-graph.sql へ移した
DELETE FROM edge;
DELETE FROM node;
