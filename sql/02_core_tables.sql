-- =====================================================================
-- 02 - Çekirdek tablolar: müşteri, kullanıcı, hesap
--   customer  -> role, customer_type
--   users     -> role, customer   (müşteri girişi: username = customer_no)
--   account   -> customer, account_type
-- (Referans tabloları 01'de oluşturulmalı.)
-- =====================================================================
USE treasury_db;

-- MÜŞTERİ: genel bilgiler (şifre YOK — users'ta tutulur)
CREATE TABLE IF NOT EXISTS customer (
    customer_id      INT AUTO_INCREMENT PRIMARY KEY,
    customer_no      INT UNIQUE,
    role_id          INT NOT NULL,        -- her zaman CUSTOMER
    customer_type_id INT NOT NULL,        -- GERCEK / TUZEL
    customer_name    VARCHAR(100) NOT NULL,
    surname          VARCHAR(100),
    tc               VARCHAR(11)  NOT NULL UNIQUE,
    phone            VARCHAR(20),
    address          VARCHAR(255),
    status           TINYINT NOT NULL DEFAULT 1,   -- 1 aktif / 0 pasif
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_role FOREIGN KEY (role_id)          REFERENCES role(role_id),
    CONSTRAINT fk_customer_type FOREIGN KEY (customer_type_id) REFERENCES customer_type(type_id)
);

-- KULLANICI/GİRİŞ: staff + müşteri
CREATE TABLE IF NOT EXISTS users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,   -- müşteri: customer_no
    password    VARCHAR(100) NOT NULL,
    role_id     INT NOT NULL,
    customer_id INT NULL,                        -- müşteri kullanıcısıysa dolu
    full_name   VARCHAR(100),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role     FOREIGN KEY (role_id)     REFERENCES role(role_id),
    CONSTRAINT fk_users_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

-- HESAP: müşterinin bir/çok hesabı (tür + döviz + bakiye)
CREATE TABLE IF NOT EXISTS account (
    account_id      INT AUTO_INCREMENT PRIMARY KEY,
    account_no      BIGINT UNIQUE,               -- 10 haneli otomatik
    customer_id     INT NOT NULL,
    account_type_id INT NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    balance         DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          TINYINT NOT NULL DEFAULT 1,  -- 1 açık / 0 kapalı
    opened_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_acc_customer FOREIGN KEY (customer_id)     REFERENCES customer(customer_id),
    CONSTRAINT fk_acc_type     FOREIGN KEY (account_type_id) REFERENCES account_type(type_id)
);
