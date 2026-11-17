# Product Requirements Document
# Sistem Manajemen Peternakan Kambing

**Versi:** 1.1  
**Tanggal:** 7 November 2026  
**Status:** Spesifikasi awal siap diturunkan menjadi desain dan implementasi  
**Platform:** Aplikasi desktop Java Swing, dikembangkan menggunakan NetBeans  
**Database:** MySQL  
**Generator laporan PDF:** JasperReports  
**Bahasa antarmuka:** Bahasa Indonesia  
**Mata uang:** Rupiah (IDR)  
**Zona waktu bisnis:** Asia/Jakarta

## 1. Ringkasan produk

Sistem membantu peternakan mengelola kambing yang dibeli dari supplier, penerimaan dan identitas setiap kambing, pemeliharaan, penjualan, pembayaran, serta evaluasi keuntungan. Setiap kambing mempunyai kode unik untuk menelusuri asal supplier, harga beli, perpindahan kandang, riwayat kesehatan, biaya, dan hasil penjualan.

Owner memperoleh laporan laba rugi setiap kambing terjual, laba rugi periode, arus kas, utang, piutang, dan analisis perkembangan bisnis. Admin mengelola transaksi sesuai izin; petugas mengelola kegiatan operasional. Laporan keuangan hanya boleh diakses owner, termasuk pratinjau, cetak, ekspor, dan permintaan data di layanan aplikasi.

Produk harus nyaman dipakai dalam kegiatan harian: menu jelas, formulir ringkas, tabel mudah dicari, umpan balik penyimpanan terlihat, dan tampilan konsisten.

## 2. Masalah, tujuan, dan ukuran keberhasilan

### 2.1 Masalah yang diselesaikan

- Identitas, kondisi, dan asal kambing sulit dilacak ketika pencatatan tersebar.
- Harga beli, biaya pemeliharaan, serta pembayaran belum terhubung dengan kambing tertentu.
- Keuntungan sering disamakan dengan selisih harga beli dan jual atau dengan saldo kas.
- Pembelian, penjualan, penerimaan, dan pembayaran berisiko tercatat ganda.
- Informasi keuangan owner perlu dibatasi dari pengguna operasional.

### 2.2 Tujuan

1. Menelusuri setiap kambing dari penerimaan sampai terjual atau mati.
2. Menghubungkan transaksi, pembayaran, dan biaya ke sumbernya.
3. Menghasilkan laba rugi per kambing yang dapat diperiksa rinciannya.
4. Menyediakan laporan keuangan manajerial khusus owner.
5. Mencatat perubahan penting beserta pengguna dan waktunya.
6. Mempermudah pekerjaan melalui UI desktop modern yang responsif.

### 2.3 Target penerimaan produk

| Indikator | Target |
| --- | --- |
| Ketertelusuran kambing | Semua kambing masuk memiliki kode unik, supplier, dan dokumen penerimaan |
| Konsistensi transaksi | Tidak ada penjualan ganda untuk kambing yang sama |
| Akurasi perhitungan | Semua skenario perhitungan dan rekonsiliasi pada bagian 15 lulus |
| Pembatasan akses | Semua percobaan akses keuangan oleh non-owner ditolak di layanan aplikasi |
| Kegunaan | Setelah pengenalan singkat, pengguna dapat mencari kambing dan menyelesaikan transaksi contoh tanpa bantuan pengembang |
| Respons daftar | Target p95 maksimal 2 detik pada dataset dan perangkat uji yang ditentukan di bagian 14 |

## 3. Asumsi dan batasan produk

- Versi pertama melayani satu usaha peternakan dengan beberapa kandang.
- Sumber kambing adalah supplier; pembibitan dan perkembangbiakan belum masuk versi pertama.
- Satu pembelian dan satu penjualan dapat memuat beberapa kambing.
- Harga beli dicatat per kambing. Harga jual mendukung per ekor dan per kilogram berat hidup.
- Pembelian dan penjualan mendukung pembayaran sebagian serta pelunasan.
- Sistem berjalan pada satu komputer atau beberapa komputer dalam LAN yang terhubung ke layanan aplikasi dan MySQL yang sama.
- Internet tidak wajib untuk transaksi harian; koneksi ke layanan aplikasi dan database tetap wajib. Sinkronisasi offline antarkomputer tidak termasuk cakupan.
- Laporan merupakan laporan manajemen internal dengan kebijakan biaya yang ditetapkan di dokumen ini. Pelaporan pajak, penilaian biologis berdasarkan standar akuntansi tertentu, dan laporan keuangan statutori berada di luar cakupan.
- Integrasi timbangan, RFID, payment gateway, aplikasi mobile, multi-cabang, dan marketplace ditunda.
- Penyusutan aset, penggajian terperinci, dan pengadaan pakan kredit ditunda. Versi awal tetap dapat mencatat pengeluaran gaji dan operasional sebagai beban periode.
- Pajak otomatis belum tersedia. Biaya tambahan harus diklasifikasikan jelas, tanpa diasumsikan sebagai perhitungan pajak.

## 4. Pengguna dan hak akses

### 4.1 Peran

**Owner:** mengawasi operasional, melihat seluruh keuangan, menetapkan hak akses, memfinalisasi alokasi biaya, menutup periode, dan menyetujui koreksi.

**Admin:** mengelola pelanggan, supplier, pembelian, penerimaan, harga, penjualan, pembayaran, serta pencatatan biaya sesuai izin. Admin dapat melihat nilai transaksi yang diperlukan untuk pekerjaan tersebut, tetapi tidak memperoleh agregat keuangan atau laba rugi.

**Petugas:** melihat identitas dan lokasi kambing, mencatat penimbangan, pemberian pakan, kesehatan, dan perpindahan kandang sesuai izin. Harga beli, harga jual, serta rincian laba tidak ditampilkan.

### 4.2 Matriks akses awal

| Kemampuan | Owner | Admin | Petugas |
| --- | --- | --- | --- |
| Dashboard operasional | Ya | Ya | Ya |
| Data kambing dan kandang | Penuh | Penuh | Lihat dan catat operasional |
| Penimbangan, kesehatan, pakan | Penuh | Penuh | Tambah dan ubah draft sendiri |
| Supplier dan pelanggan | Penuh | Penuh | Tidak |
| Pembelian, penerimaan, penjualan | Penuh | Buat dan konfirmasi sesuai izin | Tidak |
| Pembayaran transaksi | Penuh | Catat dan cetak kuitansi sesuai izin | Tidak |
| Input harga dan diskon | Penuh | Dalam batas yang ditetapkan owner | Tidak |
| Input pengeluaran dan biaya | Penuh | Ya | Tidak secara default |
| Rekap operasional tanpa nominal | Ya | Ya | Sesuai izin |
| Laba rugi per kambing/periode | Ya | Tidak | Tidak |
| Arus kas, saldo, utang/piutang agregat | Ya | Tidak | Tidak |
| Nilai persediaan dan analisis margin | Ya | Tidak | Tidak |
| Finalisasi alokasi dan tutup periode | Ya | Tidak | Tidak |
| Pembatalan transaksi terkonfirmasi | Ya, disertai alasan | Ajukan | Tidak |
| Pengguna, role, log seluruh pengguna | Ya | Tidak | Tidak |
| Backup dan restore | Ya | Tidak | Tidak |

Admin dapat melihat sisa tagihan pada transaksi yang sedang dikerjakannya. Daftar agregat utang/piutang beserta saldo total tetap merupakan laporan keuangan khusus owner.

### 4.3 Aturan pengelolaan akses

- Gunakan permission per aksi, seperti `goat.read`, `sale.create`, `payment.record`, `finance.view`, `finance.export`, `period.close`, dan `audit.read`.
- Owner dapat mengatur permission operasional untuk admin dan petugas. Permission keuangan wajib hanya untuk role OWNER.
- Tidak ada registrasi mandiri. Owner membuat akun; bootstrap owner pertama menggunakan proses instalasi sekali pakai tanpa password baku.
- Owner aktif terakhir tidak dapat dinonaktifkan atau diturunkan rolenya.
- Perubahan role, penonaktifan akun, atau logout mencabut sesi terkait; akses sensitif memeriksa izin terbaru.
- Non-owner tidak menerima field laba, harga pokok agregat, atau saldo pada respons data umum.
- Laporan keuangan yang telah diekspor menjadi tanggung jawab owner; file tersebut tidak dibagikan otomatis.

