package io.github.akiraisozaki.hamon.analysis;

/** SchemaMapper.selectColumns の 1 行（Oracle の USER_TAB_COLUMNS）。DB との境界の形 */
record TableColumn(String tableName, String columnName) {
}
