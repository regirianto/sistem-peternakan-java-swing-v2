# Konfigurasi Layanan

Konfigurasi dibaca dari file `service.properties` (atau file yang ditunjuk oleh
variabel lingkungan `SERVICE_CONFIG`) diaktifkan oleh variabel lingkungan dengan
urutan: environment > file > default.

| kunci               | contoh                                                       | arti |
|---------------------|--------------------------------------------------------------|------|
| db.url              | jdbc:mysql://localhost:3306/peternakan?useSSL=false&...       | URL koneksi MySQL 8.4 |
| db.user             | peternakan                                                   | akun aplikasi (bukan root) |
| db.password         | password                                                     | jangan kommit |
| server.port         | 8765                                                         | port API internal |
| attachment.dir      | attachments                                                  | folder foto kambing |
| backup.dir          | backups                                                      | folder file .sql backup |
| mysqldump.bin       | /opt/homebrew/opt/mysql@8.4/bin/mysqldump                    | biner backup |
| mysql.bin           | /opt/homebrew/opt/mysql@8.4/bin/mysql                        | biner restore |

Jangan pernah meletakkan di repositori. Karena `service.properties` sama-sama
rahasia, pindahkan ke luar repo bila proses berproduksi.

## MySQL 8.4 Produksi

1. Buat database:
   ```sql
   CREATE DATABASE peternakan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'peternakan'@'%' IDENTIFIED BY 'passwordnya';
   GRANT ALL PRIVILEGES ON peternakan.* TO 'peternakan'@'%';
   FLUSH PRIVILEGES;
   ```
2. Build: `ant clean jar` (atau Clean and Build di NetBeans).
3. Migrasi:
   `java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" id.peternakan.service.ServiceMain migrate`
4. Owner pertama:
   `... id.peternakan.service.ServiceMain bootstrap-owner --username owner --password 'PasswordMin10' --name "Nama"`
5. Jalankan layanan:
   `... id.peternakan.service.ServiceMain serve`
6. Jalankan klien: `java -jar dist/SistemPerternakanV2.jar`
   (launcher juga menyalakan layanan) atau `java -cp ... id.peternakan.desktop.DesktopApp`
   dengan `API_BASE_URL` bila server di mesin lain.

## Backup

`POST /api/backups` oleh owner menulis file `peternakan-yyyyMMdd-HHmmss.sql`
ke folder `backup.dir` dengan `mysqldump --single-transaction`.

Restore harus mengirim `{"file": "<lokasi backup>", "confirm":"RESTORE"}` ke
`POST /api/backups/restore`; file dipulihkan ke database, kemudian migrasi
dijalankan ulang untuk menaikkan skema bila dampingnya versi baru.

## Backup terisolasi untuk pengujian/percobaan

```
mkdir -p .mysql-test && /opt/homebrew/opt/mysql@8.4/bin/mysqld \
  --initialize-insecure --datadir="$PWD/.mysql-test/data"
/opt/homebrew/opt/mysql@8.4/bin/mysqld --socket=$PWD/.mysql-test/mysql.sock \
  --port=3307 --datadir=$PWD/.mysql-test/data --mysqlx=0 &
SERVICE_CONFIG=.mysql-test/service.properties java -cp "dist/SistemPerternakanV2.jar:dist/lib/*" id.peternakan.service.ServiceMain migrate
```

## Laporan PDF / CSV

Berkas dihasilkan dari `.jrxml` kelas `Reporting`, temuan `POST
/api/reports/<key>/file?format=pdf|csv`. Semua stempel waktu pakai zona Asia/Jakarta,
nomor halaman footer modal `HALAMAN x`.

## Idempotensi

Perintah posting (pembayaran, penerimaan, konfirmasi penjualan, pembatalan,
pengeluaran) wajib mengirim `X-Request-Id` (atau `"requestId"` di body).
Yang terjadi bila ID plat berduplikat: server mengembalikan hasil yang sama
tanda `idempotentReplay: true`, dokumen tidak kolok terjadi dua kali.