## 5. Kebutuhan fungsional

### FR-01 — Autentikasi dan sesi

Login menggunakan username dan password. Tersedia tampil/sembunyikan password, status koneksi, pesan kegagalan umum, logout, penggantian password, dan reset password oleh owner dengan kewajiban mengganti password saat masuk berikutnya. Sesi otomatis terkunci setelah 15 menit tidak aktif; nilai dapat diubah owner. Password dan token tidak ditulis ke log.

### FR-02 — Log aktivitas

Catat login berhasil/gagal, logout, perubahan akun/izin, tambah/ubah master data, konfirmasi dan pembatalan transaksi, pembayaran, perubahan harga, alokasi biaya, penutupan periode, cetak/ekspor keuangan, serta backup/restore. Data minimal: waktu server, user, aksi, entitas, ID dokumen, nilai sebelum/sesudah yang relevan, alasan, dan correlation ID. Owner dapat memfilter berdasarkan tanggal, pengguna, modul, atau nomor dokumen. Log transaksi berhasil disimpan dalam transaksi database yang sama dengan perubahan utama. Log tidak dapat diubah/dihapus dari aplikasi.

### FR-03 — Dashboard ringkas

Semua role mendapat jumlah kambing aktif, siap jual, karantina/sakit, kandang hampir penuh, penimbangan terbaru, dan kegiatan pemeliharaan. Owner mendapat kartu tambahan penjualan bersih, laba rugi periode, kas/bank, utang, serta piutang dengan periode yang jelas. Semua kartu dapat dibuka menuju daftar pendukung sesuai izin. Tampilkan waktu pembaruan terakhir dan tombol muat ulang. Angka kosong ditampilkan sebagai nol; kegagalan mengambil data tidak ditampilkan seolah-olah nol.

### FR-04 — Data dan identitas ternak

Data wajib: kode unik, supplier asal, penerimaan asal, jenis/ras, jenis kelamin, tanggal masuk, berat masuk, serta lokasi awal. Data tambahan: foto, tanggal lahir jika diketahui, perkiraan umur saat masuk, tanda fisik, dan catatan. Umur perkiraan wajib diberi label perkiraan dan tidak diubah menjadi tanggal lahir pasti.

Pisahkan status persediaan (AKTIF, TERJUAL, MATI, DIKEMBALIKAN_KE_SUPPLIER), kesehatan (SEHAT, SAKIT, PEMULIHAN), dan kelayakan jual (KARANTINA, SIAP_JUAL). Hanya kambing AKTIF dan SIAP_JUAL yang dapat dijual. Petugas dapat mencatat sakit; pelepasan karantina dilakukan pengguna berizin.

Detail kambing memuat tab Ringkasan, Berat, Kesehatan, Pakan, Perpindahan, dan Riwayat. Tab Biaya dan Laba Rugi hanya owner. Harga transaksi hanya bagi role yang memang berwenang. Kambing terjual/mati tetap memiliki riwayat dan tidak dapat dihapus.

### FR-05 — Kandang dan pemeliharaan

Kelola kode/nama kandang, kapasitas, status, dan catatan. Catat riwayat penempatan dengan waktu masuk/keluar; satu kambing aktif hanya memiliki satu penempatan berjalan. Kapasitas diperiksa saat penerimaan dan perpindahan. Penimbangan memuat tanggal, berat positif dalam kg, petugas, serta catatan. Berat terbaru ditentukan dari tanggal pengukuran, bukan urutan input.

Kesehatan memuat keluhan, hasil pemeriksaan yang dicatat petugas, obat/tindakan, biaya terkait, penanggung jawab, dan jadwal kontrol. Sistem mencatat tindakan yang diberikan, bukan menghasilkan diagnosis atau dosis otomatis.

Kematian memerlukan tanggal, alasan, pencatat, dan konfirmasi owner. Sistem melepas kapasitas kandang, menghentikan alokasi setelah tanggal kejadian, dan mencatat kerugian nilai tercatat kambing sebagai beban periode.

### FR-06 — Pakan dan stok

Kelola item pakan, satuan dasar, stok minimum, penerimaan stok tunai, dan pemakaian per kandang/per kambing. Gunakan rata-rata tertimbang bergerak untuk nilai persediaan pakan. Semua input per item menggunakan satu satuan dasar; konversi otomatis antarsatuan ditunda. Penerimaan menyimpan jumlah dan biaya; pemakaian menyimpan jumlah, tanggal, tujuan, dan biaya unit saat diposting.

Stok tidak boleh negatif. Pembelian pakan menambah aset persediaan; pemakaian mengakui beban pakan. Pemakaian yang telah diposting tidak boleh diubah langsung karena memengaruhi nilai; koreksi melalui pembalikan yang diawasi owner. Biaya pemakaian menjadi sumber alokasi analitis per kambing, bukan biaya tambahan kedua.

### FR-07 — Supplier dan pelanggan

Kelola kode, nama, nomor telepon, alamat, status aktif, dan catatan. Supplier memiliki riwayat pembelian dan penerimaan; pelanggan memiliki riwayat penjualan. Peringatkan kemungkinan duplikat berdasarkan nama/telepon tetapi izinkan nomor bersama. Entitas yang telah dipakai transaksi hanya boleh dinonaktifkan. Satu pihak dapat memiliki peran supplier dan pelanggan dengan referensi pihak yang sama.

### FR-08 — Pembelian dan penerimaan

Pembelian menyimpan nomor dokumen, supplier, tanggal, tanggal jatuh tempo, item rencana kambing, harga beli per ekor, diskon item, biaya perolehan, dan catatan. Alur: DRAFT → DIKONFIRMASI → DITERIMA_SEBAGIAN → SELESAI. DRAFT dapat dibatalkan; sisa yang belum diterima dapat ditutup oleh owner dengan alasan.

Penerimaan dapat dilakukan beberapa kali. Setiap baris penerimaan mengacu ke item pembelian, mencatat kambing aktual, berat, kondisi, kandang, serta tanggal terima. Cegah jumlah terima melebihi pesanan. Supplier, harga, dan diskon yang sudah diterima dikunci. Satu item aktual merepresentasikan satu kambing.

Asumsi versi pertama: tagihan supplier diakui berdasarkan kambing yang diterima. Pembayaran sebelum penerimaan dicatat sebagai uang muka supplier. Saat penerimaan, uang muka dapat dialokasikan ke tagihan tanpa mencatat pengeluaran kas kedua. Biaya pengiriman pembelian dibagi rata per kambing pada dokumen penerimaan terkait dan masuk harga pokok perolehan. Jika supplier menagih biaya terpisah, biaya tersebut memiliki dokumen sumber dan pembayaran sendiri agar tidak digandakan.

### FR-09 — Harga dan penjualan

Kelola harga penawaran dan riwayat perubahan harga. Harga transaksi disimpan sebagai snapshot dan tidak mengikuti perubahan harga master. Dua metode: PER_EKOR atau PER_KG. Metode per kg wajib menyimpan berat jual aktual dan tarif/kg; total adalah berat × tarif. Berat historis dapat ditawarkan sebagai referensi tetapi pengguna wajib mengonfirmasi berat transaksi.

Alur: DRAFT → DIKONFIRMASI → SELESAI atau DIBATALKAN. Konfirmasi merupakan saat serah terima dan pengakuan penjualan; DRAFT hanya penawaran dan belum mengubah stok atau laba. SELESAI berarti sudah dikonfirmasi dan lunas. Status pembayaran disimpan terpisah: BELUM_BAYAR, SEBAGIAN, LUNAS.

Konfirmasi memeriksa pelanggan aktif, harga, izin diskon, periode terbuka, serta ketersediaan seluruh kambing. Penjualan multi-kambing bersifat semua berhasil atau semua gagal. Setelah konfirmasi, seluruh kambing menjadi TERJUAL dan keluar dari penempatan kandang. Sistem menyimpan harga bersih per kambing, piutang, jurnal, dan log dalam satu transaksi atomik. Cetak invoice tidak menampilkan harga beli, biaya, atau keuntungan.

Owner menetapkan batas diskon admin; diskon di atas batas memerlukan persetujuan owner dalam aplikasi, bukan berbagi password. Harga jual di bawah harga pokok memerlukan persetujuan owner. Untuk admin, pesan hanya menyebut perlu persetujuan tanpa membuka nilai margin atau harga pokok tersembunyi.

