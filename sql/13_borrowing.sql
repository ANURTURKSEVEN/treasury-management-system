-- =====================================================================
-- 13 - Borrowing (Vadeli Mevduat)
--   Müşteri parasını vadeli yatırır; banka faiz öder. Vade sonunda anapara+faiz döner.
--   contract_type: SABIT (erken bozmada faiz yok) / ESNEK (erken bozmada kısmi faiz)
--   status: 1 aktif / 0 kapandı ;  close_type: VADE / ERKEN
-- =====================================================================
USE treasury_db;

DROP TABLE IF EXISTS borrowing;

CREATE TABLE borrowing (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    customer_id     INT NOT NULL,
    account_id      INT NOT NULL,
    contract_type   VARCHAR(10) NOT NULL,       -- SABIT / ESNEK
    currency        VARCHAR(3) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,     -- anapara
    interest_rate   DECIMAL(8,4) NOT NULL,      -- yıllık %
    term_months     INT NOT NULL,
    interest_amount DECIMAL(18,2) NOT NULL,     -- vade sonu faiz
    total_return    DECIMAL(18,2) NOT NULL,     -- anapara + faiz
    start_date      DATE NOT NULL,
    maturity_date   DATE NOT NULL,
    status          TINYINT NOT NULL DEFAULT 1, -- 1 aktif / 0 kapandı
    close_type      VARCHAR(10) NULL,           -- VADE / ERKEN
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrowing_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);
