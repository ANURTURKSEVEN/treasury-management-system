-- =====================================================================
-- 09 - Bildirimler: müşteriye "hesabınıza para geldi" gibi uyarılar
--   Havale (aynı banka) ile para ALAN müşteriye bir bildirim yazılır;
--   müşteri giriş yapınca okunmamışlar gösterilir ve okundu işaretlenir.
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS notification (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    customer_no INT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    detail      TEXT,
    is_read     TINYINT NOT NULL DEFAULT 0,   -- 0 okunmadı / 1 okundu
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