### FR-10 — Pembayaran dan kas/bank

Kelola akun kas/bank oleh owner. Catat tanggal, jumlah, metode, akun tujuan/sumber, nomor referensi, bukti opsional, pencatat, dan transaksi sumber. Satu dokumen dapat memiliki banyak pembayaran. Versi pertama satu pembayaran dialokasikan ke satu dokumen; uang muka memiliki referensi draft yang kelak diselesaikan.

Pembayaran tidak boleh melebihi sisa tagihan. Sebelum konfirmasi penjualan, penerimaan uang adalah uang muka pelanggan; saat konfirmasi dialihkan ke piutang tanpa kas masuk kedua. Pembayaran supplier sebelum penerimaan diperlakukan sebagai uang muka supplier. Refund uang muka dan pembatalan pembayaran memerlukan owner dan referensi ke pembayaran awal.

Pembayaran piutang/utang tidak mengubah laba. Transfer antarakun kas/bank bukan pendapatan/beban dan tidak mengubah arus kas bersih gabungan. Setoran modal dan penarikan owner diberi kategori terpisah dari pendapatan/beban usaha. Laporan saldo dibatasi owner, sedangkan admin hanya melihat akun yang dapat dipilih untuk transaksi dan kuitansi terkait.

### FR-11 — Biaya dan alokasi

Setiap biaya memiliki tanggal pengakuan, kategori, nilai, pihak terkait bila ada, tujuan, bukti, dan status DRAFT/POSTED/REVERSED. Bedakan pengakuan biaya dari pembayarannya agar tagihan belum dibayar tetap tercatat. Untuk penyederhanaan versi pertama, pengeluaran operasional selain tagihan supplier kambing dicatat tunai; perluasan utang operasional ditunda.

Kategori wajib menentukan perlakuan:

1. **Biaya perolehan:** harga beli bersih dan pengiriman pembelian, melekat pada persediaan kambing lalu menjadi harga pokok saat dijual.
2. **Beban operasional:** pemakaian pakan, pengobatan, gaji, sewa, dan kebutuhan harian; diakui pada periode terjadinya.
3. **Beban penjualan:** transport penjualan/komisi; diakui pada periode terkait.
4. **Pergerakan aset/modal:** bukan beban; misalnya pembelian persediaan pakan, uang muka, dan setoran modal.

Biaya langsung dapat ditautkan ke satu kambing. Biaya bersama mempunyai rentang layanan, lingkup kandang atau peternakan, dan alokasi analitis berdasarkan hari-keberadaan kambing. Tidak boleh mengubah kategori setelah POSTED tanpa pembalikan.

### FR-12 — Rekap laporan dan keuntungan

Laba rugi per kambing dihitung otomatis dari sumber transaksi dan alokasi. Tidak ada kolom bebas untuk mengetik nominal keuntungan. Owner dapat mencatat koreksi melalui dokumen penyesuaian yang memiliki alasan dan jejak audit.

Setiap laporan menyediakan filter periode, supplier, pelanggan, kandang, atau kode kambing yang relevan; total selalu mengikuti seluruh hasil filter, bukan hanya halaman tabel. Tersedia pratinjau, PDF, dan CSV dengan encoding UTF-8. Cetak dan ekspor menggunakan izin serta filter yang sama dengan tampilan. Header mencantumkan usaha, judul, periode, filter, waktu cetak, dan pencetak.

### FR-13 — Perkembangan bisnis

Owner melihat grafik bulanan penjualan bersih, laba rugi, kas masuk/keluar; margin per kambing; lama pemeliharaan; perubahan berat; serta perbandingan hasil per supplier. Semua grafik disertai tabel angka dan definisi metrik. Perbandingan supplier menyertakan jumlah sampel dan periode agar tidak menyesatkan. Tidak ada prediksi AI pada versi pertama.

### FR-14 — Tutup periode dan pencadangan

Owner menutup periode bulanan setelah memeriksa dokumen draft, alokasi tertunda, saldo, dan rekonsiliasi. Periode tertutup menolak transaksi mundur. Pembukaan kembali hanya oleh owner, disertai alasan dan log; laporan ditandai versi baru. Backup mencakup database, lampiran/foto, dan manifest versi. Restore dijalankan dalam mode pemeliharaan dengan sesi pengguna lain dihentikan. Pengujian restore wajib sebelum rilis.

## 6. Perhitungan keuangan dan kebijakan pelaporan

### 6.1 Definisi

| Metrik | Definisi |
| --- | --- |
| Penjualan bersih kambing | Harga jual item dikurangi diskon item dan bagian diskon dokumen |
| Harga pokok perolehan kambing | Harga beli bersih ditambah alokasi biaya perolehan |
| Margin kotor per kambing | Penjualan bersih dikurangi harga pokok perolehan |
| Laba/rugi manajerial per kambing | Margin kotor dikurangi beban operasional/penjualan yang ditautkan atau dialokasikan kepada kambing |
| Persentase laba manajerial | Laba manajerial ÷ penjualan bersih × 100%; penjualan nol menampilkan tidak tersedia |
| Laba/rugi periode | Penjualan bersih periode − harga pokok kambing terjual periode − seluruh beban operasional/penjualan periode − kerugian persediaan periode |
| Arus kas bersih | Penerimaan kas/bank eksternal − pengeluaran kas/bank eksternal selama periode |
| Saldo akhir | Saldo awal + pergerakan kas/bank periode |
| Nilai persediaan kambing | Total harga pokok perolehan kambing aktif yang sudah diterima |

**Kebijakan awal yang dipilih:** beban pakan dan pemeliharaan dibebankan pada periode terjadinya, tidak dikapitalisasi ke persediaan kambing. Alokasi ke kambing digunakan untuk analisis keuntungan sepanjang pemeliharaan. Karena rentang waktunya berbeda, jumlah laba manajerial kambing terjual pada satu bulan tidak harus sama dengan laba rugi usaha bulan itu. Laporan wajib menampilkan penjelasan ini dan tabel rekonsiliasi.

Penyebutan laba di dashboard selalu menyertakan jenis metrik dan periode. Laba rugi periode versi pertama belum memasukkan pajak otomatis serta penyusutan aset, sehingga jangan diberi label laba bersih setelah pajak.

### 6.2 Contoh per kambing

| Komponen K-001 | Nominal |
| --- | ---: |
| Penjualan bersih | Rp3.500.000 |
| Harga beli bersih | Rp2.400.000 |
| Pengiriman pembelian teralokasi | Rp100.000 |
| Harga pokok perolehan | Rp2.500.000 |
| Margin kotor | Rp1.000.000 |
| Beban pakan teralokasi | Rp250.000 |
| Pengobatan langsung | Rp100.000 |
| Beban penjualan langsung | Rp50.000 |
| **Laba manajerial** | **Rp600.000** |
| **Persentase laba manajerial** | **17,14%** |

Angka beban Rp400.000 dalam contoh adalah referensi ke beban sumber, bukan pengeluaran baru. Pelunasan piutang kemudian tidak menambah laba lagi.

### 6.3 Metode alokasi bersama

- Pengiriman pembelian: total biaya dibagi jumlah kambing dalam penerimaan terkait.
- Diskon penjualan dokumen: dibagi proporsional nilai bruto item sebelum diskon dokumen; total diskon tidak boleh melebihi total yang dapat didiskon.
- Pakan kandang: nilai pemakaian dibagi menurut hari-keberadaan kambing pada lingkup kandang dan rentang layanan biaya.
- Beban bersama peternakan: metode hari-keberadaan serupa pada seluruh kambing aktif dalam rentang biaya; hanya biaya yang ditandai owner sebagai layak dialokasikan.
- Hari-keberadaan dihitung berdasarkan tanggal bisnis. Tanggal masuk dan tanggal keluar dihitung inklusif; kambing yang hadir dan dijual pada hari sama memperoleh satu hari. Untuk perpindahan pada hari sama, tanggal tersebut hanya dihitung pada kandang tujuan terakhir agar tidak dihitung dua kali. Aturan ini adalah pendekatan harian, bukan pengukuran jam.
- Contoh pakan Rp300.000: A hadir 10 hari dan B 20 hari, sehingga alokasi A Rp100.000 dan B Rp200.000.
- Jika penyebut nol, alokasi ditolak dan masuk daftar perlu diperiksa; biaya sumber tetap tercatat satu kali.
- Pembulatan nominal final ke rupiah dengan HALF_UP. Sisa pembulatan dibagikan ke kode kambing terkecil secara berurutan agar jumlah alokasi persis sama dengan sumber.
- Satu sumber biaya hanya memiliki satu versi alokasi aktif; revisi membalik versi lama sebelum memposting versi baru. Beban langsung tidak boleh masuk pool bersama lagi.

