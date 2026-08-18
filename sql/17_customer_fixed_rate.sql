-- Müşteriye özel sabitlenmiş (fixlenmiş) döviz kuru
-- Banka bir müşteriye, döviz bazında özel alış/satış kuru tanımlayabilir.
-- Müşteri spot işlemde güncel kur ile fixlenmiş kur arasında seçim yapar.
CREATE TABLE IF NOT EXISTS customer_fixed_rate (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    customer_no INT NOT NULL,
    currency    VARCHAR(8) NOT NULL,
    buy_rate    DECIMAL(18,6) NULL,     -- müşteriye özel alış kuru (banka müşteriden döviz alırken)
    sell_rate   DECIMAL(18,6) NULL,     -- müşteriye özel satış kuru (banka müşteriye döviz satarken)
    active      TINYINT DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_customer_currency (customer_no, currency)
);
