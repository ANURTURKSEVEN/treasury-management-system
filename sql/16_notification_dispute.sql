-- =====================================================================
-- 16 - Bildirim genişletme + İtiraz (dispute)
--   notification: personel hedefi (target_role), tip ve referans eklenir.
--   dispute: müşteri itirazları; banka (admin/trader) sonuçlandırır.
--   (00-14 kurulduktan SONRA bir kez çalıştırılır.)
-- =====================================================================
USE treasury_db;

ALTER TABLE notification
    MODIFY customer_no INT NULL,
    ADD COLUMN target_role VARCHAR(10) NULL AFTER customer_no,
    ADD COLUMN type        VARCHAR(20) NOT NULL DEFAULT 'INFO' AFTER detail,
    ADD COLUMN ref_no      VARCHAR(50) NULL AFTER type;

CREATE TABLE IF NOT EXISTS dispute (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    customer_no     INT NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    customer_reason TEXT,
    status          VARCHAR(15) NOT NULL DEFAULT 'OPEN',   -- OPEN / RESOLVED / REJECTED
    resolved_by     VARCHAR(50) NULL,
    resolution      TEXT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
