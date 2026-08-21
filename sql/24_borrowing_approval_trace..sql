-- Mevduat onayı denetim izi: onaylayan personel + tarih
USE treasury_db;

ALTER TABLE borrowing
    ADD COLUMN approved_by VARCHAR(50) NULL AFTER reject_reason,
    ADD COLUMN approved_at TIMESTAMP   NULL AFTER approved_by;