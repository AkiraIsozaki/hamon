package io.github.akiraisozaki.hamon.graph;

/** node 表の 1 行。GLOSSARY: ノード */
public record Node(Long id, String name, NodeKind kind) {
}