### 6.4 Keuntungan sementara dan final

Ketika penjualan dikonfirmasi, laporan kambing berstatus SEMENTARA jika biaya sampai tanggal penjualan belum lengkap atau belum dialokasikan. Owner memfinalisasi setelah semua biaya yang diketahui diperiksa. Simpan snapshot hasil, sumber biaya, versi aturan, dan waktu finalisasi. Koreksi kemudian membuat versi baru; hasil lama tetap tersedia dalam audit. Finalisasi kambing tidak otomatis menutup periode.

### 6.5 Rekonsiliasi dan kerugian

- Jumlah alokasi setiap sumber harus sama dengan nilai sumbernya.
- Rekap periode menggunakan beban sumber, bukan menjumlahkan kembali tabel alokasi.
- Sediakan rekonsiliasi margin kotor periode ke laba rugi periode melalui beban dan kerugian periode. Sediakan pula penjelasan beda waktu biaya untuk laba manajerial kambing terjual, termasuk biaya periode sebelumnya, biaya periode berjalan pada kambing belum terjual, dan biaya tidak dialokasikan.
- Kambing mati dihapus dari nilai persediaan melalui dokumen penghapusan; nilai tercatat menjadi kerugian periode. Biaya pemeliharaan yang sudah menjadi beban tidak diakui ulang. Tetap sediakan laporan total biaya analitis kambing mati, terpisah dari laba kambing terjual.
- Pembelian kambing yang belum terjual tidak langsung seluruhnya menjadi beban. Pembelian pakan belum dipakai juga masih persediaan.

## 7. Alur kerja utama dan penanganan pengecualian

### 7.1 Pembelian sampai tersedia

Admin memilih supplier → membuat pembelian → mengonfirmasi pesanan → mencatat penerimaan aktual → memberi identitas kambing → memilih kandang → sistem memposting persediaan, tagihan/uang muka, serta audit. Kambing masuk karantina dan belum dapat dijual sampai dilepas oleh pengguna berizin.

### 7.2 Penjualan dan pembayaran

Admin memilih pelanggan → memilih kambing siap jual → menetapkan harga/berat/diskon → meninjau total → meminta persetujuan bila diperlukan → mengonfirmasi serah terima → sistem mengubah status, membentuk piutang, HPP, dan laba sementara → admin mencatat pembayaran → sistem menerbitkan kuitansi. Pembayaran awal boleh parsial dan tidak menentukan waktu pengakuan laba.

### 7.3 Pembatalan dan retur

Draft boleh dihapus dengan audit. Transaksi terkonfirmasi tidak dapat dihapus atau diedit langsung. Pembatalan setelah konfirmasi merupakan pembalikan bisnis oleh owner: referensi dokumen asal, alasan, item, dampak stok, tagihan, dan kas ditampilkan sebelum eksekusi.

Versi pertama mendukung pembalikan satu dokumen penuh; retur sebagian ditunda. Penjualan yang sudah dibayar harus memiliki refund tertaut atau penyelesaian terdokumentasi, bukan menghapus pembayaran. Kambing yang kembali wajib diperiksa dan masuk karantina. Pembatalan penerimaan ditolak jika kambing sudah dijual, mati, atau mempunyai pergerakan lanjutan yang belum diselesaikan. Tanggal pembalikan menggunakan periode terbuka dan tidak mengubah laporan periode tertutup diam-diam.

### 7.4 Putus koneksi dan klik berulang

Tombol konfirmasi dinonaktifkan saat permintaan berjalan. Setiap perintah posting membawa request ID unik; pengiriman ulang mengembalikan hasil yang sama dan tidak membuat dokumen kedua. Jika hasil tidak diketahui setelah koneksi putus, UI memeriksa status request terlebih dahulu dan memberi opsi muat ulang. Input draft dipertahankan di memori selama jendela masih terbuka; penyimpanan draft server dilakukan eksplisit.

## 8. Spesifikasi UI/UX

### 8.1 Arah desain

Gaya desktop modern, bersih, profesional, dengan dominasi putih dan hijau gelap sebagai identitas peternakan. Prioritas adalah keterbacaan angka, kemudahan mencari data, dan kejelasan tindakan. Gunakan FlatLaf Light sebagai dasar tampilan Swing. Tema gelap merupakan pengembangan selanjutnya.

### 8.2 Sistem visual

| Elemen | Spesifikasi awal |
| --- | --- |
| Warna utama | Hijau gelap #166534, teks tombol putih |
| Latar utama | #F8FAFC |
| Permukaan kartu/form | #FFFFFF |
| Teks utama | #0F172A |
| Teks sekunder | #475569 |
| Garis/border | #CBD5E1 |
| Berhasil | #166534 dengan ikon dan teks |
| Peringatan | #92400E di atas #FFFBEB |
| Gagal/rugi | #B91C1C di atas #FEF2F2 |
| Informasi | #1D4ED8 di atas #EFF6FF |
| Font | Sans-serif sistem, preferensi Segoe UI; fallback SansSerif |
| Ukuran teks | Isi 14–16 px; judul 22–26 px; label minimal 13 px |
| Spasi | Kelipatan 8 px; padding halaman 24 px; jarak antarbagian 24 px |
| Input dan tombol | Tinggi target 36–40 px, fokus keyboard terlihat |
| Tabel | Header 38 px; baris 38–42 px; angka rata kanan; teks rata kiri |
| Sudut | Radius sekitar 8 px pada kartu/input jika didukung tema |
| Resolusi | Target 1366×768; tetap dapat dipakai pada 1280×720 dan skala OS 125–150% |

Warna status selalu disertai label dan ikon. Verifikasi kontras minimal 4,5:1 untuk teks normal pada pasangan warna aktual. Gunakan layout manager, hindari absolute positioning dan pengaturan koordinat tetap. Di layar kecil, formulir menjadi satu kolom dan dapat digulir tanpa menyembunyikan tombol utama.

### 8.3 Struktur navigasi

Satu JFrame utama berisi sidebar sekitar 224 px, topbar, serta area konten berbasis JPanel dan CardLayout. Sidebar dapat diperkecil. Navigasi dikelompokkan:

1. Ringkasan: Dashboard.
2. Operasional: Data Kambing, Kandang, Penimbangan, Kesehatan, Pakan.
3. Transaksi: Pembelian, Penerimaan, Penjualan, Pembayaran, Pengeluaran.
4. Relasi: Supplier, Pelanggan.
5. Laporan: Rekap Operasional; Keuangan dan Analisis Bisnis khusus owner.
6. Pengaturan: Pengguna & Role, Log Aktivitas, Kategori Biaya, Profil Usaha, Backup & Restore sesuai izin.

Topbar berisi judul halaman, breadcrumb pendek, status koneksi, nama pengguna/role, dan menu akun. Tidak ada menu non-owner yang membuka agregat keuangan. Logout menutup semua pratinjau sensitif dan membersihkan model/cached data sesi.

### 8.4 Pola halaman daftar

Bagian atas: judul dan deskripsi satu baris; tombol utama seperti Tambah Kambing atau Buat Penjualan di kanan. Di bawahnya: pencarian, filter, tombol reset, dan jumlah hasil. Tabel menyediakan sorting, pagination server, serta kolom yang relevan dengan role. Ukuran halaman awal 25 dengan pilihan 25/50/100. Pencarian memakai debounce sekitar 300 ms dan tidak memblokir UI.

Klik ganda membuka detail; aksi ubah/batal tetap berlabel jelas. Status ditampilkan sebagai badge berteks. Menu tindakan per baris tidak hanya bergantung pada ikon tanpa tooltip. Export mengekspor semua data sesuai filter dan izin, bukan sekadar halaman aktif.

### 8.5 Spesifikasi halaman penting

