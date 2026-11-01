# ADR-0005: あいまいな辺の確からしさは Jev に Noul で聞き、閾値はコードに置かない

日付: 2026-11-01 / 状態: 採用

## 文脈

Phase 5。静的解析で断定できないあいまいな辺が 2 種類ある：型解決できないメソッド呼び出し（同名メソッドが候補）と、複数の参照表にある列名（各表の列が候補）。
これまで一律 0.5 だった確からしさを、Jev（TypeSafe System One API）に判定させる。

## 決定

1. **ポートは `DependencyJudge`**（`judgment` パッケージ）。入力は `Ambiguity`（判定材料の state、候補ごとに同じ質問文、候補の一覧）、出力は候補ノード名 → 確率。本番実装は `JevDependencyJudge`、テストは固定値を返す `FixedDependencyJudge`
2. **質問は Noul**（yes/no の確率）を候補ごとに 1 つ、state を共有して 1 リクエストに束ねる。質問 ID はモデルに送られないので `candidate_0, 1, …` の番号にし、答えを同じ番号で候補に戻す
3. **質問の文言と state の形は `Ambiguity` の 2 つのファクトリに集約**する（`methodCall` / `columnReference`）。文言は英語（Jev は英語が主で CJK は精度が落ちると公式が明記）
   - メソッド呼び出し：state = 呼び出し元ノード名・呼び出し元メソッドのソース・呼び出し式。候補 = メソッドのノード名と宣言
   - 列参照：state = SQL 文のノード名・SQL。候補 = 表.列、表名、その表の全列
4. **Jev の答えをそのまま辺の確からしさにする**。閾値は置かない。漏れの許容度は影響調査時の `minProbability` で利用者が決める（IDEAS.md の設計方針）
5. **モデル ID はピン留め**（`hamon.jev.model: jev-1.14.0`）。答えた版と各候補の確率を INFO ログに残す
6. **失敗は答えにしない**。Jev を呼べなければ例外で上げ、解析は失敗する。429 / 5xx / 接続失敗は `@Retryable`（Spring Framework 7 の `org.springframework.resilience`）で間隔を倍にしながら 3 回まで再試行する
7. HTTP は spring-web の `RestClient` を直接使う（Boot 4.1.2 に RestClient のスターターは無く、SDK も入れない）

## 理由

- 「判断は Jev、決定はコード」の原則に従い、Jev には「この呼び出しはこのメソッドか」「この SQL はこの表の列を読み書きしているか」という観察可能な 1 判断だけを聞く
- state を共有する質問は 1 リクエストに束ねるのが安くて速い
- このリポジトリでの最初の判定（`ImpactMapper#traceUpstream` の `id`）は NODE.ID 0.93 / EDGE.ID 0.12 で、SQL の実態（node.id で結合、edge.id は未使用）と一致した
- 外部 API は管理外の依存なので、テストでは本物を呼ばず、ポートを固定値の実装に差し替える。Spring のテストでは `@TestConfiguration` + `@Primary` の Bean で差し替える

## 影響

- 解析のたびに Jev を呼ぶ。このリポジトリではあいまいな辺が 1 組なのでリクエストは 1 回だが、対象が大きいと呼び出し元ごと・列名ごとに増える。並列化・キャッシュは必要になってから
- 環境変数 `TYPESAFE_API_KEY` が無いと起動時に `JevProperties` の検証で失敗する（意図的。.env を用意する）
- Jev の答えが 0.5 付近なら「state では決まらない」の意味。state の改善（呼び出し元のクラス全体、別名の解決など）が次の手で、文言をいじって数字を合わせることはしない
- モデルを `jev-1.14.0` から上げるときは、判定ログで答えの変化を見てから `hamon.jev.model` を変える
