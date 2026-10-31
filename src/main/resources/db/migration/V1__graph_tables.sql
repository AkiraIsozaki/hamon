-- 確率つき依存グラフ。用語は GLOSSARY.md を参照

CREATE TABLE node (
    id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(500) NOT NULL,
    kind VARCHAR2(20)  NOT NULL CHECK (kind IN ('METHOD', 'CLASS', 'FILE', 'TABLE', 'COLUMN', 'CONFIG')),
    CONSTRAINT node_name_unique UNIQUE (name)
);
COMMENT ON TABLE  node      IS 'グラフのノード。コード以外（テーブル・カラム・設定キー）も含む';
COMMENT ON COLUMN node.name IS '一意な名前。例: com.example.order.OrderService#cancel, ORDERS.STATUS, batch.order-cancel.cron';

CREATE TABLE edge (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dependent_id  NUMBER NOT NULL REFERENCES node (id),
    dependency_id NUMBER NOT NULL REFERENCES node (id),
    probability   NUMBER(5, 4) NOT NULL CHECK (probability BETWEEN 0 AND 1),
    CONSTRAINT edge_unique UNIQUE (dependent_id, dependency_id)
);
COMMENT ON TABLE  edge               IS '依存の辺。dependent が dependency を使っている。影響は dependency → dependent の向きに広がる';
COMMENT ON COLUMN edge.dependent_id  IS '依存する側（呼び出し元、参照元）。dependency が変わると影響を受ける';
COMMENT ON COLUMN edge.dependency_id IS '依存される側（呼び出し先、参照先）';
COMMENT ON COLUMN edge.probability   IS 'この依存が本当に存在する確からしさ。型解決できた呼び出しは 1、あいまいな辺は Jev が付ける';

CREATE INDEX edge_dependency_idx ON edge (dependency_id);
