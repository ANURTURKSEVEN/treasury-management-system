-- =====================================================================
-- 27 - Müşteriye özel FX kur fiksasyonu (deal + P&L + iptal + audit)
--   Mevcut customer_fixed_rate (standing fix, SpotTradePanel okur) BOZULMAZ.
--   Bir fixing FIXED olunca ilgili yön customer_fixed_rate'e upsert edilir (köprü).
--   İşlem tipi banka perspektifi: BANKA_SATIS (sell_rate) / BANKA_ALIS (buy_rate) / PARITE (ileride).
-- =====================================================================
USE treasury_db;

CREATE TABLE IF NOT EXISTS customer_fx_fixing (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    reference_no      VARCHAR(30) NOT NULL UNIQUE,       -- FX-YYYYMMDD-000001
    customer_no       INT NOT NULL,
    customer_id       INT NULL,
    transaction_type  VARCHAR(20) NOT NULL,              -- BANKA_SATIS / BANKA_ALIS / PARITE
    rate_type         VARCHAR(10) NOT NULL DEFAULT 'DOVIZ', -- DOVIZ / EFEKTIF
    currency          VARCHAR(8)  NOT NULL,              -- işlem dövizi (FX)
    pair              VARCHAR(12) NOT NULL,              -- ör. EUR/TRY
    amount            DECIMAL(18,2) NULL,                -- işlem tutarı (FX)
    market_rate       DECIMAL(18,6) NOT NULL,            -- anlık piyasa kuru (ilgili yön)
    treasury_cost     DECIMAL(18,6) NOT NULL,            -- hazine maliyeti (şimdilik = market_rate)
    spread            DECIMAL(18,6) NOT NULL DEFAULT 0,
    customer_buy_rate  DECIMAL(18,6) NULL,               -- fix alış (banka alış yönü)
    customer_sell_rate DECIMAL(18,6) NULL,               -- fix satış (banka satış yönü)
    pnl               DECIMAL(18,2) NULL,                -- tahmini kâr/zarar
    pnl_currency      VARCHAR(8) NULL DEFAULT 'TRY',
    description       VARCHAR(255) NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'FIXED',  -- FIXED / CANCELLED
    cancellation_rate DECIMAL(18,6) NULL,
    cancellation_pnl  DECIMAL(18,2) NULL,
    created_by        VARCHAR(50) NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_at      TIMESTAMP NULL
);

CREATE INDEX idx_fx_customer ON customer_fx_fixing (customer_no);
CREATE INDEX idx_fx_status   ON customer_fx_fixing (status);
