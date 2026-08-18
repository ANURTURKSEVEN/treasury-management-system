-- =====================================================================
-- Örnek müşteriler + hesapları + login kullanıcıları
-- (00-14 kurulduktan SONRA çalıştırılır. Tekrar çalıştırmadan önce mevcut
--  örnekleri silmek isterseniz aşağıdaki not'a bakın.)
-- =====================================================================
SET NAMES utf8mb4;
USE treasury_db;

-- ---- 4 müşteri (3 gerçek + 1 tüzel) ----
INSERT INTO customer (customer_no, role_id, customer_type_id, customer_name, surname, tc, phone, address) VALUES
    (10000002, 4, 1, 'Mehmet',              'Demir', '22222222222', '5321112233', 'Ankara / Çankaya'),
    (10000003, 4, 1, 'Ayşe',                'Kaya',  '33333333333', '5332223344', 'İstanbul / Kadıköy'),
    (10000004, 4, 1, 'Fatma',               'Şahin', '44444444444', '5343334455', 'İzmir / Bornova'),
    (10000005, 4, 2, 'Yıldız Tekstil A.Ş.', NULL,    '55555555555', '2124445566', 'Bursa / OSB');

-- ---- Hesaplar (account_no benzersiz; mevcut 1000000004'ten devam) ----
INSERT INTO account (account_no, customer_id, account_type_id, currency, balance) VALUES
    (1000000005, (SELECT customer_id FROM customer WHERE customer_no=10000002), 1, 'TRY',  15000.00),
    (1000000006, (SELECT customer_id FROM customer WHERE customer_no=10000002), 1, 'USD',    500.00),
    (1000000007, (SELECT customer_id FROM customer WHERE customer_no=10000003), 1, 'TRY',  32000.00),
    (1000000008, (SELECT customer_id FROM customer WHERE customer_no=10000004), 1, 'TRY',   8000.00),
    (1000000009, (SELECT customer_id FROM customer WHERE customer_no=10000004), 1, 'EUR',   1200.00),
    (1000000010, (SELECT customer_id FROM customer WHERE customer_no=10000005), 2, 'TRY', 250000.00),
    (1000000011, (SELECT customer_id FROM customer WHERE customer_no=10000005), 1, 'USD',  10000.00);

-- ---- Login kullanıcıları (username = müşteri no, parola 1234, rol CUSTOMER) ----
INSERT INTO users (username, password, role_id, customer_id, full_name) VALUES
    ('10000002', '1234', 4, (SELECT customer_id FROM customer WHERE customer_no=10000002), 'Mehmet Demir'),
    ('10000003', '1234', 4, (SELECT customer_id FROM customer WHERE customer_no=10000003), 'Ayşe Kaya'),
    ('10000004', '1234', 4, (SELECT customer_id FROM customer WHERE customer_no=10000004), 'Fatma Şahin'),
    ('10000005', '1234', 4, (SELECT customer_id FROM customer WHERE customer_no=10000005), 'Yıldız Tekstil A.Ş.');

-- Not: yeniden çalıştırmadan önce temizlemek için:
--   DELETE FROM users   WHERE username   IN ('10000002','10000003','10000004','10000005');
--   DELETE FROM account WHERE account_no  BETWEEN 1000000005 AND 1000000011;
--   DELETE FROM customer WHERE customer_no IN (10000002,10000003,10000004,10000005);
