# Panduan Menjalankan Aplikasi

Panduan ini untuk menjalankan **Sistem Manajemen Peternakan Kambing** versi
NetBeans Ant: sumber di `src/sistemperternakanv2`, dependency di
`src/sistemperternakanv2/lib`, tanpa Maven.

## 1. Prasyarat

- **JDK 21**
- **MySQL 8.4** (lokal atau server)
- **NetBeans** (opsional, untuk Run/Debug)
- **Ant** (biasanya sudah termasuk NetBeans)
- Dependency sudah tersedia di `src/sistemperternakanv2/lib`

Cek Java:

```bash
java -version
```

Pastikan keluarannya Java 21.

## 2. Struktur singkat

```text
SistemPerternakanV2/
├── src/sistemperternakanv2/
│   ├── id/peternakan/...      kode aplikasi
│   ├── db/migration/          migrasi SQL V1..V10
│   ├── reports/               template laporan .jrxml
│   └── lib/                   dependency jar
├── test/                      unit test
├── build.xml                  build Ant
├── nbproject/                 konfigurasi NetBeans
├── service.properties         konfigurasi lokal (tidak di-commit)
├── dist/                      hasil build
└── docs/
```

## 3. Konfigurasi database

### 3.1 Siapkan database

Contoh untuk MySQL lokal:

```sql
CREATE DATABASE peternakan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'peternakan'@'%' IDENTIFIED BY 'password-anda';
GRANT ALL PRIVILEGES ON peternakan.* TO 'peternakan'@'%';
FLUSH PRIVILEGES;
```

Jika memakai user `root`, sesuaikan `db.user` dan `db.password`.

### 3.2 Buat `service.properties`

```bash
cp service.properties.example service.properties
```

Edit isinya:

```properties
db.url=jdbc:mysql://127.0.0.1:3307/peternakan?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=utf8
db.user=peternakan
db.password=password-anda
server.port=8765
attachment.dir=attachments
backup.dir=backups
mysqldump.bin=mysqldump
mysql.bin=mysql
```

Catatan:
- `service.properties` berisi password dan **tidak boleh di-commit**.
- Port MySQL contoh di atas `3307`; ganti sesuai instalasi Anda.
- Port aplikasi default `8765`.

## 4. Menjalankan lewat NetBeans

1. Buka NetBeans.
2. `File → Open Project`.
3. Pilih folder `SistemPerternakanV2`.
4. Pastikan project memakai **JDK 21**.
5. Klik **Run (F6)**.

Launcher `id.peternakan.desktop.Launcher` akan:

1. menjalankan migrasi database;
2. menyalakan layanan API di `http://localhost:8765`;
3. membuka jendela login.

Jika database belum bisa dihubungi, akan muncul dialog
**“MySQL tidak dapat dihubungi”**. Periksa `service.properties` dan pastikan
MySQL hidup.

## 5. Menjalankan lewat terminal

Dari root proyek:

```bash
# Build
ant clean jar
```

Jika `ant` tidak ada di PATH, gunakan Ant bawaan NetBeans:

```bash
"/Applications/Apache NetBeans.app/Contents/Resources/netbeans/extide/ant/bin/ant" clean jar
```

Jalankan aplikasi lengkap (layanan + UI):

```bash
java -jar dist/SistemPerternakanV2.jar
```

Layanan saja:

```bash
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain serve
```

Cek layanan:

```bash
curl http://localhost:8765/api/health
```

Klien Swing hanya:

```bash
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.desktop.DesktopApp
```

Gunakan `API_BASE_URL` jika layanan berada di mesin lain:

```bash
API_BASE_URL=http://192.168.1.10:8765 \
  java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.desktop.DesktopApp
```

## 6. Database baru: owner pertama

Jika `demo.auto_seed=true` dan database masih kosong, aplikasi desktop otomatis:

1. membuat semua tabel/migrasi V1–V10;
2. membuat owner awal `owner` / `Owner#2024`;
3. memuat data contoh.

Jika auto-seed dimatikan, login akan menampilkan dialog **Bootstrap Owner**.
Isi username, password minimal 10 karakter, ulangi password, dan nama owner.

Alternatif lewat terminal:

```bash
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain bootstrap-owner \
  --username owner --password 'Owner#2024' --name "Owner"
```

Migrasi juga bisa dijalankan eksplisit:

```bash
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain migrate
```

## 7. Data contoh otomatis (development)

Saat aplikasi desktop dijalankan pertama kali pada database kosong, data contoh
dimuat otomatis. Auto-seed hanya berjalan jika **tidak ada** data operasional
(kambing, pembelian, penerimaan, penjualan, mitra, atau pakan), sehingga data
yang sudah ada tidak tercampur.

