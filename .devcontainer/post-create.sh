#!/usr/bin/env bash
# コンテナ作成後に 1 回だけ、vscode ユーザーで実行される（devcontainer.json の postCreateCommand）
set -euo pipefail

echo "[post-create] 1/3 ボリュームの所有者を修正"
# 名前付きボリュームは root 所有で作られることがあるため
sudo chown -R vscode:vscode /home/vscode/.claude /home/vscode/.gradle

echo "[post-create] 2/3 Claude Code（ネイティブ版）をインストール"
# 以後は自動更新される。~/.local/bin に入る（PATH は devcontainer.json の remoteEnv で設定済み）
curl -fsSL https://claude.ai/install.sh | bash

echo "[post-create] 3/3 uv をインストール"
# Jev を Python で試し打ちするとき用。Python 本体も uv が必要に応じて取ってくる
# （例: uv init --python 3.14 で試し打ち用プロジェクトを作り、uv add で TypeSafe の SDK を追加）
# PATH は remoteEnv で通しているので、シェル設定ファイルは書き換えさせない
curl -LsSf https://astral.sh/uv/install.sh | env UV_NO_MODIFY_PATH=1 sh

echo "[post-create] 完了"
java -version 2>&1 | head -1
node --version
echo "Oracle は初回起動に数分かかります。準備ができたら jdbc:oracle:thin:@db:1521/FREEPDB1 (app/app) で接続できます"
