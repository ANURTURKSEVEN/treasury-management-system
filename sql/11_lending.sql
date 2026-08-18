-- =====================================================================
-- 11 - Lending (Kredi) tablosu — başvuru/onay akışlı, kredi türlü
--   Akış: müşteri BAŞVURU yapar (status 0) -> admin değerlendirir ->
--         onaylar (1, para kullandırılır) veya reddeder (2). Ödenince 3.
--   loan_type: IHTIYAC / TASIT / KONUT
-- =====================================================================
USE treasury_db;

DROP TABLE IF EXISTS lending;

CREATE TABLE lending (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    customer_id     INT NOT NULL,
    account_id      INT NOT NULL,
    loan_type       VARCHAR(20) NOT NULL,       -- IHTIYAC / TASIT / KONUT
    currency        VARCHAR(3) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,     -- talep/anapara
    interest_rate   DECIMAL(8,4) NOT NULL,      -- yıllık %
    term_months     INT NOT NULL,
    monthly_payment DECIMAL(18,2) NOT NULL,
    total_due       DECIMAL(18,2) NOT NULL,
    status          TINYINT NOT NULL DEFAULT 0, -- 0 başvuru /1 aktif /2 red /3 kapandı
    reject_reason   VARCHAR(255) NULL,
    start_date      DATE NULL,                  -- onayda dolar
    maturity_date   DATE NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lending_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);