| Halaman | Struktur dan perilaku |
| --- | --- |
| Login | Panel ringkas, identitas usaha, username, password, tampilkan password, tombol Masuk, status koneksi; tanpa registrasi umum |
| Dashboard | Kartu ringkasan; daftar tugas/alert; aktivitas terbaru; grafik sederhana; owner mendapat area keuangan terpisah dengan filter periode |
| Daftar kambing | Kode, foto mini opsional, ras, kelamin, berat terbaru, kandang, kesehatan, status; filter supplier/kandang/status |
| Detail kambing | Header identitas dan status; informasi dasar; tab riwayat; tombol penimbangan/perawatan sesuai izin; tab finansial owner |
| Form pembelian | Tahap Supplier & Tanggal → Daftar Kambing → Biaya & Jatuh Tempo → Tinjau; total selalu terlihat |
| Form penerimaan | Pilih pembelian; tampilkan sisa belum diterima; input kode, kondisi, berat, dan kandang tiap kambing; cek kapasitas sebelum simpan |
| Form penjualan | Pelanggan → Pilih Kambing → Harga & Diskon → Tinjau; daftar terpilih dan total; tampilkan notifikasi konflik stok bila terjadi |
| Form pembayaran | Dokumen, total tagihan, sudah dibayar, sisa, jumlah bayar, akun/metode, tanggal, referensi; batas maksimum jelas |
| Biaya dan alokasi | Sumber biaya, kategori, tujuan, tanggal/rentang layanan; pratinjau pembagian; status belum teralokasi; hanya owner memfinalisasi |
| Laba per kambing | Kode, tanggal jual, pendapatan bersih, HPP, beban langsung/bersama, laba, margin, sementara/final; klik membuka sumber |
| Keuangan periode | Filter bulan/rentang, kartu total, tabel pendapatan/HPP/beban, rekonsiliasi, tautan dokumen; cetak dan ekspor khusus owner |
| Log aktivitas | Waktu, pengguna, aksi, dokumen; panel detail sebelum/sesudah dan alasan; pencarian dan filter |

### 8.6 Form, validasi, dan umpan balik

- Label di atas input; field wajib ditandai dan diberi penjelasan tekstual.
- Gunakan pemilih tanggal, dropdown yang dapat dicari, dan input numerik yang memahami format Indonesia.
- Tampilkan Rp1.250.000 dan berat 35,50 kg; nilai dikirim sebagai angka desimal terstruktur, bukan string hasil format tampilan.
- Validasi saat pindah field dan saat submit. Kesalahan tampil di dekat field; fokus pindah ke field salah pertama.
- Contoh: “Berat harus lebih dari 0 kg”, “Kode kambing sudah digunakan”, “Pembayaran melebihi sisa tagihan Rp500.000”.
- Tombol dibedakan: Simpan Draft, Konfirmasi Penerimaan, Konfirmasi Penjualan, atau Catat Pembayaran. Hindari satu tombol Simpan untuk semua konsekuensi.
- Sebelum posting, tampilkan ringkasan dampak: jumlah kambing, total transaksi, uang muka, sisa tagihan, serta perubahan status.
- Pesan sukses ringkas dengan nomor dokumen dan tindakan Lihat Detail/Cetak bila relevan.
- Permintaan panjang menampilkan indikator proses. Tidak ada modal berulang untuk keberhasilan rutin.
- Menutup formulir dengan perubahan yang belum disimpan memunculkan Simpan Draft, Buang Perubahan, dan Kembali.
- Operasi destruktif menampilkan nama/nomor objek dan konsekuensi yang konkret.

### 8.7 Status kosong, loading, dan gagal

Data kosong awal menampilkan penjelasan dan tombol buat data sesuai izin. Hasil filter kosong menawarkan reset filter. Loading menampilkan indikator dan mempertahankan ukuran area. Koneksi gagal menampilkan Coba Lagi dan waktu kegagalan; data lama ditandai belum diperbarui. Grafik tidak menggunakan data dummy ketika belum ada data.

### 8.8 Aksesibilitas dan keyboard

Urutan Tab mengikuti form; Enter hanya mengeksekusi aksi ketika konteks aman; Esc menutup dialog dengan pemeriksaan perubahan. Sediakan shortcut pencarian dan simpan draft dengan label di menu. Gunakan accessible name/description untuk kontrol penting. Jangan mengandalkan warna saja atau tooltip sebagai satu-satunya penjelasan.

## 9. Arsitektur dan teknologi

### 9.1 Komponen

| Lapisan | Pilihan dan tanggung jawab |
| --- | --- |
| Desktop | Java Swing, NetBeans, Maven; UI, input, navigasi, pratinjau |
| Tema | FlatLaf Light; komponen/form konsisten |
| Layanan aplikasi | Layanan Java internal; autentikasi, permission, aturan bisnis, transaksi, kalkulasi, laporan |
| Akses data | JDBC dan MySQL Connector/J pada layanan; prepared statement dan transaksi |
| Database | MySQL dengan InnoDB, foreign key, indeks, constraint, dan migrasi skema |
| Laporan | JasperReports wajib untuk pembuatan PDF dan template .jrxml; CSV diekspor dari dataset terotorisasi yang sama |
| Grafik | Library chart Swing yang kompatibel; dapat menggunakan JFreeChart setelah verifikasi dependensi |
| Distribusi | Paket aplikasi desktop beserta runtime yang sesuai, layanan Java, skrip migrasi, dan panduan instalasi |

Baseline usulan: JDK 21 dan MySQL 8.4; gunakan rilis patch yang didukung pada saat implementasi. Versi NetBeans dan seluruh library dikunci setelah uji kompatibilitas build, GUI Builder, laporan, serta packaging. Hindari mengambil versi terbaru secara otomatis dalam build.

### 9.2 Penegakan akses khusus owner

Arsitektur yang dipilih: desktop Swing berkomunikasi ke layanan aplikasi Java melalui API internal; layanan yang mengakses MySQL. Pada satu komputer, layanan dapat berjalan di localhost; pada LAN ditempatkan pada komputer server. Tidak diperlukan hosting internet.

Keputusan ini merupakan bagian rancangan PRD agar pemeriksaan akses keuangan tidak hanya berada pada UI. Kredensial MySQL berprivilege tidak dibundel ke semua klien Swing. API memeriksa sesi dan permission pada setiap operasi, termasuk laporan, ekspor, dan detail dokumen. Hanya layanan yang mendapat koneksi database bisnis. Di LAN gunakan TLS dan pembatasan jaringan.

Pemilik hak administrator OS/database tetap memiliki kontrol infrastruktur; role aplikasi tidak dirancang untuk membatasi administrator infrastruktur. Deploy dan file konfigurasi server perlu dikelola owner atau pihak tepercaya.

### 9.3 Struktur proyek yang disarankan

Maven multi-module: `desktop-ui`, `application-service`, `domain`, dan `reporting`. Kode GUI berada di modul desktop, aturan bisnis berada di service/domain, akses data berada di repository layanan. GUI Builder boleh digunakan untuk panel/form, tetapi perhitungan dan query tidak ditanam dalam event tombol.

Pemanggilan API, pembuatan laporan, dan proses berat dijalankan melalui SwingWorker atau executor; pembaruan komponen Swing pada Event Dispatch Thread. Gunakan connection pool pada layanan, try-with-resources, migrasi bernomor, dan pengujian database nyata terisolasi.

### 9.4 Pembuatan laporan PDF dengan JasperReports

**Keputusan wajib:** seluruh laporan PDF, invoice, dan kuitansi menggunakan JasperReports. Template disimpan sebagai file `.jrxml` dalam modul `reporting` dan dikelola bersama source code. Versi JasperReports dan dependensi eksportir PDF dikunci setelah uji kompatibilitas dengan JDK dan template; seluruh dependensi wajib disertakan dalam distribusi.

