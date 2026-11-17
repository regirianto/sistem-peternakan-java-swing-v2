# E2E Test Report — Sistem Peternakan Kambing

- Tanggal: 2026-11-17 10:37:57
- Database uji: MySQL lokal 127.0.0.1:3307 (DB sekali pakai)
- Server uji: port 8780 (API), 8781 (UI)
- Status keseluruhan: **PASS**

## Hasil

| Suite | Skenario | Lulus | Gagal | Status |
|---|---|---|---|---|
| API E2E | 259 | 259 | 0 | PASS |
| UI E2E | 9 alur | - | - | PASS |

## Cakupan

- Auth: login, bootstrap, ganti password wajib, sesi, izin.
- Cabang: tambah/ubah, tolak RPH, penugasan pengguna, isolasi data.
- Kandang, kambing, berat, kesehatan, kesiapan jual, pindah kandang.
- Pembelian, penerimaan sebagian, over-receipt, uang muka supplier, utang.
- Penjualan per ekor/per kg, persetujuan di bawah HPP, pembatalan, invoice.
- Pembayaran, uang muka pelanggan, refund, reverse, idempotensi.
- Pakan per cabang, stok, pemakaian, nilai stok, mutasi.
- Pengeluaran, posting, reverse, alokasi biaya bersama.
- Pemotongan, batch daging, susut, penjualan daging.
- Jurnal per cabang, rekonsiliasi, tutup/buka periode, dashboard.
- Laporan PDF/CSV (7 laporan + invoice + kuitansi), filter cabang.
- Pengaturan, audit, backup, integritas stok dan jurnal.
- UI: login form, seluruh menu utama, data tabel, toolbar, selector cabang.
- UI flow: login form, pembelian → konfirmasi → penerimaan, penerimaan status Mati/retur, siap jual, penjualan per ekor/per kg + pembatalan, pakan, pengeluaran + alokasi, pemotongan + pembatalan, penjualan daging, susut, uang muka + refund + reverse, kandang, mitra, cabang, pengguna, operasi kambing, bayar utang, batal beli, tutup sisa, periode, edit pengaturan, backup, edit master data, ekspor CSV, pratinjau PDF, dan menu per role.
- Temuan UI flow: bug prefill jumlah pembayaran (objek penjualan bukan angka), bug tabel/form Pengaturan kosong (map dibaca sebagai list), dan bug admin ditolak saat membuka menu Pembayaran (piutang/utang salah memakai izin finance) ditemukan dan diperbaiki.

## Keterbatasan

- Fitur yang belum diklik otomatis: foto/lampiran kambing dan restore backup (belum ada tombol UI); keduanya diuji lewat API atau manual.
- Restore backup tidak dijalankan otomatis karena akan menimpa DB uji.

## Cara menjalankan ulang

```bash
scripts/e2e/run-all.sh
```

Detail: `docs/e2e/E2E_API_REPORT.md`, `docs/e2e/E2E_UI_REPORT.md`, log di `docs/e2e/`.
