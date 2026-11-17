-- V2: seed peran, permission, akun jurnal, kategori biaya, pengaturan awal.
INSERT INTO roles (code, name) VALUES
 ('OWNER', 'Owner'), ('ADMIN', 'Admin'), ('PETUGAS', 'Petugas');

INSERT INTO permissions (code, description) VALUES
 ('dashboard.view','Melihat dashboard operasional'),
 ('goat.read','Melihat data kambing'),
 ('goat.create','Menambah kambing'),
 ('goat.update','Mengubah data kambing'),
 ('goat.operational','Mencatat kegiatan operasional kambing'),
 ('pen.manage','Mengelola kandang'),
 ('weight.record','Mencatat penimbangan'),
 ('health.record','Mencatat kesehatan'),
 ('health.manage','Mengelola tindakan kesehatan dan karantina'),
 ('feed.manage','Mengelola item dan penerimaan pakan'),
 ('feed.record','Mencatat pemakaian pakan'),
 ('supplier.manage','Mengelola supplier'),
 ('customer.manage','Mengelola pelanggan'),
 ('purchase.manage','Membuat dan mengubah pembelian'),
 ('purchase.confirm','Mengonfirmasi pembelian'),
 ('receipt.manage','Mencatat penerimaan kambing'),
 ('sale.manage','Membuat dan mengubah penjualan'),
 ('sale.confirm','Mengonfirmasi penjualan'),
 ('sale.approve','Menyetujui diskon/harga di bawah pokok'),
 ('payment.record','Mencatat pembayaran'),
 ('payment.reverse','Membatalkan pembayaran'),
 ('expense.manage','Mencatat pengeluaran'),
 ('expense.post','Memposting pengeluaran'),
 ('allocation.finalize','Finalisasi alokasi biaya'),
 ('finance.view','Melihat laporan dan agregat keuangan'),
 ('finance.export','Mengekspor laporan keuangan'),
 ('report.operational','Melihat rekap operasional'),
 ('period.close','Menutup/membuka periode'),
 ('user.manage','Mengelola pengguna dan role'),
 ('audit.read','Melihat log aktivitas'),
 ('backup.manage','Backup dan restore'),
 ('settings.manage','Mengubah profil dan pengaturan usaha'),
 ('price.manage','Mengelola harga dan diskon');

-- OWNER: semua permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'OWNER';

-- ADMIN: operasional dan transaksi, tanpa permission keuangan/owner.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
 'dashboard.view','goat.read','goat.create','goat.update','goat.operational',
 'pen.manage','weight.record','health.record','health.manage','feed.manage','feed.record',
 'supplier.manage','customer.manage','purchase.manage','purchase.confirm','receipt.manage',
 'sale.manage','sale.confirm','payment.record','expense.manage','expense.post',
 'report.operational','price.manage'
) WHERE r.code = 'ADMIN';

-- PETUGAS: operasional terbatas.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
 'dashboard.view','goat.read','goat.operational','weight.record','health.record','feed.record','report.operational'
) WHERE r.code = 'PETUGAS';

INSERT INTO accounts (code, name, account_type) VALUES
 ('1101','Kas','ASET'),
 ('1102','Bank','ASET'),
 ('1201','Piutang Pelanggan','ASET'),
 ('1301','Persediaan Kambing','ASET'),
 ('1302','Persediaan Pakan','ASET'),
 ('1401','Uang Muka Supplier','ASET'),
 ('2101','Utang Supplier','KEWAJIBAN'),
 ('2201','Uang Muka Pelanggan','KEWAJIBAN'),
 ('3101','Modal','EKUITAS'),
 ('3102','Penarikan Modal','EKUITAS'),
 ('4101','Penjualan','PENDAPATAN'),
 ('5101','Harga Pokok Penjualan','BEBAN'),
 ('6101','Beban Pakan','BEBAN'),
 ('6102','Beban Operasional','BEBAN'),
 ('6103','Beban Penjualan','BEBAN'),
 ('6104','Kerugian Persediaan','BEBAN');

INSERT INTO expense_categories (code, name, treatment, allocatable) VALUES
 ('PAKAN','Pakan dan pemeliharaan','OPERASIONAL',1),
 ('OBAT','Pengobatan','OPERASIONAL',0),
 ('GAJI','Gaji dan upah','OPERASIONAL',1),
 ('SEWA','Sewa','OPERASIONAL',1),
 ('LISTRIK','Listrik dan air','OPERASIONAL',1),
 ('TRANSPORT','Transport penjualan','PENJUALAN',0),
 ('KOMISI','Komisi penjualan','PENJUALAN',0),
 ('LAIN','Biaya operasional lain','OPERASIONAL',1),
 ('MODAL','Pergerakan aset/modal','ASET',0);

INSERT INTO app_settings (setting_key, setting_value) VALUES
 ('company.name','Peternakan Kambing Sejahtera'),
 ('company.address','Jl. Peternakan No. 1, Indonesia'),
 ('company.phone',''),
 ('company.email',''),
 ('session.idle_minutes','15'),
 ('session.max_hours','12'),
 ('discount.admin_limit','50000'),
 ('allocation.rule_version','PRD-1.1'),
 ('attachment.dir','attachments'),
 ('backup.dir','backups'),
 ('report.footer','Laporan manajemen internal - bukan laporan keuangan statutori');

INSERT INTO cash_accounts (code, name, account_type, opening_balance) VALUES
 ('KAS','Kas Tunai','KAS',0), ('BANK','Bank','BANK',0);