- Desain template dapat menggunakan Jaspersoft Studio; aplikasi utama tetap Java Swing dan NetBeans.
- Kompilasi template menjadi `.jasper` dilakukan pada proses build/deployment terkontrol. Pengguna tidak dapat memasukkan template, SQL, ekspresi, atau path bebas.
- Layanan Java memeriksa sesi, permission, dan filter, mengambil dataset terotorisasi dari MySQL, mengisi laporan melalui JasperReports, lalu menghasilkan PDF. Kredensial database tidak diberikan ke klien Swing atau template di klien.
- Pratinjau Swing menampilkan PDF dari layanan. Cetak dan simpan menggunakan dokumen PDF yang sama agar hasil konsisten.
- Proses laporan tidak memblokir UI; tampilkan indikator proses dan pesan kegagalan yang jelas.
- PDF keuangan hanya dibuat dan dikirim untuk owner. File sementara sensitif dan cache dibersihkan setelah selesai atau logout. Non-owner hanya dapat membuat invoice/kuitansi transaksi sesuai izin tanpa rincian laba atau harga pokok.
- Template mencantumkan identitas/logo usaha, judul, periode/filter, nomor dokumen bila relevan, rincian, subtotal/total, nomor halaman, waktu pembuatan, dan pencetak.
- Gunakan A4 portrait untuk invoice, kuitansi, dan laporan ringkas; A4 landscape untuk tabel lebar. Header tabel berulang pada halaman berikutnya, angka rata kanan, dan total tidak terpotong.
- Font tersedia atau disertakan melalui konfigurasi font yang sesuai lisensinya agar hasil konsisten pada komputer pengguna.
- PDF tanpa data tetap menampilkan identitas laporan dan pesan “Tidak ada data untuk filter yang dipilih”, tanpa data contoh.
- CSV tetap mengambil dataset dan filter yang sama; JasperReports wajib untuk keluaran PDF.

Template minimum: `invoice_penjualan.jrxml`, `kuitansi_pembayaran.jrxml`, `laporan_persediaan.jrxml`, `laba_rugi_per_kambing.jrxml`, `rekap_keuntungan_kerugian.jrxml`, `laba_rugi_periode.jrxml`, `arus_kas.jrxml`, `utang_supplier.jrxml`, dan `piutang_pelanggan.jrxml`. Laporan PDF lain dalam cakupan produk mengikuti pola yang sama.

## 10. Rancangan database MySQL konseptual

Nama tabel merupakan rancangan awal untuk diturunkan menjadi DDL. Semua tabel transaksi memiliki ID internal, nomor bisnis unik bila relevan, timestamp server, pencatat, serta referensi pembalikan jika ada. Master dapat dinonaktifkan; dokumen posted dipertahankan.

| Kelompok/tabel | Kolom inti dan tujuan |
| --- | --- |
| users | id, username unik, password_hash, role_id, active, session_version |
| roles, permissions, role_permissions | Definisi role, permission, dan pemetaan izin |
| sessions | user_id, token_hash, expires_at, revoked_at |
| activity_logs | user_id, occurred_at, action, entity, entity_id, before_json, after_json, reason, correlation_id |
| parties, party_roles | Kode, nama, kontak, alamat, status; peran SUPPLIER/CUSTOMER |
| pens | Kode unik, nama, kapasitas, status |
| goats | Kode unik, receipt_item_id unik, ras, kelamin, umur/tanggal lahir, berat awal, status persediaan/kesehatan/kelayakan jual |
| goat_photos | goat_id, attachment_id, is_primary |
| pen_placements | goat_id, pen_id, entered_at, exited_at, reason |
| weight_records | goat_id, measured_at, weight_kg, user_id, notes |
| health_records | goat_id, occurred_at, complaint, action, follow_up_date, recorder_id |
| purchases, purchase_items | Supplier, status, jatuh tempo; item rencana, harga beli, diskon |
| receipts, receipt_items | Pembelian, tanggal; purchase_item_id, goat_id, berat, kondisi, nilai perolehan |
| sales, sale_items | Pelanggan, tanggal serah terima, status; goat_id, metode harga, berat jual, tarif, diskon, pendapatan bersih, HPP snapshot |
| sale_approvals | sale_id, alasan, requested_by, approved_by, waktu, versi draft yang disetujui |
| feed_items, feed_movements | Satuan dasar, stok minimum; gerakan masuk/pemakaian, jumlah, biaya unit snapshot, tujuan |
| expense_categories, expenses | Kategori, perlakuan akuntansi, tanggal, jumlah, sumber, pihak, status |
| cost_sources | Sumber biaya tunggal bertipe; FK ke item pembelian, pengiriman, pemakaian pakan, atau expense; tepat satu sumber aktif |
| allocation_batches, cost_allocations | cost_source_id, versi/metode/rentang, status; goat_id, bobot hari, nominal |
| cash_accounts, payments | Akun kas/bank; arah, nilai, tanggal, referensi, tipe uang muka/pelunasan/refund |
| payment_allocations | payment_id dan tepat satu referensi tagihan pembelian atau penjualan; nilai teralokasi |
| account_transfers | Akun sumber/tujuan, jumlah, tanggal, referensi pasangan jurnal |
| goat_profit_snapshots, profit_snapshot_lines | goat_id, sale_item_id, versi, status, pendapatan/HPP/beban/laba; referensi sumber terinci |
| inventory_writeoffs | goat_id atau feed_item_id sesuai tipe, tanggal, nilai, alasan, owner |
| accounts, journal_entries, journal_lines | Daftar akun minimal; dokumen sumber, tanggal, status; account_id, debit, credit |
| accounting_periods | Periode mulai/akhir, status buka/tutup, closed_by, closed_at |
| opening_balances | Saldo awal kas/bank, persediaan, utang/piutang dan pasangan modal awal |
| attachments | Nama aman, tipe, ukuran, checksum, storage_key; akses melalui layanan |
| app_settings | Profil usaha, aturan harga/diskon, metode alokasi, timeout |
| processed_requests | Request ID unik, actor_id, command_type, result_reference untuk idempotensi |
| backup_history | Waktu, versi skema, lokasi aman, hasil, pencatat |

### 10.1 Relasi utama

- Supplier 1:N pembelian; pembelian 1:N item dan 1:N penerimaan.
- Item pembelian 1:0..1 kambing aktual diterima pada versi item-per-ekor.
- Kambing 1:N penempatan, berat, kesehatan, biaya analitis, serta riwayat penjualan/pembalikan.
- Pelanggan 1:N penjualan; penjualan 1:N item.
- Kambing maksimum memiliki satu penjualan efektif yang belum dibalik.
- Biaya sumber 1:N alokasi; satu batch versi aktif per biaya sumber.
- Dokumen tagihan 1:N alokasi pembayaran.
- Dokumen posted 1:N jurnal terkait dengan hubungan sumber yang unik per jenis kejadian.

### 10.2 Tipe data dan integritas

- Uang menggunakan DECIMAL(18,2); berat/jumlah DECIMAL(12,3); tarif memadai untuk perkalian desimal. Kalkulasi Java menggunakan BigDecimal, bukan double/float.
- Nominal transaksi final IDR dibulatkan ke rupiah sesuai bagian 6; nilai antara tidak dibulatkan berulang.
- Gunakan utf8mb4; timestamp teknis disimpan dengan konvensi UTC, tanggal bisnis menggunakan DATE Asia/Jakarta.
- Foreign key dan NOT NULL untuk referensi wajib. CHECK atau validasi setara untuk nominal nonnegatif, jumlah positif, dan tepat satu tipe sumber/alokasi.
- Indeks pada kode, tanggal/status, supplier/customer, goat_id, pen_id, foreign key, serta tanggal dokumen untuk laporan.
- Validasi kode unik di database, bukan hanya melalui pencarian sebelum simpan.
- Kunci baris kambing saat konfirmasi penjualan dan periksa ulang status dalam transaksi yang sama. Kunci data kapasitas saat perpindahan bersamaan.
- Gunakan version column untuk mencegah overwrite perubahan draft oleh dua pengguna.
- Nomor dokumen dibuat server secara atomik, misalnya PB-202611-000001 dan PJ-202611-000001. Nomor tidak dipakai ulang setelah pembatalan.
- Hubungan sumber jurnal, request ID, dan event posting harus unik untuk mencegah pencatatan ganda.

## 11. Buku transaksi dan jurnal minimal

Gunakan jurnal berpasangan otomatis untuk menjaga konsistensi laporan, tanpa meminta admin mengetik debit/kredit. Jumlah debit harus sama dengan kredit per dokumen. Akun minimal mencakup kas/bank, persediaan kambing, persediaan pakan, uang muka supplier, piutang, utang supplier, uang muka pelanggan, modal, penjualan, HPP, beban operasional/penjualan, dan kerugian persediaan.

