# ADR-0001: データアクセスはまず MyBatis、その後 Spring Data JDBC と比較する

日付: 2026-10-31 / 状態: 採用

## 文脈

Phase 2 でノード・辺を Oracle に保存し、再帰 SQL で逆向きに辿る必要がある。候補は MyBatis / Spring Data JDBC / JdbcClient（docs/IDEAS.md 未決事項）。
このプロジェクトは Spring の学習も目的で、Phase 4 では MyBatis XML を解析対象にする予定がある。

## 決定

1. Phase 2 は **MyBatis**（`mybatis-spring-boot-starter` 4.1.x、XML マッパー）で実装する
2. Phase 2 完了後、同じ API の裏側を **Spring Data JDBC** で実装し直して比較する（学習目的）
3. DB アクセスは `impact` パッケージの Mapper 1 つに閉じ込め、Controller はそれを呼ぶだけにする。差し替え時に触る範囲を 1 クラスに保つ
4. マイグレーションは **Flyway**（`src/main/resources/db/migration`）

## 理由

- MyBatis は SI 現場でなじみが深く、自分のリポジトリが Phase 4 の解析対象（MyBatis XML）のサンプルにもなる
- 再帰 SQL は生 SQL で書く方が素直で、どの方式でも結局は SQL を書く
- Spring Data JDBC は Spring 標準寄りで、学習目的に合う。比較すると両者の違いが体感できる

## 影響

- 当面、依存関係に MyBatis が 1 つ増える。Spring Data JDBC を試す際は一時的に両方が入る
- SQL の中に業務知識（確からしさの積、最良経路の選択）が入るので、SQL は実 DB を使う統合テストで守る
