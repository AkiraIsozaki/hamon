# ADR-0006: Claude Code には Spring AI の MCP サーバーでツールを公開し、レポートは Claude Code 自身が書く

日付: 2026-11-01 / 状態: 採用

## 文脈

Phase 6「Claude でレポート / MCP 化」。ロードマップでは (a) 影響候補の上位を Claude が精読してレポートにする、(b) Claude Code から MCP 経由で問い合わせる、の 2 つを挙げていた。
サーバー側から Claude API を呼ぶには別の API キーと 1 回あたり数円〜数十円の費用がかかる。

## 決定

1. **MCP 化だけを行い、レポート生成の API は作らない**。Claude Code が `survey_impact` ツールで候補と経路を得て、ソースを読み、その場でレポートを書く。サーバーは判断材料を返すだけ
2. **Spring AI 2.0.2 の MCP サーバースターター**（`spring-ai-starter-mcp-server-webmvc`、Streamable HTTP）を同じアプリに入れる。`@McpTool` を付けた Bean のメソッドがツールになる
   - ツールは `survey_impact(target, minProbability)` と `analyze_sources()` の 2 つ。REST API と同じ操作を同じ裏側で公開する
   - REST と MCP が共通で呼ぶ入口として `ImpactSurvey` と `Analysis` を置き、Controller とツールはどちらも配線だけにする
   - 入口は `POST /mcp`。認証は無いので localhost 限定（`.mcp.json` も localhost）
3. **接続設定はリポジトリ直下の `.mcp.json`**（プロジェクトスコープ、コミットする）。Claude Code をこのディレクトリで起動すると hamon サーバーが見える
4. MCP の統合テストは本物のポートで起動し、MCP Java SDK のクライアント（Claude Code と同じ Streamable HTTP）で初期化 → ツール一覧 → 呼び出しを通す。DB を変えないツール呼び出しだけにする

## 理由

- 「上位だけ Claude が精読」は、精読する主体が Claude Code なら API 呼び出しもキーも要らない。サーバーに置くのは、画面にレポートを出したくなったときでよい
- Spring AI は Boot 4.1.2 と同じ Boot 版に依存しており（2.0.2）、注釈を付けるだけで JSON Schema の生成・JSON-RPC・セッション管理を引き受ける。MCP Java SDK を直接使うより配線が少ない
- REST と MCP で同じコードを呼ぶので、契約（openapi.yaml）と MCP ツールの振る舞いがずれない

## 影響

- 依存に Spring AI（と MCP Java SDK 2.1.0）が入る。Spring AI の他の機能（ChatClient など）は使っていない
- `/mcp` は認証なし。外に公開するなら Spring Security か MCP Security を足す
- ツールの説明文は Claude が読む。変更点の名前の形（`クラス#メソッド`、`TABLE`、`TABLE.COLUMN`）を説明に入れてある
- `analyze_sources` は解析のたびに Jev を呼ぶ。Claude Code からの呼び出しでも同じ
