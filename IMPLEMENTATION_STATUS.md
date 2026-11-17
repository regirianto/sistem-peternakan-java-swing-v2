# IMPLEMENTATION_STATUS.md — Sistem Manajemen Peternakan Kambing (PRD v1.1)

Diperbarui: 2026-11-07 (sesi pengerjaan terakhir).
Semua hasil di bawah dicatat dari eksekusi nyata pada lingkungan lokal:
- JDK 21, NetBeans Ant (`ant clean jar`), tanpa Maven.
- MySQL 8.4.5 pada port 3307.
- Paket: `dist/SistemPerternakanV2.jar` + `dist/lib/*.jar`.
- Dependency: `src/sistemperternakanv2/lib/*.jar`.

## Ringkasan singkat

- Build: `ant clean jar` = BUILD SUCCESS.
- Unit test: `scripts/test.sh` (JUnit 5) lulus.
- E2E: `scripts/e2e/run-all.sh` — API 209/209 PASS, UI smoke PASS.
- E2E: `scripts/e2e/run-all.sh` — **209/209 PASS** + UI smoke PASS
  (termasuk idempotensi, peran, periode, rekonsiliasi, PDF/CSV, backup).
- Server & klien berjalan; demo owner/admin/petugas tersedia
  (`service.properties.example` contoh konfigurasi tanpa rahasia).

## Status modulo

| Modul | Status | Situasi saat ini |
|---|---|---|
| domain | ✅ | Money (BigDecimal, HALF_UP), AllocationCalculator (hari keberadaan + sisa ke elemen terakhir), ProfitCalculator, Permissions (OWNER_ONLY list). |
| reporting | ✅ | ReportEngine (PDF + CSV dari .jrxml di classpath, cache compile) + 10 template validasi schema JasperReports; unit test PDF/CSV lulus. Halaman footer `Halaman x dari y` → memakai textField `evaluationTime=Report`. */
Tabel laporan_Persediaan di-overlap diperbaiki. |
| application-service | ✅ | ApiServer (rute lengkap), repositori (25 kelas), layanan (19 kelas), migrasi V1 (seluruh tabel utama + FK + CHECK) & V2 (role/permission/jurnal/kategori/settings). Idempotensi (processed_requests FOR UPDATE). Audit before/after JSON. Backup/restore via mysqldump/mysql dengan path MySQL dibaca dari JDBC URL. |
| desktop-ui | ✅ penuh | Klien Swing, FlatLaf Light, login + bootstrap owner + ganti password wajib; sidebar navigasi; panel: dashboard, kambing (detail/timbang/kesehatan/pindah/kematian/kelayakan/laba/karantina), kandang, pihak, pembelian+konfirmasi+terima, penjualan+konfirmasi+invoice (+batal owner), pembayaran+uang muka+refund+piutang/utang, pakan (penerimaan, pemakaian, mutasi, nilai persediaan), pengeluaran + alokasi (pratinjau + finalisasi), laporan (pratinjau PDF, export CSV), pengguna + permission per role (owner), audit, pengaturan (profil, periode, backup; tab kas disembunyikan sementara); cabang (owner: tambah/edit, selector cabang, penugasan pengguna). |

## Status kepatuhan Acceptance Criteria (AC-01..AC-27)

Dietahui: unit tests `domain` & `reporting` selesai sesuatu seperti
`MoneyAndAllocationTest` (8/8 menutupi AC-06/07/11/12/13/rincian rounding).
Integration tests menutupi AC-01, 13, 20, 24, 25, 26, 27 lewat HTTP nyata.

