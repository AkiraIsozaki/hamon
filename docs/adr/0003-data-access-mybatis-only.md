# ADR-0003: データアクセスは MyBatis に決め、Spring Data JDBC 版を外す

日付: 2026-11-01 / 状態: 採用（ADR-0001 の比較を完了し、その 2 項を置き換える）

## 文脈

ADR-0001 に従い、Phase 2b で同じ API の裏側を MyBatis と Spring Data JDBC の両方で実装し、プロファイルで切り替えて比較した（Phase 2c）。
Phase 4 以降は書き込みも読み取りも増えるので、両方を維持すると DB の変更がすべて 2 回になる。

## 決定

- **MyBatis**（XML マッパー）だけを残す
- Spring Data JDBC の依存・`@Profile` による切り替え・`ImpactGraph` / `GraphStore` のインターフェース層を削除する。実装が 1 つしかない抽象は持たない
  - `ImpactController` は `ImpactMapper` を、`AnalysisController` は具象クラス `GraphStore` を直接受け取る
  - `Node` record から Spring Data の注釈を外す

## 理由

- 肝心の再帰 SQL（`WITH … CYCLE`）は両方とも生 SQL で、Spring Data JDBC でも `@Query` に同じ文を書くだけだった。Repository の自動実装が効いたのは insert / delete の 4 文のみ
- Spring Data JDBC は Oracle の引用符付き識別子のせいで `NamingStrategy` の回避コードが必要になり、`Node` / `Edge` の注釈が MyBatis 側にも漏れていた
- MyBatis XML は Phase 4 の解析対象そのもの。自分の `GraphMapper.xml` / `ImpactMapper.xml` が試験材料になる
- Spring Data JDBC で学びたかったこと（Repository の自動実装、`@Profile`、`@Configuration` + `@Bean`、`NamingStrategy`）は Phase 2b で体験済み

## 影響

- `--spring.profiles.active=datajdbc` は無くなる。プロファイルの既定値も不要になり application.yml から消した
- 落語ノート（docs/rakugo）の抜粋は Phase 2b/3 時点のもので、Data JDBC 版と `@Profile` の説明は歴史として残す。コードと食い違ったらコードが正
- Phase 5 で確からしさを書き戻すときも MyBatis の update 文を足すだけでよい
