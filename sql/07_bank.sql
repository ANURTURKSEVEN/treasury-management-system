-- =====================================================================
-- 07 - Banka (Hazine) hesabı
--   Spot al/sat işlemlerinde KARŞI TARAF budur.
--   Müşteri döviz alınca TL'sini bankaya verir, bankadan döviz alır (satışta tersi).
--   Banka özel bir müşteri kaydıdır (customer_no = 99999999); giriş yapmaz,
--   müşteri listelerinde gösterilmez (DAO filtreler).
-- (Tablolar 00-06 arası oluşturulmalı.)
-- =====================================================================
USE treasury_db;

-- Banka müşterisi (yalnızca yoksa ekle)
INSERT INTO customer (customer_no, role_id, customer_type_id, customer_name, surname, tc, phone, address, status)
SELECT 99999999,
       (SELECT role_id FROM role WHERE role_name = 'CUSTOMER'),
       (SELECT type_id FROM customer_type WHERE type_name = 'TUZEL'),
       'HAZİNE', 'BANKA', '99999999999', '-', 'Genel Müdürlük', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_no = 99999999);

-- Banka kasa hesapları (TRY/USD/EUR/GBP, Vadesiz, yüksek bakiye) — yoksa ekle
INSERT INTO account (customer_id, account_type_id, currency, balance)
SELECT c.customer_id,
       (SELECT type_id FROM account_type WHERE type_name = 'Vadesiz'),
       x.cur, x.bal
FROM customer c
JOIN (
        SELECT 'TRY' AS cur, 1000000000.00 AS bal
        UNION ALL SELECT 'USD', 100000000.00
        UNION ALL SELECT 'EUR', 100000000.00
        UNION ALL SELECT 'GBP', 100000000.00
     ) x
WHERE c.customer_no = 99999999
  AND NOT EXISTS (
        SELECT 1 FROM account a
        WHERE a.customer_id = c.customer_id AND a.currency = x.cur);

-- account_no atanmamış hesaplara 10 haneli numara ver (Java ile aynı kural)
UPDATE account SET account_no = 1000000000 + account_id WHERE account_no IS NULL;
