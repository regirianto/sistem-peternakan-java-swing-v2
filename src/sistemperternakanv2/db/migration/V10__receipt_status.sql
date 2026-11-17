-- V10: status penerimaan Sehat/Mati. Kambing mati diretur ke supplier dan
-- tidak masuk persediaan, utang, atau hitungan diterima.

ALTER TABLE receipt_items ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SEHAT' AFTER goat_id;
ALTER TABLE receipt_items MODIFY goat_id BIGINT NULL;
ALTER TABLE receipt_items MODIFY weight_kg DECIMAL(12,3) NULL;
ALTER TABLE receipt_items ADD CONSTRAINT ck_ri_status CHECK (status IN ('SEHAT','MATI'));

ALTER TABLE purchase_items ADD COLUMN rejected_qty INT NOT NULL DEFAULT 0 AFTER received_qty;
