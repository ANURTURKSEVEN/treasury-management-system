-- =====================================================================
-- 22 - Kredi kullandırım (disbursement) ayrımı + onay izi
--   Onay ile para çıkışı ayrılır:
--     status 0 başvuru -> ONAY -> status 4 (onaylandı, kullandırılmadı)
--                       -> KULLANDIR -> status 1 (aktif, para verildi)
--   approved_by/approved_at : onayı kim/ne zaman yaptı (denetim izi)
--   disbursed_at            : para ne zaman kullandırıldı
-- =====================================================================
USE treasury_db;

ALTER TABLE lending
    ADD COLUMN approved_by  VARCHAR(50) NULL AFTER kds_decision,
    ADD COLUMN approved_at  TIMESTAMP   NULL AFTER approved_by,
    ADD COLUMN disbursed_at DATE        NULL AFTER approved_at;
