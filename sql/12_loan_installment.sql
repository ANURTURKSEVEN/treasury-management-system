-- =====================================================================
-- 12 - Kredi taksitleri (taksit planı)
--   Kredi onaylanınca her ay için bir taksit üretilir (vade tarihli).
--   Vade günü geldiğinde batch otomatik tahsil eder; elle de ödenebilir.
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS loan_installment (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    lending_id  INT NOT NULL,
    seq_no      INT NOT NULL,               -- taksit sırası (1..n)
    due_date    DATE NOT NULL,
    amount      DECIMAL(18,2) NOT NULL,
    status      TINYINT NOT NULL DEFAULT 0, -- 0 bekliyor / 1 ödendi
    paid_date   DATE NULL,
    CONSTRAINT fk_inst_lending FOREIGN KEY (lending_id) REFERENCES lending(id) ON DELETE CASCADE
);
