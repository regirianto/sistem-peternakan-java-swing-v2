#!/usr/bin/env bash
# Jalankan seluruh E2E: API (209 skenario) + UI smoke.
# Hasil gabungan ditulis ke docs/E2E_REPORT.md.

set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
OUT="$ROOT/docs/e2e"
mkdir -p "$OUT"

API_RC=0
UI_RC=0
scripts/e2e/api-e2e.sh || API_RC=$?
scripts/e2e/ui-smoke.sh || UI_RC=$?

api_total="$(grep -o 'Total skenario: \*\*[0-9]*\*\*' "$OUT/E2E_API_REPORT.md" 2>/dev/null | grep -o '[0-9]*' | head -1)"
api_pass="$(grep -o 'Lulus: \*\*[0-9]*\*\*' "$OUT/E2E_API_REPORT.md" 2>/dev/null | grep -o '[0-9]*' | head -1)"
api_fail="$(grep -o 'Gagal: \*\*[0-9]*\*\*' "$OUT/E2E_API_REPORT.md" 2>/dev/null | grep -o '[0-9]*' | head -1)"
ui_status="$(grep -o 'Hasil: \*\*[A-Z]*\*\*' "$OUT/E2E_UI_REPORT.md" 2>/dev/null | grep -o 'PASS\|FAIL' | head -1)"

overall="PASS"
[ "$API_RC" -ne 0 ] && overall="FAIL"
[ "$UI_RC" -ne 0 ] && overall="FAIL"

{
  echo "# E2E Test Report — Sistem Peternakan Kambing"
  echo
  echo "- Tanggal: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "- Database uji: MySQL lokal 127.0.0.1:3307 (DB sekali pakai)"
  echo "- Server uji: port 8780 (API), 8781 (UI)"
  echo "- Status keseluruhan: **$overall**"
  echo
  echo "## Hasil"
  echo
  echo "| Suite | Skenario | Lulus | Gagal | Status |"
  echo "|---|---|---|---|---|"
  echo "| API E2E | ${api_total:-0} | ${api_pass:-0} | ${api_fail:-0} | $([ "$API_RC" -eq 0 ] && echo PASS || echo FAIL) |"
  echo "| UI E2E | 9 alur | - | - | ${ui_status:-FAIL} |"
  echo
  echo "## Cakupan"
  echo
  echo "- Auth: login, bootstrap, ganti password wajib, sesi, izin."
  echo "- Cabang: tambah/ubah, tolak RPH, penugasan pengguna, isolasi data."
  echo "- Kandang, kambing, berat, kesehatan, kesiapan jual, pindah kandang."
  echo "- Pembelian, penerimaan sebagian, over-receipt, uang muka supplier, utang."
  echo "- Penjualan per ekor/per kg, persetujuan di bawah HPP, pembatalan, invoice."
  echo "- Pembayaran, uang muka pelanggan, refund, reverse, idempotensi."
  echo "- Pakan per cabang, stok, pemakaian, nilai stok, mutasi."
  echo "- Pengeluaran, posting, reverse, alokasi biaya bersama."
  echo "- Pemotongan, batch daging, susut, penjualan daging."
  echo "- Jurnal per cabang, rekonsiliasi, tutup/buka periode, dashboard."
  echo "- Laporan PDF/CSV (7 laporan + invoice + kuitansi), filter cabang."
  echo "- Pengaturan, audit, backup, integritas stok dan jurnal."
  echo "- UI: login form, seluruh menu utama, data tabel, toolbar, selector cabang."
  echo "- UI flow: login form, pembelian → konfirmasi → penerimaan, penerimaan status Mati/retur, siap jual, penjualan per ekor/per kg + pembatalan, pakan, pengeluaran + alokasi, pemotongan + pembatalan, penjualan daging, susut, uang muka + refund + reverse, kandang, mitra, cabang, pengguna, operasi kambing, bayar utang, batal beli, tutup sisa, periode, edit pengaturan, backup, edit master data, ekspor CSV, pratinjau PDF, dan menu per role."
  echo "- Temuan UI flow: bug prefill jumlah pembayaran (objek penjualan bukan angka), bug tabel/form Pengaturan kosong (map dibaca sebagai list), dan bug admin ditolak saat membuka menu Pembayaran (piutang/utang salah memakai izin finance) ditemukan dan diperbaiki."
  echo
  echo "## Keterbatasan"
  echo
  echo "- Fitur yang belum diklik otomatis: foto/lampiran kambing dan restore backup (belum ada tombol UI); keduanya diuji lewat API atau manual."
  echo "- Restore backup tidak dijalankan otomatis karena akan menimpa DB uji."
  echo
  echo "## Cara menjalankan ulang"
  echo
  echo '```bash'
  echo "scripts/e2e/run-all.sh"
  echo '```'
  echo
  echo "Detail: \`docs/e2e/E2E_API_REPORT.md\`, \`docs/e2e/E2E_UI_REPORT.md\`, log di \`docs/e2e/\`."
} > "$ROOT/docs/E2E_REPORT.md"

echo "Laporan gabungan: docs/E2E_REPORT.md"
[ "$overall" = "PASS" ]
