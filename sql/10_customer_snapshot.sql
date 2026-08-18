-- =====================================================================
-- 10 - Müşteri varlık anlık görüntüleri (müşteri anasayfasındaki trend çizgisi)
--   Her spot/transfer sonrası müşterinin toplam TL karşılığı buraya yazılır.
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS customer_snapshot (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    customer_no INT NOT NULL,
    total_try   DECIMAL(20,2) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Çizgi hemen görünsün diye Ahmet (10000001) için son günlere örnek noktalar
INSERT INTO customer_snapshot (customer_no, total_try, created_at)
SELECT 10000001, ROUND(t.tot * f.factor, 2), NOW() - INTERVAL f.d DAY
FROM (
        SELECT COALESCE(SUM(a.balance * CASE WHEN a.currency = 'TRY' THEN 1
                                             ELSE COALESCE(cr.buy_rate, 1) END), 0) AS tot
        FROM account a
        JOIN customer c ON a.customer_id = c.customer_id AND c.customer_no = 10000001
        LEFT JOIN currency_rate cr ON cr.currency = a.currency AND cr.status = 1
     ) t
JOIN (
        SELECT 6 AS d, 0.95 AS factor
        UNION ALL SELECT 5, 0.97
        UNION ALL SELECT 4, 1.02
        UNION ALL SELECT 3, 0.99
        UNION ALL SELECT 2, 1.03
        UNION ALL SELECT 1, 0.98
        UNION ALL SELECT 0, 1.00
     ) f
WHERE NOT EXISTS (SELECT 1 FROM customer_snapshot WHERE customer_no = 10000001);
