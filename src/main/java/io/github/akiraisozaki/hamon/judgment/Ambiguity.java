package io.github.akiraisozaki.hamon.judgment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 静的解析で断定できなかった依存。state が判定の材料（Jev の state）、question が候補ごとに同じ質問、
 * candidates が「このどれかを使っているはず」の候補。質問の文言はすべてこのクラスに置く（監査・調整が 1 か所で済む）。
 * Jev は英語が主で CJK は精度が落ちるため、文言は英語
 */
public record Ambiguity(String dependent, Map<String, String> state, String question, List<Candidate> candidates) {

	/** 候補 1 つ。dependency はノード名、fields は質問に添える説明 */
	public record Candidate(String dependency, Map<String, String> fields) {
		public Candidate {
			fields = Map.copyOf(fields);
		}
	}

	public Ambiguity {
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("候補のないあいまいな辺: " + dependent);
		}
		state = Map.copyOf(state);
		candidates = List.copyOf(candidates);
	}

	/** 型解決できなかったメソッド呼び出し。候補は同名メソッドで、declarationsByMethod はノード名 → 宣言（順序を保つ Map を渡す） */
	public static Ambiguity methodCall(String caller, String callerSource, String call, Map<String, String> declarationsByMethod) {
		List<Candidate> candidates = new ArrayList<>();
		declarationsByMethod.forEach((method, declaration) ->
				candidates.add(new Candidate(method, Map.of("method", method, "declaration", declaration))));
		return new Ambiguity(caller,
				Map.of("caller", caller, "caller_source", callerSource, "call", call),
				"Is this the method that `call` in `caller_source` invokes?",
				candidates);
	}

	/** 複数の参照表にある列名。候補は 表.列 で、columnsByTable は候補の表 → その表の全列 */
	public static Ambiguity columnReference(String statement, String sql, String column, Map<String, Set<String>> columnsByTable) {
		List<Candidate> candidates = new ArrayList<>();
		columnsByTable.forEach((table, columns) ->
				candidates.add(new Candidate(table + "." + column, Map.of(
						"column", table + "." + column,
						"table", table,
						"columns_of_table", String.join(", ", columns)))));
		return new Ambiguity(statement,
				Map.of("statement", statement, "sql", sql),
				"Does `sql` read or write this column?",
				candidates);
	}
}
