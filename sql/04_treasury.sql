-- =====================================================================
-- 04 - Hazine tabloları: kurlar + spot/borrowing/lending işlemleri
--   currency_rate     : versiyonlu kurlar (status/batch)
--   spot_transaction  : döviz al/sat + transfer FX kaydı  -> customer
--   borrowing/lending : ileride kullanılacak              -> customer
-- =====================================================================
USE treasury_db;

-- DÖVİZ KURLARI (versiyonlu): status 1=güncel/0=eski, batch_id = TCMB çekimi
CREATE TABLE IF NOT EXISTS currency_rate (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    currency       VARCHAR(3) NOT NULL,
    buy_rate       DECIMAL(18,6) NOT NULL,   -- Döviz Alış  (Forex Buying)
    sell_rate      DECIMAL(18,6) NOT NULL,   -- Döviz Satış (Forex Selling)
    effective_buy  DECIMAL(18,6) NULL,       -- Efektif Alış  (Banknote Buying)
    effective_sell DECIMAL(18,6) NULL,       -- Efektif Satış (Banknote Selling)
    status         TINYINT NOT NULL DEFAULT 1,
    batch_id       INT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO currency_rate (currency, buy_rate, sell_rate, effective_buy, effective_sell, status, batch_id) VALUES
    ('USD', 40.20, 40.35, 40.17, 40.41, 1, 1),
    ('EUR', 43.10, 43.28, 43.07, 43.34, 1, 1),
    ('GBP', 51.40, 51.65, 51.36, 51.73, 1, 1);

-- SPOT İŞLEM (döviz al/sat + transfer arka plan FX)
CREATE TABLE IF NOT EXISTS spot_transaction (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    buy_currency     VARCHAR(3) NOT NULL,
    sell_currency    VARCHAR(3) NOT NULL,
    buy_amount       DECIMAL(18,2) NOT NULL,
    sell_amount      DECIMAL(18,2) NOT NULL,
    rate             DECIMAL(18,6) NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_spot_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE IF NOT EXISTS borrowing (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    customer_id   INT NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    amount        DECIMAL(18,2) NOT NULL,
    interest_rate DECIMAL(8,4) NOT NULL,
    start_date    DATE NOT NULL,
    maturity_date DATE NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrowing_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE IF NOT EXISTS lending (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    customer_id   INT NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    amount        DECIMAL(18,2) NOT NULL,
    interest_rate DECIMAL(8,4) NOT NULL,
    start_date    DATE NOT NULL,
    maturity_date DATE NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lending_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);
