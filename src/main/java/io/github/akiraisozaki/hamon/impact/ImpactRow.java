package io.github.akiraisozaki.hamon.impact;

import java.util.List;

import io.github.akiraisozaki.hamon.api.model.ImpactItem;
import io.github.akiraisozaki.hamon.api.model.NodeKind;

/** ImpactMapper.traceUpstream の 1 行。DB との境界の形なので、API へは toItem で変換する。 */
record ImpactRow(String node, io.github.akiraisozaki.hamon.graph.NodeKind kind, double probability, String nodePath) {

	/** ImpactMapper.xml の CHR(10) と揃える */
	private static final String PATH_SEPARATOR = "\n";

	ImpactItem toItem() {
		return new ImpactItem(node, NodeKind.fromValue(kind.name()), probability, List.of(nodePath.split(PATH_SEPARATOR)));
	}
}
