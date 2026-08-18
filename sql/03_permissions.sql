-- =====================================================================
-- 03 - Rol-Menü yetkileri: role_screen + varsayılan yetkiler
--   (role ve screen tabloları 01'de oluşturulmalı.)
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS role_screen (
    role_id   INT NOT NULL,
    screen_id INT NOT NULL,
    PRIMARY KEY (role_id, screen_id),
    CONSTRAINT fk_rs_role   FOREIGN KEY (role_id)   REFERENCES role(role_id)     ON DELETE CASCADE,
    CONSTRAINT fk_rs_screen FOREIGN KEY (screen_id) REFERENCES screen(screen_id) ON DELETE CASCADE
);

-- Varsayılan yetkiler (admin sonradan Rol Yetkileri ekranından değiştirebilir)
INSERT IGNORE INTO role_screen (role_id, screen_id)
SELECT r.role_id, s.screen_id FROM role r JOIN screen s WHERE
      (r.role_name = 'ADMIN')
   OR (r.role_name = 'TRADER'   AND s.screen_key IN ('CUSTOMER','ACCOUNTS','TRANSFER','SPOT','BORROWING','LENDING','REPORTS'))
   OR (r.role_name = 'VIEWER'   AND s.screen_key IN ('CUSTOMER','ACCOUNTS','REPORTS'))
   OR (r.role_name = 'CUSTOMER' AND s.screen_key IN ('TRANSFER','SPOT','BORROWING','LENDING'));
