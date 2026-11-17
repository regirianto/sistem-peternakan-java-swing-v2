# Sistem Manajemen Peternakan Kambing

Aplikasi Java Swing + layanan internal REST + MySQL untuk manajemen peternakan
kambing: pembelian, kandang, kesehatan, penggemukan, pemotongan, penjualan,
pembayaran, pakan, biaya & alokasi, jurnal otomatis, laporan JasperReports,
dan multi-cabang.

## Struktur proyek (NetBeans Ant)

```text
SistemPerternakanV2/
├── src/sistemperternakanv2/
│   ├── id/peternakan/domain/       aturan uang, alokasi, laba, permission
│   ├── id/peternakan/service/      API, repositori, migrasi, backup
│   ├── id/peternakan/reporting/    ReportEngine PDF/CSV
│   ├── id/peternakan/desktop/      klien Swing (API client + UI)
│   ├── db/migration/               skrip SQL V1..V8
│   ├── reports/                    template .jrxml
│   └── lib/                        semua jar dependency
├── test/                           unit test (JUnit 5)
├── nbproject/                      konfigurasi project NetBeans
├── build.xml
├── service.properties
├── dist/                           hasil build
└── docs/, scripts/
```

Tidak ada Maven. Dependency cukup berupa jar di
`src/sistemperternakanv2/lib`.

Database kosong akan otomatis dimigrasikan dan diisi data contoh saat aplikasi
desktop pertama kali dijalankan (`demo.auto_seed=true`). Untuk produksi,
set `demo.auto_seed=false` di `service.properties`.

## Menjalankan di NetBeans

1. `File → Open Project` lalu pilih folder proyek ini.
2. Pastikan JDK 21 dipakai.
3. Klik **Run (F6)**. Launcher
   `id.peternakan.desktop.Launcher` akan:
   - menjalankan migrasi database;
   - menyalakan layanan API di `http://localhost:8765`;
   - membuka jendela login Swing.
4. MySQL dev harus hidup di `127.0.0.1:3307` sesuai `service.properties`.

## Build dan jalankan dari terminal

```bash
# Build (NetBeans Ant)
"/Applications/Apache NetBeans.app/Contents/Resources/netbeans/extide/ant/bin/ant" clean jar

# Aplikasi lengkap (layanan + UI)
java -jar dist/SistemPerternakanV2.jar

# Layanan saja (untuk E2E/server)
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain serve

# Migrasi / owner pertama / data contoh
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain migrate
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain bootstrap-owner \
  --username owner --password 'Owner#2024'
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain demo
```

`ant jar` menyalin dependency ke `dist/lib/` dan menulis `Class-Path` pada
manifest, jadi `java -jar dist/SistemPerternakanV2.jar` langsung bekerja.

## Konfigurasi

Salin `service.properties.example` menjadi `service.properties`, lalu isi:

```properties
db.url=jdbc:mysql://127.0.0.1:3307/peternakan?... 
db.user=root
db.password=...
server.port=8765
```

Detail operasional ada di `docs/OPERATIONS.md`.

## Test

```bash
# Unit test (JUnit 5, tanpa Maven)
scripts/test.sh

# E2E API (209 skenario) + UI smoke
scripts/e2e/run-all.sh
```

Laporan E2E: `docs/E2E_REPORT.md`.
Dokumentasi suite: `docs/e2e/README.md`.

## Akun demo

| akun | password | peran | cabang |
|---|---|---|---|
| owner | Owner#2024 | OWNER | semua |
| admin | Admin#2024 | ADMIN | Pusat |
| petugas | Petugas#2024 | PETUGAS | Pusat |
| cikarang | Cikarang#2024 | PETUGAS | Cikarang |

Data demo: kambing Pusat (K-001 terjual, K-002 sakit, K-003 aktif),
Cikarang (CK-001 terjual, CK-002 dipotong), pakan, biaya, daging, jurnal,
dan laporan.

## Dokumen lain

- `docs/PANDUAN_MENJALANKAN.md` — panduan lengkap menjalankan aplikasi.
- `docs/ARCHITECTURE.md` — desain, multi-cabang, keuangan.
- `docs/OPERATIONS.md` — konfigurasi, backup/restore.
- `IMPLEMENTATION_STATUS.md` — status fitur.
- `summary.md` — ringkasan non-teknis.
