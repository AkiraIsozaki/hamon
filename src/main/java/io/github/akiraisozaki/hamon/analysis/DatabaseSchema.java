package io.github.akiraisozaki.hamon.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.akiraisozaki.hamon.graph.DependencyEdge;
import io.github.akiraisozaki.hamon.graph.NodeKind;
import io.github.akiraisozaki.hamon.judgment.Ambiguity;
import io.github.akiraisozaki.hamon.judgment.DependencyJudge;

/**
 * 解析対象システムの DB にある表と列。表名・列名は Oracle の辞書どおり大文字。
 * SQL 文がどの表・列を使っているかは、SQL パーサではなく識別子の照合で決める（ADR-0004）。
 * shortcut: 引用符付き識別子と ${} で組み立てる表名は見ない, 対象システムで使われていたら SQL パーサに替える
 * shortcut: SELECT * は列の辺にならない, 列単位の漏れが問題になったら表の全列へ張る
 */
public record DatabaseSchema(Map<String, Set<String>> columnsByTable) {

	/** #{param} / ${param} は列名と同じ名前のことが多いので、照合の前に取り除く */
	private static final Pattern BIND_PARAMETER = Pattern.compile("[#$]\\{[^}]*}");
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]*");

	public DatabaseSchema {
		columnsByTable = columnsByTable.entrySet().stream()
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
	}

	static DatabaseSchema of(List<TableColumn> columns) {
		Map<String, Set<String>> byTable = new LinkedHashMap<>();
		columns.forEach(column -> byTable.computeIfAbsent(column.tableName(), table -> new LinkedHashSet<>()).add(column.columnName()));
		return new DatabaseSchema(byTable);
	}

	/** 表は TABLE ノード、列は「表.列」の COLUMN ノード */
	Map<String, NodeKind> nodes() {
		Map<String, NodeKind> nodes = new LinkedHashMap<>();
		columnsByTable.forEach((table, columns) -> {
			nodes.put(table, NodeKind.TABLE);
			columns.forEach(column -> nodes.put(table + "." + column, NodeKind.COLUMN));
		});
		return nodes;
	}

	/**
	 * sql が使っている表と列への、dependent からの辺。
	 * 表名が出てくれば確実な辺。列名は、それを持つ表が参照表の中に 1 つなら確実、複数ならどの表の列かを judge（Jev）に聞く
	 */
	List<DependencyEdge> dependenciesOf(String dependent, String sql, DependencyJudge judge) {
		Set<String> identifiers = identifiersIn(sql);
		List<String> tables = columnsByTable.keySet().stream().filter(identifiers::contains).toList();
		List<DependencyEdge> edges = new ArrayList<>();
		tables.forEach(table -> edges.add(DependencyEdge.certain(dependent, table)));
		for (String identifier : identifiers) {
			Map<String, Set<String>> owners = new LinkedHashMap<>();
			tables.stream().filter(table -> columnsByTable.get(table).contains(identifier)).forEach(table -> owners.put(table, columnsByTable.get(table)));
			if (owners.size() == 1) {
				edges.add(DependencyEdge.certain(dependent, owners.keySet().iterator().next() + "." + identifier));
			} else if (owners.size() > 1) {
				judge.judge(Ambiguity.columnReference(dependent, sql, identifier, owners))
						.forEach((column, probability) -> edges.add(new DependencyEdge(dependent, column, probability)));
			}
		}
		return edges;
	}

	private static Set<String> identifiersIn(String sql) {
		Matcher matcher = IDENTIFIER.matcher(BIND_PARAMETER.matcher(sql).replaceAll(" "));
		Set<String> identifiers = new LinkedHashSet<>();
		while (matcher.find()) {
			identifiers.add(matcher.group().toUpperCase());
		}
		return identifiers;
	}
}
