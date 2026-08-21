-- Banka gelen kutusu / mesajlaşma sistemi.
--   sender / recipient: "SYSTEM" | "STAFF" | "STAFF:kadi" | "CUSTOMER:no"
--   category: INFO | LOAN_APPROVAL | DEPOSIT_APPROVAL | SURVEY
--   ref_no: onaya bağlı kayıt (ör. kredi id)
CREATE TABLE IF NOT EXISTS message (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    sender     VARCHAR(64),
    recipient  VARCHAR(64) NOT NULL,
    subject    VARCHAR(200) NOT NULL,
    body       TEXT,
    category   VARCHAR(30) NOT NULL DEFAULT 'INFO',
    ref_no     VARCHAR(50) NULL,
    is_read    TINYINT NOT NULL DEFAULT 0,   -- 0 okunmadı / 1 okundu
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
