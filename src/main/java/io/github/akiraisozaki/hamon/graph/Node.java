package io.github.akiraisozaki.hamon.graph;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** node 表の 1 行（Spring Data JDBC の集約ルート）。GLOSSARY: ノード */
@Table("NODE") // Oracle は引用符付き識別子を区別するので大文字（DataJdbcConfiguration も参照）
public record Node(@Id Long id, String name, NodeKind kind) {
}
