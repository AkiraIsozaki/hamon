package io.github.akiraisozaki.hamon.graph;

/** ノードの種類。V1__graph_tables.sql の CHECK 制約と揃える */
public enum NodeKind {
	METHOD, CLASS, FILE, TABLE, COLUMN, CONFIG
}
