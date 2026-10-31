package io.github.akiraisozaki.hamon.graph;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** edge 表の 1 行（Spring Data JDBC）。GLOSSARY: 辺。dependent が dependency を使っている */
@Table("EDGE")
public record Edge(@Id Long id, Long dependentId, Long dependencyId, double probability) {
}
