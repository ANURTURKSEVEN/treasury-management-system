-- =====================================================================
-- 08 - Hazine değeri anlık görüntüleri (trend grafiği için)
--   Her spot işlemde bankanın toplam TL karşılığı buraya yazılır;
--   Banka Varlıkları ekranı bunu çizgi grafik olarak gösterir.
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS treasury_snapshot (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    total_try  DECIMAL(20,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Çizgi hemen görünsün diye son 6 güne birkaç örnek nokta ekle
-- (mevcut gerçek toplamın hafif oynatılmış hali). Yalnızca tablo boşsa.
INSERT INTO treasury_snapshot (total_try, created_at)
SELECT ROUND(t.tot * f.factor, 2), NOW() - INTERVAL f.d DAY
FROM (
        SELECT SUM(a.balance * CASE WHEN a.currency = 'TRY' THEN 1
                                    ELSE COALESCE(cr.buy_rate, 1) END) AS tot
        FROM account a
        JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = 99999999
        LEFT JOIN currency_rate cr ON cr.currency = a.currency AND cr.status = 1
     ) t
JOIN (
        SELECT 6 AS d, 0.970 AS factor
        UNION ALL SELECT 5, 0.985
        UNION ALL SELECT 4, 0.992
        UNION ALL SELECT 3, 1.004
        UNION ALL SELECT 2, 0.998
        UNION ALL SELECT 1, 1.010
        UNION ALL SELECT 0, 1.000
     ) f
WHERE NOT EXISTS (SELECT 1 FROM treasury_snapshot);
