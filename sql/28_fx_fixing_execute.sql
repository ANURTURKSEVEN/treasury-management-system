-- =====================================================================
-- 28 - FX fiksasyonun referansla işleme alınması (execution)
--   Bir FIXED fixing, referansıyla gerçek spot alım/satıma dönüştürülür.
--   status: FIXED -> EXECUTED (işlendi) ; iz için executed_at/by.
-- =====================================================================
USE treasury_db;

ALTER TABLE customer_fx_fixing
    ADD COLUMN executed_at TIMESTAMP  NULL AFTER cancelled_at,
    ADD COLUMN executed_by VARCHAR(50) NULL AFTER executed_at;
