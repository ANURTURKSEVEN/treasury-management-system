-- =====================================================================
-- TÜM KURULUM — sıfırdan veritabanı için hepsini sırayla çalıştırır.
-- Kullanım (mysql komut satırı, sql/ klasöründeyken):
--     mysql -u root -p < run_all.sql
-- veya mysql istemcisinde:  SOURCE run_all.sql;
--
-- Not: Workbench "Run SQL Script" SOURCE'u desteklemeyebilir; o durumda
-- dosyaları 00 -> 06 sırasıyla tek tek açıp çalıştırın.
-- =====================================================================
SOURCE 00_database.sql;
SOURCE 01_reference_tables.sql;
SOURCE 02_core_tables.sql;
SOURCE 03_permissions.sql;
SOURCE 04_treasury.sql;
SOURCE 05_logs.sql;
SOURCE 06_seed_data.sql;
SOURCE 07_bank.sql;
SOURCE 08_treasury_snapshot.sql;
SOURCE 09_notification.sql;
SOURCE 10_customer_snapshot.sql;
SOURCE 11_lending.sql;
SOURCE 12_loan_installment.sql;
SOURCE 13_borrowing.sql;
SOURCE 14_indexes.sql;