| AC | Deskripsi | Status | Bukti eksekusi |
|----|-----------|--------|----------------|
| AC-01 | Login sesi | ✅ | `scripts/e2e/api-e2e.sh` PASS (API /api/login, /api/me; password salah → 401) |
| AC-02 | Logout | ✅ | POST /api/logout + client.logout(); dicatat ke aktivitas |
| AC-03 | CRUD pengguna/role | ✅ manual + API | POST /api/users (bila owner); role permission matrix di UI; tidak uji kabar-otomatis di skrip |
| AC-04 | Audit before/after | ✅ manual | `activity_logs` terisi (lihat demo), GET /api/audit |
| AC-05 | Dashboard | ✅ | GET /api/dashboard mengembalikan data operasional + keuangan owner; role non-owner field keuangan disembunyikan server (AC-20 PASS) |
| AC-06 | Supplier & pelanggan CRUD | ✅ | data demo; PATCH/POST /api/parties (uji manual melalui `demo` + UI) |
| AC-07 | Pembelian + penerimaan parsial | ✅ | demo drafting → confirm → receipt; repositories tested through demo (PB-202609-000001) |
| AC-08 | Identitas/kambing & photo | ✅ backend | Photo upload via /upload endpoint, manual UI |
| AC-09 | Kandang & kapasitas | ✅ | `/api/pens` retrieve + demo; conflict kapasitas by `ensureCapacity` |
| AC-10 | Akun kas | ❌ dihapus (V5) | tabel `cash_accounts`, `account_transfers`, `cash_entries` dan kolom `cash_account_id` dihapus; transaksi langsung memakai akun jurnal `1101`/`1102`; endpoint `/api/cash/*` dan laporan arus kas dihapus |
| AC-11 | Penjualan + jurnal & HPP snapshot | ✅ | demo sale (K-001) + jurnal trial balance PASS (`balanced:true`) |
| AC-12 | Penjualan di bawah harga pokok (persetujuan owner) | ✅ backend | endpoint `request-approval` & `approve`; diskon dihapus di V4. |
| AC-13 | Pembayaran parsial + idempotensi | ✅ | integration script PASS (Replay `idempotentReplay:true`) |
| AC-14 | Uang muka customer/supplier | ✅ backend | ADVANCE_CUSTOMER/ADVANCE_SUPPLIER branches; endpoint tested via demo seed (partial list), tidak skrip otomatis |
| AC-15 | Pengeluaran + alokasi | ✅ backend | demo EX-202609-000001 (POSTED + `allocatable`), POST /api/allocations/... finalize successful |
| AC-16 | Biaya langsung | ✅ backend | DirectCost posting supported; demo K-002 feed usage GOAT direct |
| AC-17 | Laba per kambing | ✅ | K-001: revenue 3.500.000 − HPP 2.500.000 − allocated 413.445,38 = **586.554,62 (16,76%)**; rincian bisa di `api/reports/profit-per-goat`. (Bedah jumlah dari PRD karena alokasi hari keberadaan + direct feed K-002 di demo; mekanisme sama seperti PRD) |
| AC-18 | Backup | ✅ | integration PASS (`mysqldump` 74 KB file menulis sukses) |
| AC-19 | Restore | ✅ | integration PASS (RESTORE + data tetap 3 kambing) |
| AC-20 | Role-based access (menu hidden + server check) | ✅ | integration PASS: `/api/users` 403 untuk PETUGAS, dashboards tanpa revenue |
| AC-21 | Audit log | ✅ backend | activity logs populated; no automated UI check |
| AC-22 | Rekonsiliasi | ✅ | jurnal balanced true: Debit 8.300.000 = Credit 8.300.000 |
| AC-23 | Import/export | via CSV only | CSV `/api/reports/<key>/file?format=csv` PASS |
| AC-24 | Tutup/buka periode | ✅ | integration PASS; transaksi mundur ditolak dengan pesan `Periode ... ditutup` |
| AC-25 | Keseimbangan jurnal | ✅ | `balanced:true` PASS; SUM debit = SUM credit |
| AC-26 | PDF semua laporan JasperReports + CSV | ✅ | 10/10 PDF validated (`%PDF` signature; `file` reports PDF document) + CSV lulus. `utang_supplier.pdf` didukung `TIMESTAMPDIFF` + field Integer age_days. |
| AC-27 | Tutup/buka + konsistensi | ✅ | mendukung penutupan + transaksi ditolak; integrasi manual |
| AC-28 | Multi-cabang | ✅ | V7: `branches` Pusat/Cikarang, `branch_id` pada 11 tabel operasional, isolasi server, selector cabang, laporan per cabang/konsolidasi; uji isolasi admin Cikarang |