| Kejadian | Debit | Kredit |
| --- | --- | --- |
| Penerimaan kambing belum dibayar | Persediaan kambing | Utang supplier |
| Pengiriman pembelian tunai | Persediaan kambing | Kas/bank |
| Pembayaran utang supplier | Utang supplier | Kas/bank |
| Uang muka supplier | Uang muka supplier | Kas/bank |
| Penggunaan uang muka supplier | Utang supplier | Uang muka supplier |
| Pembelian pakan tunai | Persediaan pakan | Kas/bank |
| Pemakaian pakan | Beban pakan | Persediaan pakan |
| Penjualan saat serah terima | Piutang pelanggan | Penjualan |
| Pengakuan HPP penjualan | HPP | Persediaan kambing |
| Pelunasan pelanggan | Kas/bank | Piutang pelanggan |
| Uang muka pelanggan | Kas/bank | Uang muka pelanggan |
| Pemakaian uang muka pelanggan | Uang muka pelanggan | Piutang pelanggan |
| Pengeluaran operasional tunai | Beban terkait | Kas/bank |
| Kambing mati | Kerugian persediaan | Persediaan kambing |
| Setoran modal | Kas/bank | Modal |
| Penarikan owner | Penarikan modal | Kas/bank |

Diskon penjualan mengurangi pendapatan; diskon pembelian mengurangi harga perolehan. Jurnal pembalikan membalik jurnal asal dengan referensi jelas. Alokasi analitis biaya tidak membuat jurnal beban kedua. Opening balance diposting sekali dengan pasangan yang seimbang dan rincian kambing/tagihan agar buku pembantu cocok dengan jurnal. Tabel ini adalah spesifikasi kebijakan manajerial produk, bukan klaim kepatuhan akuntansi formal.

## 12. Daftar laporan dan isi

| Laporan | Isi minimal | Akses |
| --- | --- | --- |
| Persediaan fisik kambing | Kode, supplier, kandang, berat terbaru, kondisi, status | Owner/admin; subset petugas |
| Penerimaan dan penjualan operasional | Nomor, tanggal, pihak, jumlah, kode kambing, status tanpa agregat nominal | Sesuai izin operasional |
| Pemeliharaan | Penimbangan, kesehatan, pakan, dan perpindahan | Sesuai izin operasional |
| Laba rugi per kambing | Identitas, supplier, beli/jual, lama pemeliharaan, penjualan bersih, HPP, rincian beban, laba, margin, versi finalisasi | Owner |
| Rekap keuntungan/kerugian | Jumlah kambing untung/rugi, total dan rata-rata laba manajerial, filter periode penjualan | Owner |
| Laba rugi periode | Penjualan, HPP, beban sumber periode, kerugian, hasil periode, rekonsiliasi | Owner |
| Arus kas dan saldo | Saldo awal, arus operasi/modal, transfer internal terpisah, saldo akhir per akun/gabungan | Owner |
| Utang dan piutang | Dokumen, pihak, jatuh tempo, pembayaran, sisa, kelompok umur tagihan | Owner |
| Nilai persediaan | Kambing aktif dan nilai perolehan; stok pakan dan nilai rata-rata | Owner |
| Analisis bisnis | Tren bulanan, sampel supplier, pertumbuhan berat, lama pemeliharaan, margin | Owner untuk angka keuangan |

Periode penjualan menggunakan tanggal serah terima, periode arus kas menggunakan tanggal pembayaran, dan periode beban menggunakan tanggal pengakuan. Usia tagihan dihitung dari tanggal jatuh tempo terhadap tanggal laporan. Laporan historis saldo menggunakan transaksi hingga tanggal laporan, bukan saldo hari ini.

## 13. Keamanan dan keandalan

- Hash password menggunakan algoritma password hashing yang sesuai seperti Argon2id atau bcrypt dengan parameter teruji; tidak menyimpan plaintext atau hash cepat tanpa salt.
- Prepared statement wajib; batas ukuran/tipe lampiran dan nama file aman; attachment keuangan dilindungi permission yang sama.
- Rahasia database disimpan pada konfigurasi layanan di luar source control; akun runtime tidak memiliki izin DDL atau administrasi database.
- Migrasi menggunakan akun terpisah. Semua koneksi non-localhost menggunakan TLS sesuai konfigurasi lingkungan.
- Pengguna klien tidak mendapat akses database langsung. Endpoint finance dan export menolak non-owner sekalipun dipanggil tanpa UI.
- Cache dibatasi per sesi dan dibersihkan saat logout. Tidak ada agregat sensitif pada pesan error atau log petugas.
- Audit penting, stok, pembayaran, jurnal, dan sumber transaksi harus commit/rollback bersama.
- Backup dijadwalkan harian setelah kegiatan; simpan minimum 7 harian dan 4 mingguan di lokasi terlindungi di luar disk utama. Jadwal ini adalah kebutuhan produk, bukan tugas yang telah dibuat.
- Restore harus mempertahankan versi skema yang cocok, menghasilkan audit, dan tidak memulihkan sebagian data tanpa lampiran.

## 14. Kebutuhan nonfungsional dan deployment

| Area | Kriteria |
| --- | --- |
| Perangkat uji baseline | Desktop/server uji CPU 4 core, RAM 8 GB, SSD, LAN stabil; catat detail aktual saat pengujian |
| Dataset uji | 10.000 kambing historis, 100.000 baris transaksi/riwayat dan 5 sesi bersamaan |
| Kinerja | Daftar/pencarian p95 ≤2 detik; posting transaksi normal p95 ≤3 detik; laporan tahunan p95 ≤10 detik pada baseline |
| Respons UI | Proses jaringan/database/laporan tidak memblokir Event Dispatch Thread |
| Ketahanan | Request ulang, klik ganda, crash sebelum commit, dan koneksi terputus tidak membuat stok/jurnal ganda |
| Pemeliharaan | Pemisahan UI/service/repository, migrasi skema bernomor, log error teknis dengan correlation ID |
| Instalasi | Panduan pemasangan layanan, MySQL, klien Swing, konfigurasi LAN, bootstrap owner, dan backup |
| Portabilitas | Prioritas Windows; uji platform tambahan jika akan digunakan. Dependensi font dan path tidak boleh hardcoded |
| Pemulihan | Target kehilangan data maksimal satu hari dengan backup harian; target pemulihan 4 jam setelah backup tersedia, harus divalidasi saat uji restore |

## 15. Acceptance criteria dan pengujian wajib

