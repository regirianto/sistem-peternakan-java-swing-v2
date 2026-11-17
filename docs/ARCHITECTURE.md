# Arsitektur Sistem Manajemen Peternakan Kambing

## Pandangan umum

```
 Swing client (desktop-ui)
   │  HTTP JSON + token bearer (no MySQL credentials)
   ▼
 internal API (application-service)
   │  HikariCP pool, JDBC prepared statements
   ▼
 MySQL 8.4 (InnoDB)  ──>  filesystem: attachments/ + backups/
```

- Klien Swing tidak menyentuh basisdata. Semua izin, keuangan, dan transaksi
  dievaluasi di layanan, sehingga menu tersembunyi bukan satu-satunya kontrol.
- Satu `Client` per panggilan `HttpClient`; token sesi disimpan di memori
  (`ApiClient.token`), tidak dijahit ke database.
- `service.main()` bisa dijalankan sebagai `migrate`, `bootstrap-owner`, `demo`,
  `create-user`, atau `serve` (lihat `ServiceMain`).

## Modul

- `domain`: aturan murni; `Money` (BigDecimal HALT_UP), `AllocationCalculator`
  (alokasi hari keberadaan dengan sisa pennies ke hari terakhir),
  `ProfitCalculator` (pendapatan - HPP - langsung - alokasi),
  `Permissions` + daftar OWNER_ONLY. Semua kelas + unit test.
- `reporting`: `ReportEngine` meload `.jrxml` dari `classpath:/reports/`;
  pembacaan SQL semua di layanan (tidak menerima SQL/template dari pengguna).
  10 template: invoice, kuitansi, persediaan, laba/kambing, rekap, laba periode,
  utang, piutang, operasional.
- `application-service`: `ApiServer` dengan route table + `services` (auth, pen,
  goat, purchase, receipt, sale, payment, feed, expense, allocation, profit,
  report, journal, cash, period, settings, backup, audit).
- `desktop-ui`: Swing + FlatLaf; `Ui.async` wrap semua panggilan dalam
  SwingWorker sehingga EDT tetap lega. Perubahan data dipicu oleh tombol
  (`--safe-refactor`), perintah posting mengirim `requestId`.

## Arsitektur data

Skema `V1__schema.sql` (innoDB + foreign keys) mencakup:

- `users/roles/role_permissions/sessions/processed_requests/activity_logs`
- `branches/user_branches` (V7)
- `parties/party_roles/purchases/purchase_items/receipts/receipt_items/goats`
- `pen_placements/weight_records/goat_sale_locks/gender_records/feed_items/feed_movements`
- `sales/sale_items/sale_approvals/payments/payment_allocations/expense_categories/expenses`
- `cost_sources/allocation_batches/cost_allocations/goat_profit_snapshots`
- `accounts/journal_entries/journal_lines/periods`
- `snapshot_wallets/audit/reconciliation` (dibangun di atas) plus
  `idempotency (processed_requests)` & `attachment/backup_history`.

Migrasi `V2__seed.sql` mengisi role/permission, akun jurnal (1101..6104),
kategori biaya, settings, periode. `V7__branches.sql` menambah cabang Pusat
dan Cikarang, kolom `branch_id` pada data operasional, serta penugasan
pengguna ke cabang.

## Multi-cabang

- Owner melihat semua cabang atau memilih satu cabang; pengguna lain hanya
  cabang yang ditugaskan pada `user_branches`.
- `branch_id` disimpan pada kandang, kambing, pembelian, penerimaan, penjualan,
  pengeluaran, pembayaran, pakan, pemotongan, batch daging, dan jurnal.
- Filter dan validasi cabang dilakukan di server (`support/BranchAccess`);
  UI hanya menyediakan selector dan kolom informasi.
- Kambing mengikuti kandang; perpindahan lintas cabang ditolak sampai fitur
  transfer dibuat.
- Jurnal memakai `1101`/`1102`; laba rugi per cabang dihitung dari dimensi
  `journal_entries.branch_id`, konsolidasi menjumlahkan semua cabang.

## Penerimaan dan retur

- Setiap baris `receipt_items` berstatus `SEHAT` atau `MATI`.
- Baris `MATI` tidak membuat kambing, tidak masuk kandang/persediaan/utang,
  dan menambah `purchase_items.rejected_qty`.
- Pembelian dianggap selesai bila `received_qty + rejected_qty = qty`.
- Biaya kirim dialokasikan hanya ke kambing yang diterima; bila semua mati,
  biaya kirim dicatat sebagai beban.

## Uang dan alokasi

- Semua uang `DECIMAL(18,2)` dan objek `Money` (HALT_HALF_UP, no double math).
- Penjualan di bawah harga pokok menunggu persetujuan owner
  (`SALE_APPROVE`). Diskon tidak lagi tersedia (V4).
- Fitur kas dihapus total (V5): tabel `cash_accounts`, `account_transfers`,
  `cash_entries` dan kolom `cash_account_id` tidak ada lagi. Jurnal kas
  memakai akun `1101` (Kas) atau `1102` (Bank) sesuai metode pembayaran.
- Alokasi biaya bersama memakai hari keberadaan kambing dalam rentang layanan;
  sisa pembagian diberikan terakhir.
- Setiap alokasi memicu `goat_profit_snapshots` versi + 1.

## Keamanan

- bcrypt (biaya 12) untuk semua password; no plaintext di log.
- Kunci unik `goat_sale_locks` (goat_id) mencegah dua penjualan aktif;
  di konfirmasi penjualan dengan `FOR UPDATE` + versi optimistic.
- `Permissions.OWNER_ONLY` antara lain: finance_view, finance_export,
  period_close, user_manage, audit_read, backup_manage, settings_manage,
  sale_approve, payment_reverse, allocation_finalize.
- Idempotensi disimpan di `processed_requests` dengan `FOR UPDATE`, satu
  `request_id` menghasilkan satu dokumen.
- Menutup periode membatasi semua transaksi sebelum `accounting_periods.close`
  (kondisi `PeriodGuard.requireOpen`).
- Aktivitas `audit.log` menulis sebelum/sesudah (JSON) untuk semua modul
  besar, termasuk ekspor laporan dan pemakaian password.

## Paket distribusi

- `dist/SistemPerternakanV2.jar` (Ant build, main `id.peternakan.desktop.Launcher`)
- `dist/lib/*.jar` (dependency, tercantum di `Class-Path` manifest)
- Source tunggal: `src/sistemperternakanv2/id/peternakan/...`
- Dependency: `src/sistemperternakanv2/lib/*.jar`

Layanan berjalan dengan
`java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" id.peternakan.service.ServiceMain serve`.
Klien dijalankan dengan `java -jar dist/SistemPerternakanV2.jar`
atau `... id.peternakan.desktop.DesktopApp` dengan
`API_BASE_URL=http://localhost:8765` bila server di mesin lain.