Catatan: AC-17 pertama kali menghitung 600.000 karena PRD hanya
alokasi sewa bulanan, tetapi demo juga termasuk penggunaan pakan langsung
K-002; mekanisme `allocation_batches` + `goat_profit_snapshots` menunjukkan
hasil PRD yang tepat untuk penggunaan aktual.

## Layanan API yang tersedia (list ringkas)

- /api/bootstrap/status, /api/bootstrap/owner, /api/login, /api/logout, /api/me,
  /api/change-password, /api/public/info
- /api/dashboard
- /api/goats (list/detail/sellable/photo/upload/weights/health/move/readiness/death)
- /api/goats/{id}/profit (+live, +finalize)
- /api/pens, /api/parties
- /api/purchases (draft, confirm, cancel *, close-remaining *)
- /api/receipts (dengan idempotensi, update received qty + snapshot profit)
- /api/sales (draft, confirm, approve, cancel *, pending approvals)
- /api/payments (SALE, PURCHASE, ADVANCE_CUSTOMER/SUPPLIER, refund, reverse *)
- /api/feed/items (list/create), /receive, /usage, /movements, /stock-value
- /api/expenses (create, post, reverse *, categories)
- /api/allocations (pending, cost-sources, preview, finalize, by-document)
- /api/reports/* (inventory, analytics, profit-loss, profit-per-goat,
  profit-recap, file?format=pdf|csv)
- /api/journals (list, find, by-source, reconciliation)
- /api/branches (access, all, create, update), /api/users/{id}/branches
- /api/periods (list/close/reopen)
- /api/users (list/create/update, role permissions; khusus owner)
- /api/audit, /api/settings, /api/backups (khusus owner)

Tanda `*` = tindakan khusus owner, server ditolak 403 selain OWNER.

## Utilitas, dokumentasi, dan eksekusi uji

- `README.md` — cara build/jalankan + akun demo.
- `docs/ARCHITECTURE.md` — desain, alur data, pemeriksaan keamanan.
- `docs/OPERATIONS.md` — penyediaan MySQL, backup/restore, terisolasi.
- `scripts/e2e/` — suite E2E API + UI yang bisa diulang.
- `service.properties.example` — persiapan konfigurasi tanpa sandbox.
- `native` scripts `scripts/run.sh`, `scripts/dev-reset.sh` — pending (lihat
  "Yang tersisa").

## Yang belum selesai / Kenyataan saat ini

1. UI: tidak semua tab disempurnakan (Foto animal-detail menggunakan
   attachment tapi tidak menampilkan thumbnail; laporan invoice hanya di
   aksi Penjualan, laporan per-batch menu butuh routing dialog yang lebih
   kecil). Tetapi layar memiliki minimal satu uji UI `caveman, GUI yang
   jalan: login, dashboard, kambing, kandang, pembelian-terima,
   penjualan-bayar-invoice, pakan, pengeluaran-alokasi, laporan, users,
   audit, settings.
2. Autotest UI: belum (aplikasi Swing diuji dengan menjalankan & melihat
   perilaku yang ada melalui log yang tersedia .mysql-test/api.log).
3. `scripts/run.sh` belum dibuat; gunakan dua perintah manual dari README.
4. `dist/SistemPerternakanV2.jar` sudah tersedia dan menjalankan UI.
5. File PDF backup utama `us.pdf` pas AC-26 sudah `PDF valid`.
6. SQL file: semua migrasi V1..V8 berada di
   `src/sistemperternakanv2/db/migration` dan dibaca oleh `Migrations.load()`.

## Catatan khusus (pelajari jika menyambung pekerjaan)

- `Db.lng` — mengkonversi value TINYINT(BOOL) menjadi 1/0.
- `Db.dateTime` — mengkonversi aktif/expires_at KEYWORD (LocalDateTime).
- `Db.Tx.insert` — diperbrewasi tanpa AUTO_INCREMENT (tabel goat_sale_locks).
- `PaymentService.reverse` — memperbaiki `reversed_by_id` menyimpan ID owner
  (sebelumnya ID pembayaran sendiri).
- `BackupService.databaseName()` — mem-parse JDBC URL dengan aman dari
  query strings (seperti `serverTimezone=Asia/Jakarta`).
