# ADR-0002: Java ソースの解析は JavaParser（シンボル解決つき）

日付: 2026-10-31 / 状態: 採用

## 文脈

Phase 3 でこのリポジトリ自身を解析してグラフにする。候補は JavaParser / Spoon / tree-sitter（docs/IDEAS.md 未決事項）。
hamon の肝は「確実な辺」と「あいまいな辺」を分けることなので、呼び出し先の型解決ができるかどうかが決め手。

## 決定

- **JavaParser + javaparser-symbol-solver-core** を使う（`JavaSourceAnalysis`）
- 型解決できた呼び出しは確からしさ 1 の辺、できなかった呼び出しは同名メソッド全部へのあいまいな辺（当面 0.5、Phase 5 で Jev が付け直す）
- 解析対象の外（JDK・ライブラリ）への呼び出しは辺にしない
- 解析対象は `hamon.analysis.source-roots`（`@ConfigurationProperties`）で与え、`POST /api/analysis` で保存済みグラフを作り直す

## 理由

- JavaParser は構文木とシンボル解決が一体で、Java 21 までの構文に追従している。Spring/MyBatis を使う SI のコードを読める
- Spoon はモデルが重く、tree-sitter は型解決がないので「確実な辺」が作れない
- 解析対象の依存ライブラリがクラスパスにあれば `ReflectionTypeSolver(false)` で解決でき、無ければあいまいな辺に落ちる。どちらでも壊れない

## 影響

- ノード名は `クラス#メソッド` なのでオーバーロードは 1 ノードにまとまる（`shortcut:` コメント）
- コンストラクタ呼び出し、フィールド初期化子からの呼び出し、ラムダの外の式は辺にしていない
- 他リポジトリ（Phase 4 の SI サンプル）を解析するときは、そのリポジトリのライブラリをクラスパスに積まない限りライブラリ呼び出しがあいまいな辺になる
