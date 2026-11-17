-- V5: hapus total fitur kas (akun kas, transfer, modal, dan arus kas).
-- Transaksi kini mencatat kas/bank langsung ke akun jurnal 1101/1102.

ALTER TABLE payments DROP FOREIGN KEY fk_pay_account;
ALTER TABLE payments DROP COLUMN cash_account_id;
ALTER TABLE slaughter_orders DROP FOREIGN KEY fk_so_cash;
ALTER TABLE slaughter_orders DROP COLUMN cash_account_id;
ALTER TABLE expenses DROP COLUMN cash_account_id;

DROP TABLE IF EXISTS cash_entries;
DROP TABLE IF EXISTS account_transfers;
DROP TABLE IF EXISTS cash_accounts;
