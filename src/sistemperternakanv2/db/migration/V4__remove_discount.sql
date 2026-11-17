-- V4: hapus fitur diskon pada penjualan dan pembelian.
-- Harga jual/beli kini tunggal: tidak ada diskon item maupun diskon dokumen.

ALTER TABLE sale_items DROP CHECK ck_si_disc;
ALTER TABLE sale_items DROP COLUMN discount;
ALTER TABLE sale_items DROP COLUMN document_discount;
ALTER TABLE sales DROP COLUMN total_discount;
ALTER TABLE purchase_items DROP COLUMN discount;

DELETE FROM app_settings WHERE setting_key = 'discount.admin_limit';

UPDATE permissions SET description = 'Menyetujui penjualan di bawah harga pokok'
WHERE code = 'sale.approve';
