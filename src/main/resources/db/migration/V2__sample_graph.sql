-- 動作確認用のサンプルグラフ（Phase 3 で実際の解析結果に置き換わるまでの仮データ）
-- 注文キャンセル周りの小さなシステムを想定している

INSERT INTO node (name, kind) VALUES ('com.example.order.OrderService#cancel',        'METHOD');
INSERT INTO node (name, kind) VALUES ('com.example.order.OrderController#cancel',     'METHOD');
INSERT INTO node (name, kind) VALUES ('web/order/cancel.html',                        'FILE');
INSERT INTO node (name, kind) VALUES ('com.example.order.OrderFacade#cancelWithRefund','METHOD');
INSERT INTO node (name, kind) VALUES ('com.example.api.OrderCancelEndpoint#post',     'METHOD');
INSERT INTO node (name, kind) VALUES ('com.example.batch.OrderCancelJob#run',         'METHOD');
INSERT INTO node (name, kind) VALUES ('batch.order-cancel.cron',                      'CONFIG');
INSERT INTO node (name, kind) VALUES ('com.example.common.MethodInvoker#invoke',      'METHOD');
INSERT INTO node (name, kind) VALUES ('com.example.report.DailyReportJob#run',        'METHOD');
INSERT INTO node (name, kind) VALUES ('ORDERS.STATUS',                                'COLUMN');

-- dependent（使う側）→ dependency（使われる側）, 確からしさ
INSERT INTO edge (dependent_id, dependency_id, probability)
SELECT d.id, y.id, p.probability
FROM (
    SELECT 'com.example.order.OrderController#cancel'      AS dependent, 'com.example.order.OrderService#cancel'         AS dependency, 1.00 AS probability FROM dual UNION ALL
    SELECT 'web/order/cancel.html',                           'com.example.order.OrderController#cancel',       0.80 FROM dual UNION ALL
    SELECT 'com.example.order.OrderFacade#cancelWithRefund',  'com.example.order.OrderService#cancel',          0.95 FROM dual UNION ALL
    SELECT 'com.example.api.OrderCancelEndpoint#post',        'com.example.order.OrderFacade#cancelWithRefund', 0.90 FROM dual UNION ALL
    SELECT 'com.example.batch.OrderCancelJob#run',            'com.example.order.OrderService#cancel',          0.72 FROM dual UNION ALL
    SELECT 'com.example.batch.OrderCancelJob#run',            'batch.order-cancel.cron',                        1.00 FROM dual UNION ALL
    -- リフレクションでメソッド名の文字列から呼ぶ。あいまいな辺
    SELECT 'com.example.common.MethodInvoker#invoke',         'com.example.order.OrderService#cancel',          0.12 FROM dual UNION ALL
    SELECT 'com.example.report.DailyReportJob#run',           'com.example.common.MethodInvoker#invoke',        0.35 FROM dual UNION ALL
    SELECT 'com.example.order.OrderService#cancel',           'ORDERS.STATUS',                                  1.00 FROM dual UNION ALL
    SELECT 'com.example.report.DailyReportJob#run',           'ORDERS.STATUS',                                  0.90 FROM dual
) p
JOIN node d ON d.name = p.dependent
JOIN node y ON y.name = p.dependency;
