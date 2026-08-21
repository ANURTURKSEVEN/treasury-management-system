-- =====================================================================
-- 29 - Para Piyasası PLASMAN / BORÇ VERME (Money Market Lending)
-- mm_borrowing modülünün AYNASI. Nakit yönü terstir:
--   create  : banka kasasından fon ÇIKIŞI (-anapara)
--   mature  : banka kasasına GİRİŞ (+anapara+faiz-stopaj)
--   cancel  : kasaya anapara geri (+anapara)
-- Yaşam döngüsü: ACTIVE / MATURED / CANCELLED / ROLLED_OVER / EARLY_CLOSED
-- ELLE ÇALIŞTIR (00..28'den sonra).
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS mm_lending (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    reference_no          VARCHAR(30)   NOT NULL UNIQUE,
    counterparty_id       INT           NULL,
    market_type           VARCHAR(20)   NOT NULL DEFAULT 'MONEY_MARKET',
    purpose               VARCHAR(20)   NULL,
    dealer                VARCHAR(50)   NULL,
    broker                VARCHAR(120)  NULL,
    comment               VARCHAR(500)  NULL,
    bcs                   VARCHAR(20)   NULL,          -- Taraf Tipi (placeholder; iş kuralı yok)
    currency              VARCHAR(3)    NOT NULL,
    principal             DECIMAL(18,2) NOT NULL,
    interest_rate         DECIMAL(12,8) NOT NULL,
    day_count             VARCHAR(10)   NOT NULL DEFAULT 'A/365',
    deal_date             DATE          NOT NULL,
    value_date            DATE          NOT NULL,
    maturity_date         DATE          NOT NULL,
    interest_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount            DECIMAL(18,2) NOT NULL DEFAULT 0,   -- stopaj
    repayment_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    stopaj_flag           TINYINT       NOT NULL DEFAULT 0,
    funding_account_id    INT           NULL,          -- valörde fonun ÇIKACAĞI banka kasası
    collection_account_id INT           NULL,          -- vadede tahsilin GİRECEĞİ banka kasası
    correspondent1_bic    VARCHAR(20)   NULL,
    correspondent2_bic    VARCHAR(20)   NULL,
    create_swift          TINYINT       NOT NULL DEFAULT 0,
    create_mt320          TINYINT       NOT NULL DEFAULT 0,
    create_mt202          TINYINT       NOT NULL DEFAULT 0,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/MATURED/CANCELLED/ROLLED_OVER/EARLY_CLOSED
    -- Yaşam döngüsü (rollover / erken kapama) izleme alanları
    parent_deal_id        INT           NULL,          -- rollover ile bu deal'i doğuran eski deal
    rolled_to_id          INT           NULL,          -- bu deal'in rollover ile devredildiği yeni deal
    early_closed_at       DATE          NULL,
    penalty_amount        DECIMAL(18,2) NULL,
    created_by            VARCHAR(50)   NULL,
    created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    settled_at            DATE          NULL,           -- = valör
    matured_at            DATE          NULL,
    CONSTRAINT fk_ml_counterparty FOREIGN KEY (counterparty_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_ml_parent       FOREIGN KEY (parent_deal_id)  REFERENCES mm_lending(id)
);

CREATE INDEX idx_ml_status   ON mm_lending (status);
CREATE INDEX idx_ml_maturity ON mm_lending (maturity_date);

-- Masraf / komisyon kalemleri (mm_charge aynası)
CREATE TABLE IF NOT EXISTS mm_lending_charge (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    ml_id       INT           NOT NULL,
    charge_type VARCHAR(30)   NOT NULL,   -- SWIFT_FEE/CORRESPONDENT_FEE/BROKER_FEE/TXN_FEE/OTHER
    amount      DECIMAL(18,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    payer       VARCHAR(20)   NOT NULL DEFAULT 'BANKA',   -- BANKA/KARSI_KURUM
    note        VARCHAR(200)  NULL,
    CONSTRAINT fk_mlc_ml FOREIGN KEY (ml_id) REFERENCES mm_lending(id) ON DELETE CASCADE
);

-- Menü / yetki: MM_LEND ekranı ve ADMIN rolüne bağlanması
INSERT INTO screen (screen_key, screen_name, for_type) VALUES
    ('MM_LEND','Para Piyasası Borç Verme','USER')
ON DUPLICATE KEY UPDATE screen_name = VALUES(screen_name);

INSERT IGNORE INTO role_screen (role_id, screen_id)
SELECT r.role_id, s.screen_id FROM role r JOIN screen s
WHERE r.role_name = 'ADMIN' AND s.screen_key = 'MM_LEND';
