# CLAUDE.md

## このリポジトリ

Java + Oracle のシステムを対象にした「確率つき影響調査ツール」hamon（波紋）。
**作業を始める前に `docs/IDEAS.md` を読むこと。** 目的・全体像・ロードマップ・未決事項がまとまっている。

## 開発者について

- Spring と API セントリックな開発はこれが初めて（経験は 2000 年頃の自社フレームワークのみ）
- Spring の新しい概念（DI、Bean、自動構成、プロファイルなど）が初めて出てくるときは、2〜3 行で説明を添える。旧来の作り方との違いがあれば一言触れる
- 応答は日本語で

## 進め方のルール

- `docs/IDEAS.md` のロードマップに沿って、Phase 単位で小さく進める。Phase を終えたら IDEAS.md の「現在地」を更新する
- 独自フレームワークは作らない。まず Spring Boot の標準的なやり方に乗る
- API はスキーマファースト：先に `openapi.yaml` を変更 → コード生成 → 実装。生成されたコードは手で編集しない
- 未決事項に関わる選択をするときは、勝手に決めずに選択肢と理由を示して確認する
- 作業の区切りごとに都度コミットする（ユーザーに聞かずに進めてよい）。コミットメッセージは日本語

## 環境

- ホストは Linux Mint + Docker Engine。Dev Container（`app` + Oracle の `db`）を VS Code の「コンテナーで再度開く」で起動
- DB 接続は環境変数 `SPRING_DATASOURCE_*` で設定済み（`jdbc:oracle:thin:@db:1521/FREEPDB1`、ユーザー `app` / `app`）。application.yml に接続情報を書かない
- Oracle は初回起動に数分かかる
- Jev の API キーはリポジトリ直下の `.env`（git 管理外）に書かれ、コンテナの環境変数 `TYPESAFE_API_KEY` として渡っている
  - コードからは環境変数で参照する（Spring なら `application.yml` に `${TYPESAFE_API_KEY}` と書く）
  - `.env` を読んだり、キーの値を表示・ログ出力・コミットしたりしない
  - 新しい秘密情報が必要になったら、`.env.example` にキー名だけ追加する
- Jev のモデル ID はピン留めする
- ビルドは Gradle Wrapper（`./gradlew`）。Gradle 本体はコンテナに入れない

## よく使うコマンド

バックエンド（リポジトリ直下）
- `./gradlew bootRun` … 起動（http://localhost:8080）。データアクセスは既定で MyBatis。Spring Data JDBC 版は `./gradlew bootRun --args='--spring.profiles.active=datajdbc'`（ADR-0001）
- `./gradlew test` … テスト（`openapi.yaml` からのコード生成も走る。生成先は `build/generated/openapi`。統合テストは Oracle が起きている必要がある）
- DB の変更は `src/main/resources/db/migration/V{n}__{説明}.sql` を足す（Flyway が起動時に適用。適用済みの V ファイルは編集しない）
- `curl -X POST localhost:8080/api/analysis` … `hamon.analysis.source-roots`（既定: このリポジトリの `src/main/java`）を解析してグラフを作り直す
- `curl 'localhost:8080/api/impact?target=io.github.akiraisozaki.hamon.impact.ImpactRow%23toItem'` … 影響調査の疎通確認
- `curl localhost:8080/actuator/health` … DB を含むヘルス（`components.db.status` が `UP` なら Oracle に繋がっている）

フロントエンド（`frontend/`）
- `npm run dev` … 起動（http://localhost:5173。`/api` は 8080 へプロキシ）
- `npm run api` … `openapi.yaml` から `src/api/schema.d.ts` を再生成（yaml を変えたら必ず実行し、生成物もコミットする）
- `npm test` … テスト（vitest）

## Agent skills

### Issue tracker

課題は GitHub Issues（AkiraIsozaki/hamon）で管理する。`gh` CLI を使う。See `docs/agents/issue-tracker.md`.

### Triage labels

既定の 5 ラベル（needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix）をそのまま使う。See `docs/agents/triage-labels.md`.

### Domain docs

単一コンテキスト：ルートの `GLOSSARY.md` と `docs/adr/`。See `docs/agents/domain.md`.
