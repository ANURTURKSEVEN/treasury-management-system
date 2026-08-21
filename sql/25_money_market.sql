-- =====================================================================
-- 25 - Para Piyasası (Money Market) Borçlanma — deal capture
--   Bankanın bir karşı kurumdan fon alması (borrowing) işlemini kaydeder.
--   Retail vadeli mevduat (borrowing tablosu) ile KARIŞMAZ; ayrı tablodur.
--   Alacak/ödeme hesabı bankanın kendi kasa hesabıdır (customer_no 99999999).
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS correspondent_bank (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    bank_name  VARCHAR(120) NOT NULL,
    bic        VARCHAR(20)  NOT NULL UNIQUE,   -- SWIFT/BIC kodu
    country    VARCHAR(40),
    active     TINYINT NOT NULL DEFAULT 1
);

INSERT INTO correspondent_bank (bank_name, bic, country) VALUES
    ('COMMERZBANK AG',            'COBADEFF001', 'Almanya'),
    ('KT BANK AG - FRANKFURT',    'KTAGDEFFXXX', 'Almanya'),
    ('DEUTSCHE BANK AG',          'DEUTDEFFXXX', 'Almanya'),
    ('JPMORGAN CHASE - LONDON',   'CHASGB2LXXX', 'İngiltere'),
    ('CITIBANK NA - NEW YORK',    'CITIUS33XXX', 'ABD')
ON DUPLICATE KEY UPDATE bank_name = VALUES(bank_name);

CREATE TABLE IF NOT EXISTS mm_borrowing (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    reference_no     VARCHAR(30) NOT NULL UNIQUE,   -- MM-YYYYMMDD-000001 (sistem üretir)
    counterparty_id  INT NULL,                      -- karşı kurum (customer, TUZEL)
    market_type      VARCHAR(20) NOT NULL DEFAULT 'MONEY_MARKET',
    purpose          VARCHAR(20) NULL,              -- ALM / LIQUIDITY / FUNDING / OTHER
    dealer           VARCHAR(50) NULL,              -- işlemi yapan personel
    broker           VARCHAR(120) NULL,             -- opsiyonel aracı
    comment          VARCHAR(500) NULL,
    bcs              VARCHAR(20) NULL,              -- Taraf Tipi (kesin tanım doğrulanamadı; placeholder)
    currency         VARCHAR(3)  NOT NULL,
    principal        DECIMAL(18,2) NOT NULL,        -- borçlanma (anapara)
    interest_rate    DECIMAL(12,8) NOT NULL,        -- yıllık %
    day_count        VARCHAR(10) NOT NULL DEFAULT 'A/360',
    deal_date        DATE NOT NULL,
    value_date       DATE NOT NULL,
    maturity_date    DATE NOT NULL,
    interest_amount  DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,   -- stopaj
    repayment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,   -- anapara + faiz - stopaj
    stopaj_flag      TINYINT NOT NULL DEFAULT 0,
    receiving_account_id INT NULL,   -- valörde fonun gireceği banka kasa hesabı
    repayment_account_id INT NULL,   -- vadede ödemenin çıkacağı banka kasa hesabı
    correspondent1_bic VARCHAR(20) NULL,
    correspondent2_bic VARCHAR(20) NULL,
    create_swift     TINYINT NOT NULL DEFAULT 0,
    create_mt320     TINYINT NOT NULL DEFAULT 0,
    create_mt202     TINYINT NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / MATURED / CANCELLED
    created_by       VARCHAR(50) NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settled_at       DATE NULL,
    matured_at       DATE NULL,
    CONSTRAINT fk_mm_counterparty FOREIGN KEY (counterparty_id) REFERENCES customer(customer_id)
);

CREATE INDEX idx_mm_status   ON mm_borrowing (status);
CREATE INDEX idx_mm_maturity ON mm_borrowing (maturity_date);

-- Menü / rol yetkisi: Para Piyasası Borçlanma ekranı (yalnız personel)
INSERT INTO screen (screen_key, screen_name, for_type) VALUES
    ('MM_BORROW','Para Piyasası Borçlanma','USER')
ON DUPLICATE KEY UPDATE screen_name = VALUES(screen_name);

INSERT IGNORE INTO role_screen (role_id, screen_id)
SELECT r.role_id, s.screen_id FROM role r JOIN screen s
WHERE r.role_name = 'ADMIN' AND s.screen_key = 'MM_BORROW';
