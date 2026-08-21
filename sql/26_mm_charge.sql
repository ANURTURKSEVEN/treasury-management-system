-- =====================================================================
-- 26 - Para Piyasası deal masraf/komisyon kalemleri
--   Her kalem bir mm_borrowing deal'ine bağlıdır.
--   payer = BANKA ise ilgili kalem deal ile aynı transaction'da kasadan düşülür.
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS mm_charge (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    mm_id       INT NOT NULL,
    charge_type VARCHAR(30) NOT NULL,   -- SWIFT_FEE / CORRESPONDENT_FEE / BROKER_FEE / TXN_FEE / OTHER
    amount      DECIMAL(18,2) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    payer       VARCHAR(20) NOT NULL DEFAULT 'BANKA',   -- BANKA / KARSI_KURUM
    note        VARCHAR(200) NULL,
    CONSTRAINT fk_mmcharge_deal FOREIGN KEY (mm_id) REFERENCES mm_borrowing(id) ON DELETE CASCADE
);
