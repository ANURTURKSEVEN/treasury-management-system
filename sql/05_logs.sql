-- =====================================================================
-- 05 - Log tabloları (bağımsız): activity_log, error_log
-- =====================================================================
USE treasury_db;

-- AKTİVİTE (İŞLEM) LOG — Raporlar ekranı bu tabloyu okur
CREATE TABLE IF NOT EXISTS activity_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(30) NOT NULL,
    username    VARCHAR(50),
    customer_no INT NULL,
    amount      DECIMAL(18,2) NULL,   -- işlem tutarı (filtre için)
    currency    VARCHAR(3) NULL,      -- işlem dövizi (filtre için)
    description VARCHAR(255),
    details     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HATA LOG
CREATE TABLE IF NOT EXISTS error_log (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    error_type    VARCHAR(200),
    error_source  VARCHAR(255),   -- hatanın oluştuğu metot : satır
    error_caller  VARCHAR(255),   -- o metodun çağrıldığı yer : satır
    error_message TEXT,
    username      VARCHAR(50),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
