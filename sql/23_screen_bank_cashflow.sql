-- =====================================================================
-- 23 - Banka Kasası + Nakit Akışı ekranlarını rol/menü yetki sistemine ekle
--   Böylece "Rol Yetkileri" ekranından rollere atanabilir hale gelirler
--   (önceden DashboardFrame'de sadece ADMIN'e sabit kodluydu).
-- =====================================================================
USE treasury_db;

INSERT INTO screen (screen_key, screen_name, for_type) VALUES
    ('BANK','Banka Kasası','USER'),
    ('CASHFLOW','Nakit Akışı','USER')
ON DUPLICATE KEY UPDATE screen_name = VALUES(screen_name);

-- Varsayılan olarak ADMIN'e yetkiyi ver (mevcut davranış korunsun)
INSERT IGNORE INTO role_screen (role_id, screen_id)
SELECT r.role_id, s.screen_id
FROM role r JOIN screen s
WHERE r.role_name = 'ADMIN' AND s.screen_key IN ('BANK','CASHFLOW');
