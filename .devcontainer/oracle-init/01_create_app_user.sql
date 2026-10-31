-- 初回の DB 作成直後に 1 回だけ実行される（SYS としてコンテナ DB のルートに接続された状態）
-- アプリ用ユーザー app を PDB（FREEPDB1）に作る。
-- ユーザー名・パスワードは compose.yaml の SPRING_DATASOURCE_* と揃えること（ローカル開発専用）

ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER app IDENTIFIED BY "app"
  DEFAULT TABLESPACE users
  QUOTA UNLIMITED ON users;

-- 23ai 以降で使える開発者向けロール（表・ビュー・プロシージャ等の作成権限一式）
GRANT DB_DEVELOPER_ROLE TO app;

-- グラフを Oracle の SQL Property Graph（GRAPH_TABLE）で扱う場合に必要
GRANT CREATE PROPERTY GRAPH TO app;
