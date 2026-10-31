package io.github.akiraisozaki.hamon.impact;

/** ノードの種類。V1__graph_tables.sql の CHECK 制約と揃える */
enum NodeKind {
	METHOD, CLASS, FILE, TABLE, COLUMN, CONFIG
}
