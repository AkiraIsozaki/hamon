package io.github.akiraisozaki.hamon.graph;

import io.github.akiraisozaki.hamon.api.model.NodeSummary;

/** node 表の 1 行。GLOSSARY: ノード */
public record Node(Long id, String name, NodeKind kind) {

	NodeSummary toSummary() {
		return new NodeSummary(name, io.github.akiraisozaki.hamon.api.model.NodeKind.fromValue(kind.name()));
	}
}
