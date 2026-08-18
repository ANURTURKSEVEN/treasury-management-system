-- =====================================================================
-- 14 - Performans index'leri (loglar büyüdükçe sorgular yavaşlamasın)
-- =====================================================================
USE treasury_db;

CREATE INDEX ix_activity_created   ON activity_log (created_at);
CREATE INDEX ix_activity_customer  ON activity_log (customer_no);
CREATE INDEX ix_activity_type      ON activity_log (action_type);
CREATE INDEX ix_error_created      ON error_log (created_at);
CREATE INDEX ix_rate_cur_status    ON currency_rate (currency, status);
CREATE INDEX ix_account_customer   ON account (customer_id, status);
CREATE INDEX ix_installment_lending ON loan_installment (lending_id, status);
