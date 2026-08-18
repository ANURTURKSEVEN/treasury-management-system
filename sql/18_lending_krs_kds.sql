-- Kredi başvurusu anındaki KRS/KDS sonucunu denetim izi olarak sakla.
-- (Başvuru yapıldığı andaki durumu dondurur; sonradan veri değişse bile kalır.)
ALTER TABLE lending
    ADD COLUMN krs_score    INT NULL         AFTER status,
    ADD COLUMN krs_band     VARCHAR(20) NULL AFTER krs_score,
    ADD COLUMN kds_decision VARCHAR(10) NULL AFTER krs_band,
    ADD COLUMN evaluated_at TIMESTAMP NULL   AFTER kds_decision;
