package io.github.akiraisozaki.hamon.impact;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** node 表の 1 行（Spring Data JDBC の集約ルート）。GLOSSARY: ノード */
@Table("node")
record Node(@Id Long id, String name, NodeKind kind) {
}
