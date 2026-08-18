-- =====================================================================
-- SORGU / ARAMA ÖRNEKLERİ  (şema değil — inceleme/rapor sorguları)
-- Gerektiğinde ilgili bloğu seçip çalıştırın.
-- =====================================================================
USE treasury_db;

-- ---- ROLLER & YETKİLER ----
-- Rollerin erişebildiği ekranlar:
SELECT r.role_name, GROUP_CONCAT(s.screen_key ORDER BY s.screen_id) AS ekranlar
FROM role r
LEFT JOIN role_screen rs ON r.role_id = rs.role_id
LEFT JOIN screen s       ON rs.screen_id = s.screen_id
GROUP BY r.role_name ORDER BY r.role_id;

-- ---- KULLANICILAR ----
-- Personel (müşteri hariç):
SELECT u.id, u.username, r.role_name, u.full_name
FROM users u JOIN role r ON u.role_id = r.role_id
WHERE r.role_type = 'USER';

-- ---- MÜŞTERİLER ----
-- Aktif müşteriler (tipiyle):
SELECT c.customer_no, c.customer_name, c.surname, ct.type_name AS tur, c.phone, c.status
FROM customer c JOIN customer_type ct ON c.customer_type_id = ct.type_id
WHERE c.status = 1 ORDER BY c.customer_no;

-- Bir müşteriyi ara (ada göre):
-- SELECT * FROM customer WHERE customer_name LIKE 'Ah%';

-- ---- HESAPLAR ----
-- Tüm hesaplar (müşteri + tür + bakiye):
SELECT a.account_no, c.customer_no, CONCAT(c.customer_name,' ',IFNULL(c.surname,'')) AS musteri,
       t.type_name AS tur, a.currency, FORMAT(a.balance,2) AS bakiye,
       CASE a.status WHEN 1 THEN 'Açık' ELSE 'Kapalı' END AS durum, a.opened_at
FROM account a
JOIN customer c     ON a.customer_id = c.customer_id
JOIN account_type t ON a.account_type_id = t.type_id
ORDER BY c.customer_no, a.account_id;

-- Filtreler:
-- ... WHERE c.customer_no = 10000001;      -- tek müşteri
-- ... WHERE a.currency = 'USD';            -- sadece USD
-- ... WHERE a.balance > 50000;             -- bakiye eşiği

-- Müşteri bazında toplam varlık (döviz bazında):
SELECT c.customer_no, a.currency, FORMAT(SUM(a.balance),2) AS toplam
FROM account a JOIN customer c ON a.customer_id = c.customer_id
WHERE a.status = 1
GROUP BY c.customer_no, a.currency ORDER BY c.customer_no;

-- ---- KURLAR ----
-- Güncel kurlar:
SELECT currency, buy_rate, sell_rate, updated_at FROM currency_rate WHERE status = 1;
-- Kur geçmişi (bloklar halinde):
SELECT batch_id AS blok, currency, buy_rate, sell_rate,
       CASE status WHEN 1 THEN 'Güncel' ELSE 'Eski' END AS durum, updated_at
FROM currency_rate ORDER BY batch_id DESC, currency;

-- ---- SPOT / TRANSFER İŞLEMLERİ ----
SELECT s.id, c.customer_no, s.buy_currency, s.buy_amount,
       s.sell_currency, s.sell_amount, s.rate, s.transaction_date
FROM spot_transaction s JOIN customer c ON s.customer_id = c.customer_id
ORDER BY s.id DESC;

-- ---- RAPOR / AKTİVİTE LOG ----
-- Son işlemler:
SELECT id, action_type, username, customer_no, amount, currency, description, created_at
FROM activity_log ORDER BY id DESC LIMIT 50;
-- İşlem türüne göre:
-- ... WHERE action_type = 'TRANSFER';
-- Tutar eşiği + döviz:
-- ... WHERE amount >= 1000 AND currency = 'USD';

-- ---- HATA LOG ----
SELECT id, error_type, error_source, error_caller, LEFT(error_message,60) AS mesaj, username, created_at
FROM error_log ORDER BY id DESC LIMIT 50;

-- =====================================================================
-- HIZLI KONTROL — tabloya hızlı göz atmak için (elle çalıştır)
-- =====================================================================
-- Çekirdek / referans
SELECT * FROM users;
SELECT * FROM customer;
SELECT * FROM role;
SELECT * FROM customer_type;
SELECT * FROM account_type;
SELECT * FROM screen;
SELECT * FROM role_screen ORDER BY role_id, screen_id;
SELECT * FROM account ORDER BY customer_id, account_id;

-- Hazine / işlemler
SELECT * FROM currency_rate    ORDER BY batch_id DESC, currency;
SELECT * FROM spot_transaction ORDER BY id DESC;

-- Kredi & mevduat
SELECT * FROM lending          ORDER BY id DESC;
SELECT * FROM loan_installment ORDER BY lending_id, seq_no;
SELECT * FROM borrowing        ORDER BY id DESC;

-- Trend / bildirim
SELECT * FROM treasury_snapshot ORDER BY id DESC;
SELECT * FROM customer_snapshot ORDER BY id DESC;
SELECT * FROM notification      ORDER BY id DESC;

-- Loglar
SELECT * FROM error_log    ORDER BY id DESC;
SELECT * FROM activity_log ORDER BY id DESC;

-- müşteri hesaplarının bilgileri: 
SELECT c.customer_no AS musteri_no,
       CONCAT(c.customer_name,' ',COALESCE(c.surname,'')) AS musteri,
       ct.type_name AS tip,
       a.account_no AS hesap_no,
       at.type_name AS hesap_turu,
       a.currency   AS doviz,
       FORMAT(a.balance,2) AS bakiye
FROM customer c
JOIN customer_type ct     ON ct.type_id = c.customer_type_id
LEFT JOIN account a        ON a.customer_id = c.customer_id AND a.status = 1
LEFT JOIN account_type at  ON at.type_id = a.account_type_id
WHERE c.customer_no <> 99999999   -- banka hazinesi hariç
  AND c.status = 1                -- yalnız aktif müşteriler
ORDER BY c.customer_no, a.account_id;