Matikan untuk produksi:

```properties
demo.auto_seed=false
```

Data contoh juga bisa dimuat manual jika auto-seed dimatikan:

```bash
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain demo
```

**Peringatan:** owner awal hasil auto-seed memakai password `Owner#2024`.
Segera ganti password setelah login.

Data yang dimuat otomatis: Pusat + Cikarang, 5 kambing, pakan, biaya,
pemotongan, penjualan, daging, jurnal, dan akun demo.

Akun demo:

| Akun | Password | Role | Cabang |
|---|---|---|---|
| owner | `Owner#2024` | OWNER | semua |
| admin | `Admin#2024` | ADMIN | Pusat |
| petugas | `Petugas#2024` | PETUGAS | Pusat |
| cikarang | `Cikarang#2024` | PETUGAS | Cikarang |

Segera ganti password akun demo jika sistem dipakai di luar development.

## 8. Menjalankan test

Unit test:

```bash
scripts/test.sh
```

E2E API + UI:

```bash
scripts/e2e/run-all.sh
```

E2E memakai database sekali pakai (`peternakan_e2e` dan
`peternakan_ui_e2e`) dan **tidak menyentuh** database `peternakan`.

Laporan:
- `docs/E2E_REPORT.md`
- `docs/e2e/E2E_API_REPORT.md`
- `docs/e2e/E2E_UI_REPORT.md`

## 9. Backup dan restore

Backup manual lewat menu **Pengaturan → Backup Database** (owner), atau API
`POST /api/backups`.

Backup darurat sebelum reset/migrasi:

```bash
mysqldump -h127.0.0.1 -P3307 -uroot -p \
  --databases peternakan --single-transaction \
  > .backups/peternakan-$(date +%Y%m%d-%H%M%S).sql
```

Restore (hati-hati, menimpa database):

```bash
mysql -h127.0.0.1 -P3307 -uroot -p peternakan < file-backup.sql
```

## 10. Reset database development (opsional)

**Semua data akan hilang.** Hanya untuk development/testing.

```bash
# Drop + buat ulang
mysql -h127.0.0.1 -P3307 -uroot -p -e "
  DROP DATABASE IF EXISTS peternakan;
  CREATE DATABASE peternakan CHARACTER SET utf8mb4;
"

# Migrasi, owner, data contoh
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain migrate
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain bootstrap-owner \
  --username owner --password 'Owner#2024'
java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" \
  id.peternakan.service.ServiceMain demo
```

## 11. Troubleshooting

**“MySQL tidak dapat dihubungi”**
- Pastikan MySQL hidup.
- Periksa `db.url`, `db.user`, `db.password`.
- Pastikan database sudah dibuat.

**Port 8765 sudah dipakai**

```bash
lsof -nP -iTCP:8765 -sTCP:LISTEN
```

Hentikan proses lama atau ubah `server.port`.

**`ant: command not found`**

Gunakan Ant bawaan NetBeans seperti contoh di bagian 5, atau tambahkan
`ant/bin` ke `PATH`.

**Build gagal karena class tidak ditemukan**

Pastikan dependency masih ada:

```bash
ls src/sistemperternakanv2/lib | wc -l
```

Jika jar hilang, dependency harus dipulihkan ke folder tersebut.

**Aplikasi error setelah update kode**

```bash
ant clean jar
```

lalu jalankan ulang dari NetBeans.

**Data contoh tidak muncul otomatis**
- Pastikan `demo.auto_seed=true`.
- Auto-seed hanya berjalan dari launcher desktop (`java -jar`/Run NetBeans),
  bukan dari `ServiceMain serve`.
- Jika database sudah memiliki data operasional, auto-seed sengaja dilewati.

**Data lama tidak muncul setelah migrasi**

Cek versi skema:

```bash
mysql -h127.0.0.1 -P3307 -uroot -p -N \
  -e "SELECT MAX(version) FROM peternakan.schema_migrations;"
```

Versi terbaru saat ini: **V10**.

**Laporan PDF gagal dibuat**

Pastikan `src/sistemperternakanv2/lib` berisi dependency JasperReports dan
OpenPDF. Jalankan `scripts/test.sh` untuk memeriksa template laporan.

## 12. Checklist menjalankan

- [ ] JDK 21 terpasang
- [ ] MySQL hidup dan database `peternakan` ada
- [ ] `service.properties` sesuai
- [ ] `ant clean jar` berhasil
- [ ] `dist/SistemPerternakanV2.jar` dan `dist/lib/` ada
- [ ] Login berhasil
- [ ] Dashboard menampilkan data
- [ ] Backup database dibuat sebelum perubahan besar
