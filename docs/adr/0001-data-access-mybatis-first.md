# ADR-0001: データアクセスはまず MyBatis、その後 Spring Data JDBC と比較する

日付: 2026-10-31 / 状態: 採用

## 文脈

Phase 2 でノード・辺を Oracle に保存し、再帰 SQL で逆向きに辿る必要がある。候補は MyBatis / Spring Data JDBC / JdbcClient（docs/IDEAS.md 未決事項）。
このプロジェクトは Spring の学習も目的で、Phase 4 では MyBatis XML を解析対象にする予定がある。

## 決定

1. Phase 2 は **MyBatis**（`mybatis-spring-boot-starter` 4.1.x、XML マッパー）で実装する
2. Phase 2 完了後、同じ API の裏側を **Spring Data JDBC** でも実装し、Spring プロファイルで切り替えて比較する（学習目的）
   - `ImpactGraph` インターフェースが共通の seam。`ImpactGraphConfiguration` が `@Profile("mybatis")` / `@Profile("datajdbc")` でどちらを Bean にするか決める
   - 既定は `mybatis`（application.yml の `spring.profiles.default`）。`--spring.profiles.active=datajdbc` で切り替え
   - 統合テスト `ImpactApiTest` を `@ActiveProfiles("datajdbc")` のサブクラスで両実装に流す
3. Controller は `ImpactGraph` を呼ぶだけにし、DB の都合を漏らさない
4. マイグレーションは **Flyway**（`src/main/resources/db/migration`）

## 理由

- MyBatis は SI 現場でなじみが深く、自分のリポジトリが Phase 4 の解析対象（MyBatis XML）のサンプルにもなる
- 再帰 SQL は生 SQL で書く方が素直で、どの方式でも結局は SQL を書く
- Spring Data JDBC は Spring 標準寄りで、学習目的に合う。比較すると両者の違いが体感できる

## 影響

- 依存関係に MyBatis と Spring Data JDBC の両方が入る。比較が済んで片方に決めたら、もう片方と `ImpactGraphConfiguration` を消す（決定は別 ADR）
- SQL の中に業務知識（確からしさの積、最良経路の選択）が入るので、SQL は実 DB を使う統合テストで守る