| ID | Skenario | Hasil yang diterima |
| --- | --- | --- |
| AC-01 | Petugas login dan membuka daftar kambing | Hanya data operasional tampil; tidak ada harga, biaya, laba, atau saldo |
| AC-02 | Admin/petugas memanggil endpoint laporan/ekspor keuangan langsung | Ditolak oleh layanan; tidak mengirim field sensitif |
| AC-03 | Owner mengubah role saat sesi pengguna aktif | Akses berikutnya mengikuti izin baru dan sesi dicabut sesuai aturan |
| AC-04 | Pembelian 5 ekor diterima 3 lalu 2 | Lima kode unik terbentuk, status parsial lalu selesai, tagihan sesuai penerimaan |
| AC-05 | Dua pengguna menjual kambing sama bersamaan | Hanya satu konfirmasi berhasil; stok, piutang, jurnal lain tetap konsisten |
| AC-06 | Penjualan 35,5 kg × Rp100.000, diskon Rp50.000 | Pendapatan bersih Rp3.500.000 dan snapshot berat/tarif tersimpan |
| AC-07 | Contoh K-001 pada bagian 6.2 | HPP Rp2.500.000, laba manajerial Rp600.000, persentase 17,14% |
| AC-08 | Penjualan kredit dan pelunasan di bulan berbeda | Laba diakui saat serah terima, kas saat pembayaran; tidak ada laba ganda |
| AC-09 | Pembayaran melebihi tagihan | Ditolak tanpa perubahan saldo, pembayaran, atau jurnal |
| AC-10 | Pembelian kambing belum terjual | Tetap persediaan, tidak seluruhnya menjadi beban periode |
| AC-11 | Pakan masuk Rp500.000 lalu terpakai Rp200.000 | Persediaan tersisa Rp300.000, beban hanya Rp200.000; alokasi tidak menambah beban lagi |
| AC-12 | Biaya Rp300.000 dengan bobot 10 dan 20 hari | Alokasi Rp100.000 dan Rp200.000; total sama persis sumber |
| AC-13 | Alokasi nominal tidak habis dibagi dan revisi batch | Sisa dibagikan sesuai aturan; hanya satu versi aktif; total tidak ganda |
| AC-14 | Kambing pindah kandang dan dijual pada hari sama | Bobot harian tidak terhitung ganda dan tidak bertambah setelah keluar |
| AC-15 | Penjualan dengan uang muka | Kas tercatat sekali; uang muka menutup sebagian piutang saat serah terima |
| AC-16 | Kambing mati | Kapasitas dilepas, persediaan berkurang, kerugian tercatat sekali, tidak dapat dijual |
| AC-17 | Koreksi biaya setelah laba final | Terbentuk revisi dengan alasan dan sumber; snapshot lama tidak hilang |
| AC-18 | Posting transaksi ke periode tertutup | Ditolak; pembukaan kembali hanya owner dan diaudit |
| AC-19 | Penjualan terkonfirmasi dibatalkan | Stok/tagihan/jurnal dibalik dengan dokumen referensi; refund diperiksa; kambing kembali karantina |
| AC-20 | Klik konfirmasi berulang atau timeout lalu retry | Request ID mengembalikan dokumen sama, tanpa transaksi ganda |
| AC-21 | Daftar laporan dipaginasi dan diekspor | Total dan file mengikuti seluruh filter; angka PDF/CSV sama dengan laporan |
| AC-22 | Saldo awal, setoran modal, dan transfer antarbank | Saldo cocok, laba tidak berubah, transfer gabungan bukan arus kas eksternal |
| AC-23 | Ubah harga transaksi atau role | Nilai sebelum/sesudah, pengguna, waktu, dan alasan tersedia di audit |
| AC-24 | Restore ke lingkungan uji bersih | Data, lampiran, saldo, hak akses, dan laporan cocok dengan backup |
| AC-25 | UI pada 1280×720 dan skala 150% | Tidak ada tombol utama terpotong; form dapat digulir dan dipakai lewat keyboard |
| AC-26 | Beban bulan sebelumnya dialokasikan ke kambing yang terjual bulan ini | Laba manajerial lifecycle dan laba periode berbeda secara benar, dilengkapi rekonsiliasi |
| AC-27 | PDF JasperReports multi-halaman | Template .jrxml berhasil dibangun; pratinjau/unduhan sama; header, nomor halaman, font, total, dan rupiah benar; akses non-owner ke PDF keuangan ditolak |

Pengujian minimum mencakup unit test perhitungan uang/alokasi, integration test transaksi dan constraint MySQL, pengujian permission API, konkurensi posting, serta UAT alur owner/admin/petugas. Data contoh dan fixture menggunakan data fiktif.

## 16. Tahapan implementasi dan hasil kerja

### Tahap 1 — Fondasi, desain, dan akses

Hasil: struktur proyek, migrasi database awal, layanan autentikasi, bootstrap owner, role/permission, audit dasar, shell Swing, sistem visual, serta desain halaman utama. Selesaikan alur UI login, daftar/detail kambing, dan form transaksi sebelum memperbanyak halaman.

### Tahap 2 — Supplier, pembelian, penerimaan, dan kambing

Hasil: pihak supplier/pelanggan, kandang, pembelian parsial, penerimaan, identitas dan foto kambing, status dan karantina. Uji ketertelusuran dan kapasitas kandang.

### Tahap 3 — Pemeliharaan dan biaya

Hasil: penimbangan, kesehatan, pakan dan nilainya, pengeluaran, sumber biaya, alokasi analitis, kematian/penghapusan. Uji agar biaya tidak dihitung dua kali.

### Tahap 4 — Penjualan, pembayaran, dan jurnal

Hasil: harga per ekor/kg, persetujuan diskon, penjualan atomik, uang muka, utang/piutang, pembayaran, invoice/kuitansi, jurnal otomatis, pembalikan. Uji konkurensi dan idempotensi.

### Tahap 5 — Laporan dan analisis owner

Hasil: dashboard sesuai role, laba per kambing sementara/final, laba periode, arus kas, saldo historis, nilai persediaan, rekonsiliasi, template JasperReports untuk PDF, ekspor CSV, dan grafik bisnis. Uji penolakan akses non-owner pada seluruh jalur.

### Tahap 6 — Penyelesaian dan rilis

Hasil: tutup periode, backup/restore teruji, pengujian UI berbagai skala, UAT, installer, panduan penggunaan dan deployment, serta dataset demonstrasi. Jangan menganggap tahap selesai hanya karena tampilan menu sudah tersedia.

Semua tahap termasuk cakupan versi 1.1. Pemecahan tahap adalah urutan pengerjaan; pengurangan cakupan harus dinyatakan eksplisit dan tidak boleh diam-diam menghilangkan laporan owner, akurasi biaya, atau pembatasan akses.

## 17. Definition of Done

- Seluruh modul cakupan versi 1.1 bekerja end-to-end dengan database MySQL nyata.
- Semua acceptance criteria yang berlaku lulus dan bukti pengujian tersedia.
- Perhitungan laba per kambing, laba periode, kas, utang/piutang, persediaan, dan jurnal dapat direkonsiliasi.
- Hak akses owner diuji dari UI dan permintaan langsung ke layanan.
- Tidak ada data dummy yang menyamar sebagai data transaksi atau grafik produksi.
- Tidak ada password/kredensial di source, installer klien, atau log.
- Migrasi, seed minimal, build, paket distribusi, serta panduan tersedia.
- UI ditinjau secara visual dan digunakan dalam simulasi pekerjaan nyata.
- Backup berhasil dipulihkan ke lingkungan uji dan hasilnya diverifikasi.
- Owner dapat menelusuri sebuah angka laba hingga transaksi serta biaya sumbernya.
- Seluruh PDF dibuat menggunakan JasperReports, template .jrxml tersedia, dan hasil multi-halaman diperiksa secara visual.

## 18. Risiko dan keputusan operasional awal

| Risiko/keputusan | Penanganan versi pertama |
| --- | --- |
| Pakan tidak ditimbang per kambing | Gunakan pemakaian kandang dan alokasi hari-keberadaan; labeli sebagai alokasi |
| Biaya belum lengkap saat kambing dijual | Tampilkan laba sementara sampai finalisasi owner |
| Keuntungan lifecycle berbeda dengan laba bulan | Bedakan judul, tanggal dasar, dan rekonsiliasi pada laporan |
| Harga transaksi perlu dilihat admin | Batasi pada tugas transaksi; agregat dan margin hanya owner |
| Koneksi LAN terputus | Gagal dengan jelas, pertahankan form, periksa request ID sebelum retry |
| Pemilik menghendaki akses keuangan kuat | Layanan Java menjadi otoritas akses; MySQL tidak dibuka langsung ke klien |
| Ruang lingkup membesar | Tunda multi-cabang, pembibitan, RFID, retur sebagian, pajak, dan prediksi |

Keputusan default: role Owner/Admin/Petugas; harga per ekor dan per kg; biaya perolehan melekat pada kambing; beban pemeliharaan diakui per periode dan dialokasikan untuk analisis; pemakaian pakan bernilai rata-rata tertimbang; laporan PDF melalui JasperReports dan ekspor CSV; satu peternakan; pembalikan dokumen penuh; layanan Java internal untuk keamanan akses. Keputusan dapat direvisi sebelum implementasi, tetapi perubahan harus memperbarui aturan bisnis, database, laporan, dan pengujian terkait.

## 19. Referensi teknis

Referensi berikut mendukung pilihan teknis; rancangan fitur, UI, dan kebijakan manajerial merupakan usulan produk dalam PRD ini.

1. FlatLaf — tampilan modern untuk Java Swing: https://www.formdev.com/flatlaf/
2. Oracle — SwingWorker dan aturan thread Swing: https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/SwingWorker.html
3. MySQL — tipe DECIMAL untuk nilai eksak: https://dev.mysql.com/doc/refman/8.4/en/fixed-point-types.html
4. MySQL — model transaksi InnoDB: https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-model.html

---
**Acuan implementasi:** bangun aplikasi Java Swing di NetBeans, gunakan MySQL sebagai database, utamakan alur pembelian dari supplier sampai penjualan, hitung laba dari data sumber, dan batasi seluruh laporan keuangan pada owner. Implementasi UI harus mengikuti spesifikasi visual dan perilaku, bukan hanya menyediakan form CRUD.
