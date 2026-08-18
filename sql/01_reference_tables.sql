-- =====================================================================
-- 01 - Referans (lookup) tabloları + sabit verileri
--   role, customer_type, account_type, screen
-- Bu tablolar hiçbir tabloya bağlı değildir; ilk bunlar oluşturulur.
-- =====================================================================
USE treasury_db;

-- ROL: ADMIN / TRADER / VIEWER / CUSTOMER
CREATE TABLE IF NOT EXISTS role (
    role_id          INT AUTO_INCREMENT PRIMARY KEY,
    role_name        VARCHAR(20)  NOT NULL UNIQUE,
    role_description VARCHAR(200),
    role_type        VARCHAR(20)  NOT NULL          -- USER / CUSTOMER
);

INSERT INTO role (role_name, role_description, role_type) VALUES
    ('ADMIN',    'Tüm işlemleri yapabilen yönetici', 'USER'),
    ('TRADER',   'İşlem oluşturur, silme yapamaz',   'USER'),
    ('VIEWER',   'Sadece görüntüleme ve rapor',      'USER'),
    ('CUSTOMER', 'Müşteri (gerçek veya tüzel)',      'CUSTOMER')
ON DUPLICATE KEY UPDATE role_description = VALUES(role_description);

-- MÜŞTERİ TİPİ: GERCEK / TUZEL
CREATE TABLE IF NOT EXISTS customer_type (
    type_id          INT AUTO_INCREMENT PRIMARY KEY,
    type_name        VARCHAR(20) NOT NULL UNIQUE,
    type_description VARCHAR(200)
);

INSERT INTO customer_type (type_name, type_description) VALUES
    ('GERCEK', 'Gerçek kişi (bireysel hesap)'),
    ('TUZEL',  'Tüzel kişi (işletme/kurum hesabı)')
ON DUPLICATE KEY UPDATE type_description = VALUES(type_description);

-- HESAP TÜRÜ: Vadesiz / Mevduat / Yatırım
CREATE TABLE IF NOT EXISTS account_type (
    type_id          INT AUTO_INCREMENT PRIMARY KEY,
    type_name        VARCHAR(20) NOT NULL UNIQUE,
    type_description VARCHAR(200)
);

INSERT INTO account_type (type_name, type_description) VALUES
    ('Vadesiz', 'Vadesiz mevduat hesabı'),
    ('Mevduat', 'Vadeli mevduat hesabı'),
    ('Yatırım', 'Yatırım hesabı')
ON DUPLICATE KEY UPDATE type_description = VALUES(type_description);

-- EKRAN (menü) tanımları
CREATE TABLE IF NOT EXISTS screen (
    screen_id   INT AUTO_INCREMENT PRIMARY KEY,
    screen_key  VARCHAR(30) NOT NULL UNIQUE,
    screen_name VARCHAR(50) NOT NULL,
    for_type    VARCHAR(20) NOT NULL           -- USER / CUSTOMER / BOTH
);

INSERT INTO screen (screen_key, screen_name, for_type) VALUES
    ('CUSTOMER','Müşteriler','USER'),        ('ACCOUNTS','Hesaplar','BOTH'),
    ('TRANSFER','Para Transferi','BOTH'),    ('SPOT','Spot FX','BOTH'),
    ('BORROWING','Borrowing','BOTH'),        ('LENDING','Lending','BOTH'),
    ('REPORTS','Raporlar','BOTH'),           ('USER_MGMT','Kullanıcı Yönetimi','USER'),
    ('ROLE_PERM','Rol Yetkileri','USER')
ON DUPLICATE KEY UPDATE screen_name = VALUES(screen_name);
