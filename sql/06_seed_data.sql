-- =====================================================================
-- 06 - Başlangıç verisi (personel + örnek müşteri)
--   (Tablolar 00-05 arası oluşturulmalı.)
-- =====================================================================
USE treasury_db;

-- Personel (customer_id NULL)
INSERT INTO users (username, password, role_id, full_name) VALUES
    ('admin',  'admin123',  (SELECT role_id FROM role WHERE role_name='ADMIN'),  'Sistem Yöneticisi'),
    ('trader', 'trader123', (SELECT role_id FROM role WHERE role_name='TRADER'), 'İşlem Kullanıcısı'),
    ('viewer', 'viewer123', (SELECT role_id FROM role WHERE role_name='VIEWER'), 'Rapor Kullanıcısı')
ON DUPLICATE KEY UPDATE username = username;

-- Örnek müşteri (genel bilgiler)
INSERT INTO customer (customer_no, role_id, customer_type_id, customer_name, surname, tc, phone, address, status)
VALUES (10000001,
        (SELECT role_id FROM role WHERE role_name='CUSTOMER'),
        (SELECT type_id FROM customer_type WHERE type_name='GERCEK'),
        'Ahmet', 'Yılmaz', '12345678901', '5551112233', 'İstanbul', 1)
ON DUPLICATE KEY UPDATE tc = tc;

-- Örnek müşterinin giriş bilgisi (username = customer_no, şifre = 1234)
INSERT INTO users (username, password, role_id, customer_id, full_name)
SELECT c.customer_no, '1234',
       (SELECT role_id FROM role WHERE role_name='CUSTOMER'),
       c.customer_id,
       CONCAT(c.customer_name, ' ', c.surname)
FROM customer c WHERE c.tc = '12345678901'
ON DUPLICATE KEY UPDATE username = username;
