-- =====================================================================
-- Müşteri hesapları sorguları (treasury_db)
--   Banka hazine "müşterisi" (customer_no = 99999999) hariç tutulur.
-- =====================================================================
--
-- ULAŞABİLECEĞİN TABLOLAR (treasury_db):
--   customer            Müşteriler (customer_id, customer_no, ad, soyad, tc, ...)
--   customer_type       Müşteri tipleri (Bireysel/Kurumsal ...)
--   account             Hesaplar (account_no, currency, balance, status, ...)
--   account_type        Hesap tipleri (Vadesiz/Yatırım ...)
--   currency_rate       Döviz kurları (currency, buy_rate, sell_rate, status)
--   customer_fixed_rate Müşteriye özel sabit (fix) kurlar
--   spot_transaction    Spot döviz al/sat işlemleri
--   borrowing           Vadeli mevduat kayıtları
--   lending             Kredi kayıtları
--   loan_installment    Kredi taksitleri
--   activity_log        İşlem/aktivite geçmişi
--   error_log           Hata kayıtları
--   notification        Bildirimler
--   dispute             İtirazlar
--   treasury_snapshot   Banka toplam varlık geçmişi (trend)
--   customer_snapshot   Müşteri toplam varlık geçmişi (trend)
--   users               Kullanıcılar (giriş: personel + müşteri)
--   role                Roller (ADMIN/TRADER/VIEWER/CUSTOMER)
--   screen              Ekranlar (menü ekran tanımları)
--   role_screen         Rol-ekran yetki eşleşmeleri
--
--   Bir tablonun içeriğini görmek için:   SELECT * FROM <tablo_adi> LIMIT 100;
--   Bir tablonun kolonlarını görmek için:  DESCRIBE <tablo_adi>;
--   Tüm tabloları listelemek için:         SHOW TABLES;
-- =====================================================================
USE treasury_db;

-- 1) DETAY: her hesap bir satır (müşteri + hesap bilgileri)
SELECT
    c.customer_no                         AS musteri_no,
    CONCAT(c.customer_name, ' ', COALESCE(c.surname, '')) AS musteri,
    ct.type_name                          AS musteri_tipi,
    a.account_no                          AS hesap_no,
    at.type_name                          AS hesap_turu,
    a.currency                            AS doviz,
    FORMAT(a.balance, 2)                  AS bakiye,
    CASE a.status WHEN 1 THEN 'Aktif' ELSE 'Pasif' END AS hesap_durumu
FROM customer c
JOIN customer_type ct ON ct.type_id = c.customer_type_id
LEFT JOIN account a   ON a.customer_id = c.customer_id
LEFT JOIN account_type at ON at.type_id = a.account_type_id
WHERE c.customer_no <> 99999999
  AND c.status = 1
ORDER BY c.customer_no, a.account_id;

-- 2) ÖZET: müşteri başına hesap sayısı + dövize göre bakiye
SELECT
    c.customer_no AS musteri_no,
    CONCAT(c.customer_name, ' ', COALESCE(c.surname, '')) AS musteri,
    COUNT(a.account_id) AS hesap_sayisi,
    GROUP_CONCAT(CONCAT(a.currency, ':', FORMAT(a.balance, 2)) ORDER BY a.currency SEPARATOR '  |  ') AS bakiyeler
FROM customer c
LEFT JOIN account a ON a.customer_id = c.customer_id AND a.status = 1
WHERE c.customer_no <> 99999999
  AND c.status = 1
GROUP BY c.customer_id
ORDER BY c.customer_no;

-- 3) TEK MÜŞTERİ: belirli bir müşterinin hesapları (numarayı değiştirin)
-- SELECT a.account_no, at.type_name AS tur, a.currency, FORMAT(a.balance,2) AS bakiye
-- FROM account a
-- JOIN customer c ON c.customer_id = a.customer_id
-- JOIN account_type at ON at.type_id = a.account_type_id
-- WHERE c.customer_no = 10000002 AND a.status = 1
-- ORDER BY a.account_id;
