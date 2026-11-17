# E2E Testing

Suite E2E memakai build Ant (`dist/SistemPerternakanV2.jar` + `dist/lib`) dan
MySQL lokal 3307. Database selalu sekali pakai; data demo di `peternakan` tidak
disentuh.

## Menjalankan semua

```bash
scripts/e2e/run-all.sh
```

Hasil utama: `docs/E2E_REPORT.md`.

## Menjalankan terpisah

```bash
# API E2E (209 skenario): build, migrasi V8, server 8780, seed lewat API
scripts/e2e/api-e2e.sh

# UI smoke: DB sekali pakai, demo, server 8781, Swing
scripts/e2e/ui-smoke.sh
```

Opsi:

```bash
SKIP_BUILD=1 scripts/e2e/api-e2e.sh    # pakai jar yang sudah ada
KEEP_DB=1 scripts/e2e/api-e2e.sh       # biarkan DB uji untuk investigasi
E2E_PORT=8790 scripts/e2e/api-e2e.sh   # ganti port API
```

## Prasyarat

- MySQL 8.4 di `127.0.0.1:3307` (bisa diubah lewat `E2E_DB_PORT`).
- Kredensial DB dibaca dari `service.properties`.
- Sesi grafis untuk UI smoke (bukan headless).
- `uuidgen`, `curl`, `jq`, Ant NetBeans (JDK 21).

## Struktur

- `api-e2e.sh` — seluruh alur bisnis lewat REST, assertion HTTP/JSON,
  integritas jurnal/stok, migrasi V8, laporan PDF/CSV.
- `ui-smoke.sh` — menyiapkan DB demo, menjalankan server, lalu
  `UiE2ESmokeTest` (menu, tabel, toolbar, selector cabang) dan
  `UiBusinessFlowE2ETest` (login form, beli → konfirmasi → terima,
  siap jual, jual → konfirmasi → bayar, pratinjau PDF).
- `run-all.sh` — menjalankan keduanya dan menulis laporan gabungan.

Test UI hanya aktif jika `E2E_UI=1`; pada `scripts/test.sh` biasa otomatis dilewati.
