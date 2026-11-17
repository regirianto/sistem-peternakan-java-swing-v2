# Ringkasan Proyek — Sistem Manajemen Peternakan Kambing

Dokumen ini menjelaskan proyek secara umum: tujuan, manfaat, fitur, peran pengguna, alur kerja, laporan, dan batasan sistem. Penjelasan dibuat untuk pembaca non-teknis.

- **Nama sistem:** Sistem Manajemen Peternakan Kambing
- **Versi:** 1.1
- **Jenis aplikasi:** Aplikasi desktop untuk peternakan kambing
- **Pengguna utama:** Owner, Admin, dan Petugas
- **Bahasa:** Indonesia
- **Mata uang:** Rupiah
- **Acuan:** PRD Sistem Peternakan Kambing v1.1

---

## 1. Tentang sistem

Sistem Manajemen Peternakan Kambing adalah aplikasi yang membantu peternakan mengelola kambing dari saat dibeli sampai dijual atau mati, sekaligus mencatat seluruh uang yang masuk dan keluar.

Setiap kambing memiliki identitas unik. Dari identitas tersebut, pengguna dapat mengetahui:

- Dari supplier mana kambing berasal.
- Kapan kambing diterima.
- Berapa berat awal dan riwayat penimbangannya.
- Di kandang mana kambing ditempatkan.
- Bagaimana riwayat kesehatannya.
- Berapa biaya yang sudah dikeluarkan untuk kambing tersebut.
- Kepada siapa kambing dijual.
- Berapa harga jual dan pembayarannya.
- Berapa laba atau rugi dari kambing tersebut.

Sistem juga membantu memisahkan informasi operasional dari informasi keuangan. Owner dapat melihat seluruh laporan keuangan, sedangkan Admin dan Petugas hanya melihat informasi yang sesuai dengan tugasnya.

---

## 2. Siapa yang menggunakan sistem

### Owner

Pemilik atau pengelola utama usaha.

Tugas utama:

- Memantau kondisi peternakan dan keuangan.
- Melihat laba rugi per kambing dan per periode.
- Menyetujui penjualan di bawah harga pokok.
- Memfinalisasi perhitungan biaya.
- Menutup dan membuka periode akuntansi.
- Mengelola pengguna dan hak akses.
- Melihat riwayat aktivitas.
- Melakukan backup data.

### Admin

Pengguna yang menangani transaksi dan operasional harian.

Tugas utama:

- Mengelola supplier dan pelanggan.
- Membuat pembelian dan penerimaan kambing.
- Membuat penjualan.
- Mencatat pembayaran.
- Mencatat pengeluaran.
- Mengelola data kandang dan kambing.
- Mencetak invoice dan kuitansi sesuai izin.

Admin tidak dapat melihat laporan laba rugi, saldo total, utang/piutang agregat, atau laporan keuangan khusus owner.

### Petugas

Pengguna yang menangani kegiatan harian di kandang.

Tugas utama:

- Melihat identitas dan lokasi kambing.
- Mencatat penimbangan.
- Mencatat kondisi kesehatan.
- Mencatat pemakaian pakan.
- Mencatat perpindahan kandang sesuai izin.

Petugas tidak dapat melihat harga beli, harga jual, atau rincian laba.

---

## 3. Masalah yang diselesaikan

Tanpa sistem, pencatatan peternakan sering menghadapi masalah berikut:

- Data kambing tersebar di buku, chat, atau file terpisah.
- Sulit mengetahui asal dan riwayat seekor kambing.
- Biaya pakan, obat, sewa, dan operasional tidak terhubung ke kambing tertentu.
- Laba hanya dihitung dari selisih harga beli dan harga jual.
- Pembayaran atau penjualan bisa tercatat dua kali.
- Sulit mengetahui siapa yang mengubah data.
- Informasi keuangan terbuka untuk semua orang.
- Laporan bulanan harus dibuat manual.

Sistem ini menjawab masalah tersebut dengan menyatukan data kambing, transaksi, biaya, pembayaran, dan laporan dalam satu tempat.

---

## 4. Manfaat utama

- **Ketertelusuran penuh:** setiap kambing memiliki riwayat lengkap.
- **Pencatatan lebih rapi:** pembelian, penjualan, pembayaran, dan biaya saling terhubung.
- **Laba lebih akurat:** laba dihitung dari pendapatan dikurangi harga pokok, biaya langsung, dan biaya bersama.
- **Mengurangi kesalahan:** transaksi penting tidak mudah tercatat ganda.
- **Akses lebih aman:** informasi keuangan hanya untuk owner.
- **Laporan lebih cepat:** laporan PDF dan CSV dapat dibuat kapan saja.
- **Mudah diaudit:** perubahan data tercatat beserta pengguna dan waktunya.
- **Data lebih aman:** tersedia backup dan pemulihan data.

---

## 5. Fitur utama secara ringkas

Sistem memiliki fitur berikut:

1. Masuk aplikasi dengan akun dan kata sandi.
2. Dashboard kondisi peternakan.
3. Pengelolaan data kambing.
4. Pengelolaan kandang dan kapasitas.
5. Pengelolaan supplier dan pelanggan.
6. Pembelian kambing.
7. Penerimaan kambing secara bertahap.
8. Penjualan kambing per ekor atau per kilogram, serta penjualan daging per kilogram.
9. Persetujuan owner untuk penjualan di bawah harga pokok.
10. Pembayaran, piutang, utang, dan uang muka.
11. Pengelolaan pakan dan persediaan.
12. Pengeluaran dan alokasi biaya.
13. Perhitungan laba per kambing.
14. Laporan keuangan dan operasional.
15. Akuntansi sederhana dan tutup periode.
16. Pengguna, peran, dan hak akses.
17. Riwayat aktivitas.
18. Backup data.
19. Pengaturan profil usaha.
20. Cetak invoice dan kuitansi.
21. Cabang (Pusat dan cabang operasional) dengan data dan akses terpisah.

---

## 6. Fitur lengkap

Catatan pengisian data:

- Angka boleh ditulis dengan koma desimal (mis. `12,5` kg) atau titik ribuan (mis. `2.500.000`).
- Berat dapat diisi hingga tiga angka desimal.
- Bila angka atau tanggal tidak dikenali, aplikasi menampilkan pesan yang jelas dan data tidak tersimpan.
- Tombol yang tidak sesuai hak akses tidak ditampilkan.

### 6.1 Masuk dan keamanan akun

- Pengguna masuk menggunakan username dan kata sandi.
- Aplikasi menampilkan status koneksi ke layanan.
- Jika kata sandi salah, muncul pesan umum agar tidak membocorkan informasi.
- Pengguna dapat mengganti kata sandi.
- Owner dapat mereset kata sandi pengguna.
- Pengguna yang baru direset wajib mengganti kata sandi saat masuk berikutnya.
- Sesi otomatis berakhir setelah tidak aktif dalam jangka waktu tertentu.
- Pengguna dapat keluar dari aplikasi.
- Akun yang dinonaktifkan tidak dapat digunakan.

### 6.2 Dashboard

Dashboard menampilkan ringkasan kondisi peternakan:

- Jumlah kambing aktif.
- Jumlah kambing siap jual.
- Jumlah kambing karantina.
- Jumlah kambing sakit.
- Jumlah kambing terjual.
- Jumlah kambing mati.
- Jumlah pembelian yang masih draft.
- Jumlah penjualan yang masih draft.
- Jumlah transaksi yang menunggu persetujuan owner.
- Daftar kandang yang hampir penuh.
- Daftar pakan yang stoknya menipis.

Dashboard mengikuti cabang yang dipilih. Owner dapat memilih **Semua Cabang** untuk melihat gabungan.

Khusus owner, dashboard juga menampilkan:

- Pendapatan.
- Harga pokok penjualan.
- Pengeluaran.
- Nilai pakan terpakai.
- Hasil periode.
- Total piutang.
- Total utang.

Dashboard dapat difilter berdasarkan periode tertentu.

### 6.3 Data kambing

Setiap kambing memiliki data berikut:

- Kode unik kambing.
- Supplier asal.
- Jenis ternak (Kambing/Domba).
- Jenis kelamin.
- Tanggal masuk.
- Berat masuk.
- Kandang saat ini.
- Status persediaan.
- Status kesehatan.
- Status kelayakan jual.
- Foto.
- Catatan dan ciri fisik.

Status kambing:

- **Aktif:** masih dipelihara.
- **Terjual:** sudah dijual.
- **Mati:** sudah mati.
- **Dikembalikan ke supplier:** dikembalikan.

Status kesehatan:

- **Sehat.**
- **Sakit.**
- **Pemulihan.**

Status kelayakan jual:

- **Karantina:** belum boleh dijual.
- **Siap jual:** boleh dijual.

Fitur kambing:

- Melihat daftar kambing.
- Mencari kambing berdasarkan kode atau informasi lain.
- Melihat detail kambing.
- Melihat riwayat penimbangan.
- Melihat riwayat kesehatan.
- Melihat riwayat perpindahan kandang.
- Mencatat penimbangan baru.
- Mencatat keluhan, tindakan, obat, dan biaya kesehatan.
- Memindahkan kambing ke kandang lain.
- Menandai kambing siap jual.
- Menandai kambing karantina.
- Mencatat kematian kambing beserta alasan.
- Melihat riwayat penjualan kambing.
- Melihat rincian biaya dan laba kambing khusus owner.
- Melihat laba sementara dan laba final.
- Memfinalisasi laba kambing.

Kambing yang sudah terjual atau mati tetap menyimpan riwayat dan tidak dihapus.

### 6.4 Kandang

- Menambah kandang baru.
- Mengubah data kandang.
- Mengatur kode, nama, kapasitas, status, dan catatan kandang.
- Melihat jumlah kambing yang sedang menempati kandang.
- Melihat daftar dan detail kambing yang ada di setiap kandang.
- Mencatat kambing masuk dan keluar kandang.
- Mencegah kandang melebihi kapasitas.
- Mengetahui kandang yang hampir penuh.
- Satu kambing hanya berada di satu kandang aktif pada satu waktu.

### 6.5 Supplier dan pelanggan

- Menambah supplier.
- Menambah pelanggan.
- Mengubah data supplier dan pelanggan.
- Menonaktifkan pihak yang sudah tidak digunakan.
- Menyimpan kode, nama, telepon, alamat, dan catatan.
- Melihat riwayat pembelian dari supplier.
- Melihat riwayat penjualan ke pelanggan.
- Satu pihak dapat menjadi supplier sekaligus pelanggan.
- Sistem membantu menghindari data ganda.

### 6.6 Pembelian kambing

- Membuat pembelian baru.
- Memilih supplier.
- Menentukan tanggal pembelian dan jatuh tempo.
- Menambahkan beberapa kambing dalam satu pembelian.
- Menentukan harga beli per kambing.
- Menambahkan catatan.
- Menyimpan pembelian sebagai draft.
- Mengonfirmasi pembelian.
- Membatalkan pembelian.
- Menutup sisa pesanan yang belum diterima.
- Melihat status pembelian.

Alur pembelian:

```text
Draft → Dikonfirmasi → Diterima sebagian → Selesai
```

Pembelian juga mendukung:

- Penerimaan kambing secara bertahap.
- Biaya pengiriman.
- Uang muka supplier.
- Tagihan supplier.
- Pencatatan utang supplier.

### 6.7 Penerimaan kambing

- Menerima kambing dari pembelian yang sudah dikonfirmasi.
- Menerima sebagian atau seluruh pesanan.
- Memberi kode unik untuk setiap kambing.
- Mencatat berat saat diterima.
- Mencatat kandang tujuan.
- Mencatat jenis kelamin.
- Mencatat jenis ternak (Kambing/Domba).
- Mencatat perkiraan umur.
- Mencatat ciri fisik.
- Memilih status penerimaan: Sehat atau Mati.
- Mencatat tanggal penerimaan.
- Mencatat biaya pengiriman.
- Kambing mati dicatat sebagai retur dan tidak masuk persediaan/utang.
- Pembelian selesai bila jumlah diterima ditambah retur sama dengan pesanan.
- Mencegah jumlah yang diterima melebihi pesanan.
- Kambing baru masuk dalam status karantina.
- Kambing belum dapat dijual sampai ditandai siap jual.
- Setiap penerimaan tercatat dalam riwayat.

### 6.8 Penjualan kambing

- Membuat penjualan baru.
- Memilih pelanggan.
- Memilih satu atau beberapa kambing siap jual.
- Menjual per ekor.
- Menjual per kilogram.
- Menentukan berat jual aktual untuk penjualan per kilogram.
- Menentukan harga jual.
- Menyimpan penjualan sebagai draft.
- Mengonfirmasi penjualan.
- Mencetak invoice.
- Mencatat pembayaran.
- Melihat sisa tagihan pelanggan.
- Membatalkan penjualan dengan alasan, khusus owner.

Saat penjualan dikonfirmasi:

- Kambing tidak dapat dijual dua kali.
- Kambing berubah menjadi terjual.
- Kambing keluar dari kandang.
- Harga pokok dan laba dibekukan.
- Piutang pelanggan dicatat.
- Jurnal keuangan dibuat.
- Riwayat aktivitas dicatat.

Persetujuan owner:

- Penjualan di bawah harga pokok memerlukan persetujuan owner.
- Admin hanya diberi tahu bahwa transaksi memerlukan persetujuan, tanpa melihat harga pokok atau margin.

### 6.9 Pemotongan dan penjualan daging

Selain menjual kambing hidup, sistem dapat menjual daging kambing per kilogram dengan harga tetap.

Alur pemotongan:

```text
Pilih kambing siap potong
→ Catat tanggal potong
→ Catat berat hidup
→ Catat berat karkas
→ Catat berat daging dan berat tulang
→ Catat biaya potong
→ Posting pemotongan
→ Daging masuk persediaan sebagai satu batch
```

Data yang dicatat:

- Kambing yang dipotong.
- Tanggal pemotongan.
- Berat hidup.
- Berat karkas.
- Berat daging.
- Berat tulang.
- Berat jeroan (opsional).
- Biaya potong.
- Catatan.

Perhitungan otomatis:

- Persentase karkas terhadap berat hidup.
- Persentase daging terhadap karkas.
- Persentase tulang terhadap karkas.
- Harga pokok daging per kilogram.

Aturan pemotongan:

- Kambing harus aktif dan siap jual.
- Satu kambing hanya boleh memiliki satu pemotongan aktif.
- Berat daging ditambah berat tulang harus sama dengan berat karkas, dengan toleransi susut pembersihan.
- Kambing yang sudah dipotong tidak dapat dijual sebagai kambing hidup.
- Pemotongan yang sudah diposting tidak dapat dibatalkan jika dagingnya sudah terjual atau tercatat susut.
- Pembatalan pemotongan mengembalikan kambing menjadi aktif dan membalik jurnal.

Persediaan daging:

- Setiap pemotongan menghasilkan satu batch daging.
- Setiap batch memiliki sisa kilogram dan harga pokok per kilogram.
- Sisa daging dapat dijual dalam beberapa transaksi sampai habis.
- Daging yang rusak atau susut dapat dicatat sebagai susut.
- Susut mengurangi stok dan dihitung sebagai kerugian persediaan.

Penjualan daging:

- Memilih pelanggan.
- Memilih batch daging yang masih memiliki sisa.
- Menentukan jumlah kilogram.
- Harga per kilogram terisi otomatis dari harga tetap yang diatur owner.
- Dapat menambahkan beberapa batch dalam satu dokumen.
- Menyimpan sebagai draft.
- Mengonfirmasi penjualan.
- Mencetak invoice.
- Mencatat pembayaran dan piutang seperti penjualan kambing.

Aturan penjualan daging:

- Harga per kilogram hanya dapat diubah oleh owner.
- Harga disimpan pada saat transaksi sehingga perubahan harga tidak mengubah transaksi lama.
- Jumlah yang dijual tidak boleh melebihi sisa batch.
- Penjualan daging tidak dicampur dengan penjualan kambing hidup dalam satu dokumen.
- Pembatalan penjualan mengembalikan sisa daging ke batch.

### 6.10 Pembayaran

Sistem mendukung:

- Pembayaran piutang pelanggan.
- Pembayaran utang supplier.
- Uang muka dari pelanggan.
- Uang muka kepada supplier.
- Pengembalian uang muka atau refund.

Data pembayaran:

- Tanggal.
- Jumlah.
- Metode pembayaran: tunai atau transfer.
- Uang tunai masuk ke kas, transfer masuk ke bank secara otomatis.
- Nomor referensi.
- Catatan.
- Dokumen sumber.

Fitur pembayaran:

- Satu penjualan atau pembelian dapat dibayar beberapa kali.
- Pembayaran sebagian.
- Pelunasan.
- Pembayaran tidak boleh melebihi sisa tagihan.
- Daftar piutang pelanggan beserta umur tagihan.
- Daftar utang supplier beserta umur tagihan.
- Mencetak kuitansi pembayaran.
- Membatalkan pembayaran dengan alasan, khusus owner.
- Mencatat uang muka dan refund.

Pembayaran piutang atau utang tidak mengubah laba. Yang memengaruhi laba adalah penjualan dan biaya, bukan pelunasan tagihan.

### 6.11 Pakan dan persediaan

- Menambah jenis pakan.
- Menentukan kode, nama, satuan, dan stok minimum.
- Menerima stok pakan.
- Mencatat harga beli pakan.
- Mencatat pemakaian pakan.
- Menentukan pemakaian untuk kandang tertentu.
- Menentukan pemakaian untuk kambing tertentu.
- Melihat riwayat keluar-masuk pakan.
- Melihat nilai persediaan pakan.
- Mendapat peringatan saat stok menipis.
- Mencegah stok menjadi negatif.
- Menghitung nilai persediaan dengan rata-rata tertimbang.

Pemakaian pakan yang sudah dicatat tidak boleh diubah langsung. Jika ada kesalahan, harus melalui pembatalan atau koreksi yang diawasi owner.

### 6.12 Pengeluaran

Sistem dapat mencatat berbagai pengeluaran:

- Pakan dan pemeliharaan.
- Pengobatan.
- Gaji dan upah.
- Sewa.
- Listrik dan air.
- Transport penjualan.
- Komisi penjualan.
- Biaya operasional lain.
- Perpindahan aset atau modal.

Data pengeluaran:

- Tanggal.
- Kategori.
- Nilai.
- Pihak terkait.
- Tujuan.
- Catatan.
- Bukti.
- Status.

Status pengeluaran:

- **Draft:** belum diposting.
- **Posted:** sudah diposting dan memengaruhi laporan.
- **Reversed:** sudah dibatalkan.

Fitur pengeluaran:

- Membuat pengeluaran.
- Memposting pengeluaran.
- Membatalkan pengeluaran dengan alasan.
- Menautkan biaya langsung ke kambing tertentu.
- Menandai biaya sebagai biaya bersama.
- Menentukan periode layanan biaya bersama.

### 6.13 Alokasi biaya bersama

Biaya bersama adalah biaya yang digunakan untuk banyak kambing, misalnya sewa kandang, gaji, listrik, atau pakan kandang.

Sistem membagi biaya bersama berdasarkan lama kambing berada di peternakan:

- Kambing yang lebih lama dipelihara mendapat bagian lebih besar.
- Kambing yang sebentar dipelihara mendapat bagian lebih kecil.
- Total pembagian selalu sama dengan nilai biaya asli.
- Pembulatan dilakukan secara adil dan konsisten.
- Owner dapat melihat pratinjau sebelum finalisasi.
- Owner memfinalisasi alokasi.
- Setelah finalisasi, perhitungan laba kambing diperbarui.

Contoh:

Biaya pakan Rp300.000.

- Kambing A hadir 10 hari.
- Kambing B hadir 20 hari.

Maka:

- Kambing A mendapat alokasi Rp100.000.
- Kambing B mendapat alokasi Rp200.000.

### 6.14 Laba per kambing

Setiap kambing memiliki perhitungan laba sendiri.

Komponen laba:

- Pendapatan dari penjualan.
- Harga pokok perolehan.
- Biaya langsung.
- Biaya bersama yang dialokasikan.
- Laba.
- Persentase margin.

Rumus sederhana:

```text
Laba kambing =
  total harga jual
  - harga pokok perolehan
  - biaya langsung
  - biaya bersama yang dialokasikan
```

Status laba:

- **Sementara:** masih mungkin berubah karena biaya belum lengkap.
- **Final:** sudah diperiksa dan difinalisasi owner.

Owner dapat melihat rincian biaya dan alokasi yang membentuk laba kambing.

### 6.15 Laporan

Sistem menyediakan laporan berikut:

1. Laporan persediaan kambing.
2. Laba rugi per kambing.
3. Rekap keuntungan dan kerugian.
4. Laba rugi periode.
5. Laporan utang supplier.
6. Laporan piutang pelanggan.
7. Rekap operasional.
8. Invoice penjualan.
9. Kuitansi pembayaran.

Fitur laporan:

- Melihat pratinjau sebelum mencetak.
- Menyimpan laporan sebagai PDF.
- Mengekspor laporan sebagai CSV.
- Memfilter laporan berdasarkan periode.
- Memfilter laporan berdasarkan cabang.
- Mencari data tertentu pada laporan.
- Total mengikuti seluruh data yang difilter.
- Laporan mencantumkan nama usaha, judul, periode, waktu cetak, dan pencetak.
- Invoice tidak menampilkan harga beli, biaya, atau laba.
- Laporan keuangan hanya dapat diakses owner.

### 6.16 Akuntansi dan tutup periode

Sistem mencatat jurnal keuangan secara otomatis dari transaksi:

- Pembelian.
- Penerimaan.
- Penjualan.
- Pembayaran.
- Pengeluaran.
- Pakan.

Fitur akuntansi:

- Melihat daftar jurnal.
- Melihat rincian debit dan kredit.
- Melihat jurnal berdasarkan dokumen sumber.
- Memeriksa keseimbangan debit dan kredit.
- Melakukan rekonsiliasi.
- Menutup periode bulanan.
- Membuka kembali periode dengan alasan, khusus owner.

Saat periode ditutup:

- Transaksi baru untuk periode tersebut ditolak.
- Data periode menjadi terkunci.
- Laporan dapat dibuat berdasarkan periode yang sudah ditutup.

### 6.17 Pengguna dan hak akses

- Owner dapat melihat daftar pengguna.
- Owner dapat menambah pengguna.
- Owner dapat mengubah nama pengguna.
- Owner dapat mengubah peran pengguna.
- Owner dapat mengaktifkan atau menonaktifkan pengguna.
- Owner dapat mereset kata sandi pengguna.
- Owner dapat mengatur hak akses untuk Admin dan Petugas.
- Hak akses keuangan tetap hanya untuk owner.
- Perubahan hak akses membuat pengguna terkait harus masuk ulang.

### 6.18 Riwayat aktivitas

Sistem mencatat aktivitas penting, seperti:

- Login berhasil dan gagal.
- Logout.
- Perubahan akun.
- Perubahan hak akses.
- Penambahan dan perubahan data.
- Konfirmasi transaksi.
- Pembatalan transaksi.
- Pembayaran.
- Perubahan harga.
- Alokasi biaya.
- Penutupan periode.
- Pencetakan atau ekspor laporan keuangan.
- Backup dan pemulihan data.

Riwayat aktivitas menampilkan:

- Waktu.
- Pengguna.
- Modul.
- Jenis aktivitas.
- Data yang terlibat.
- Nomor dokumen.
- Alasan.
- Nilai sebelum dan sesudah perubahan.

Owner dapat mencari dan memfilter riwayat aktivitas. Riwayat tidak dapat diubah atau dihapus dari aplikasi.

### 6.19 Pengaturan usaha

Owner dapat mengatur:

- Nama usaha.
- Alamat.
- Telepon.
- Email.
- Batas waktu tidak aktif sebelum sesi berakhir.
- Batas umur sesi.
- Versi aturan alokasi.
- Folder penyimpanan lampiran.
- Folder penyimpanan backup.
- Teks footer laporan.

### 6.20 Backup dan pemulihan data

- Owner dapat membuat backup data.
- Backup disimpan sebagai file.
- Sistem mencatat riwayat backup.
- Data dapat dipulihkan dari file backup.
- Pemulihan memerlukan konfirmasi khusus.
- Pemulihan dilakukan dengan hati-hati karena akan mengganti data saat ini.

### 6.21 Perlindungan dari pencatatan ganda

Transaksi penting seperti pembayaran, penerimaan, konfirmasi penjualan, pembatalan, dan pengeluaran memiliki perlindungan khusus.

Jika pengguna tidak sengaja menekan tombol dua kali atau koneksi terputus lalu mencoba lagi:

- Dokumen tidak dibuat dua kali.
- Sistem mengembalikan hasil yang sama.
- Data tetap konsisten.

Perlindungan ini juga mencegah dua pengguna menjual kambing yang sama secara bersamaan.

### 6.22 Cabang dan akses per cabang

Sistem mendukung beberapa cabang, misalnya Pusat dan Cikarang.

- Owner dapat menambah, mengubah, atau menonaktifkan cabang.
- Setiap cabang memiliki kandang, kambing, pembelian, penerimaan, pemotongan, daging, pakan, penjualan, pembayaran, pengeluaran, dan jurnal sendiri.
- Owner dapat memilih satu cabang atau melihat semua cabang.
- Pengguna non-owner hanya dapat membuka cabang yang ditugaskan kepadanya.
- Percobaan membuka atau mengubah data cabang lain ditolak oleh layanan.
- Pengguna dapat ditugaskan ke lebih dari satu cabang.
- Data lama tetap tersimpan di cabang Pusat.
- Perpindahan kambing, pakan, atau daging antar cabang belum tersedia pada versi ini.


---

## 7. Ringkasan hak akses

| Kemampuan | Owner | Admin | Petugas |
|---|---|---|---|
| Melihat dashboard operasional | Ya | Ya | Ya |
| Melihat dan mengelola data kambing | Penuh | Penuh | Lihat dan catat operasional |
| Menimbang dan mencatat kesehatan | Penuh | Penuh | Ya |
| Mengelola kandang | Ya | Ya | Sesuai izin |
| Mengelola supplier dan pelanggan | Ya | Ya | Tidak |
| Membuat pembelian dan penerimaan | Ya | Ya | Tidak |
| Membuat dan mengonfirmasi penjualan | Ya | Sesuai izin | Tidak |
| Mencatat pembayaran | Ya | Sesuai izin | Tidak |
| Mengelola pakan | Ya | Ya | Mencatat pemakaian |
| Mencatat pemotongan kambing | Ya | Ya | Tidak |
| Melihat persediaan daging | Ya | Ya | Ya |
| Mengatur harga daging per kg | Ya | Tidak | Tidak |
| Mencatat pengeluaran | Ya | Ya | Tidak default |
| Melihat laba rugi | Ya | Tidak | Tidak |
| Melihat saldo, utang, dan piutang total | Ya | Tidak | Tidak |
| Memfinalisasi alokasi | Ya | Tidak | Tidak |
| Menutup periode | Ya | Tidak | Tidak |
| Membatalkan transaksi | Ya | Mengajukan | Tidak |
| Mengelola pengguna dan hak akses | Ya | Tidak | Tidak |
| Melihat seluruh riwayat aktivitas | Ya | Tidak | Tidak |
| Backup dan pemulihan | Ya | Tidak | Tidak |

---

## 8. Alur kerja sehari-hari

### 8.1 Dari pembelian sampai kambing siap jual

```text
Pilih supplier
→ Buat pembelian
→ Konfirmasi pembelian
→ Terima kambing
→ Beri kode dan identitas
→ Tentukan kandang
→ Catat berat dan kondisi
→ Kambing masuk karantina
→ Periksa kesehatan
→ Tandai siap jual
```

### 8.2 Dari penjualan sampai pembayaran

```text
Pilih pelanggan
→ Buat penjualan
→ Pilih kambing siap jual
→ Tentukan harga jual
→ Konfirmasi penjualan
→ Cetak invoice
→ Catat pembayaran
→ Pantau sisa piutang
→ Cetak kuitansi
```

### 8.3 Dari pemotongan sampai penjualan daging

```text
Pilih kambing siap potong
→ Catat berat hidup, karkas, daging, dan tulang
→ Catat biaya potong
→ Posting pemotongan
→ Daging masuk persediaan sebagai batch
→ Atur harga daging per kg (owner)
→ Buat penjualan daging
→ Konfirmasi penjualan
→ Catat pembayaran
→ Catat susut bila ada daging rusak
```

### 8.4 Pencatatan biaya

```text
Catat pengeluaran
→ Posting pengeluaran
→ Tentukan biaya langsung atau biaya bersama
→ Lihat pratinjau alokasi
→ Finalisasi alokasi
→ Laba kambing diperbarui
```

### 8.5 Penutupan bulan

```text
Periksa transaksi draft
→ Periksa alokasi yang belum selesai
→ Periksa saldo piutang dan utang
→ Periksa keseimbangan jurnal
→ Tutup periode
→ Buat laporan bulanan
```

---

## 9. Cara perhitungan keuangan secara sederhana

### 9.1 Harga pokok kambing

Harga pokok adalah modal yang dikeluarkan untuk mendapatkan seekor kambing.

```text
Harga pokok = harga beli + bagian biaya pengiriman
```

Contoh:

- Harga beli: Rp2.400.000.
- Bagian biaya pengiriman: Rp100.000.
- Harga pokok: Rp2.500.000.

### 9.2 Harga pokok daging

Harga pokok daging adalah modal kambing ditambah seluruh biaya pemotongan, dibagi berat daging yang dihasilkan.

```text
Harga pokok daging per kg =
  (harga pokok kambing + biaya potong)
  ÷ berat daging
```

Contoh:

- Harga pokok kambing: Rp2.500.000.
- Biaya potong: Rp150.000.
- Berat daging: 13 kg.
- Harga pokok daging per kg: sekitar Rp203.846.

Catatan:

- Seluruh modal dibebankan ke daging, karena tulang dan jeroan tidak dijual.
- Berat tulang dan jeroan tetap dicatat sebagai informasi hasil pemotongan.
- Harga pokok per kg dibekukan saat pemotongan diposting, sehingga biaya setelahnya tidak mengubah batch yang sudah ada.

### 9.3 Laba kambing

Laba kambing berlaku untuk kambing yang dijual hidup maupun kambing yang dipotong lalu dagingnya dijual.

```text
Laba kambing =
  total harga jual
  - harga pokok
  - biaya langsung
  - biaya bersama yang dialokasikan
  - nilai daging yang susut (bila dipotong)
```

Contoh kambing hidup:

- Total harga jual: Rp3.500.000.
- Harga pokok: Rp2.500.000.
- Biaya pakan, obat, dan penjualan: Rp400.000.
- Laba: Rp600.000.
- Margin: sekitar 17,14%.

Contoh kambing yang dipotong:

- Total penjualan daging: Rp2.340.000 (13 kg × Rp180.000).
- Harga pokok kambing: Rp2.500.000.
- Biaya potong: Rp150.000.
- Susut daging 1 kg: sekitar Rp203.846.
- Laba: rugi sekitar Rp513.846.

Pendapatan dan harga pokok daging dihitung per batch, lalu digabung kembali ke kambing asalnya agar laba per kambing tetap dapat dilihat.

### 9.4 Laba periode

Laba periode adalah laba seluruh usaha dalam satu periode, bukan hanya laba kambing yang terjual.

```text
Laba periode =
  penjualan bersih
  - harga pokok kambing yang terjual
  - seluruh beban operasional dan penjualan
  - kerugian persediaan
```

Laba kambing dan laba periode bisa berbeda karena waktu pencatatan biaya dan penjualan tidak selalu sama. Laporan menyediakan penjelasan untuk hal ini.

### 9.5 Nilai persediaan

Nilai persediaan kambing adalah total modal kambing yang masih aktif dan belum terjual atau dipotong.

Nilai persediaan pakan adalah nilai stok pakan yang masih tersedia.

Nilai persediaan daging adalah sisa kilogram daging dikalikan harga pokok daging per kilogram pada batch tersebut. Nilai ini berkurang saat daging terjual atau tercatat susut.

---

## 10. Daftar laporan dan kegunaannya

| Laporan | Kegunaan |
|---|---|
| Laporan Persediaan | Mengetahui kambing, pakan, dan daging yang masih dimiliki beserta nilainya |
| Laba Rugi per Kambing | Mengetahui laba atau rugi setiap kambing, termasuk yang dijual sebagai daging |
| Rekap Keuntungan dan Kerugian | Melihat ringkasan laba seluruh kambing |
| Laba Rugi Periode | Mengetahui hasil usaha dalam satu periode |
| Laporan Utang Supplier | Mengetahui tagihan yang belum dibayar |
| Laporan Piutang Pelanggan | Mengetahui tagihan pelanggan yang belum dibayar |
| Rekap Operasional | Melihat ringkasan kegiatan operasional |
| Invoice Penjualan | Dokumen penjualan untuk pelanggan |
| Kuitansi Pembayaran | Bukti pembayaran |

Laporan dapat dilihat di layar, disimpan sebagai PDF, atau diekspor sebagai CSV.

---

## 11. Keamanan dan pengendalian

Sistem memiliki pengendalian berikut:

- Setiap pengguna memiliki akun dan kata sandi.
- Kata sandi disimpan dalam bentuk aman, bukan teks biasa.
- Hak akses diperiksa oleh sistem, bukan hanya dari tampilan menu.
- Informasi keuangan hanya dapat diakses owner.
- Transaksi penting memerlukan izin khusus.
- Kambing tidak dapat dijual dua kali.
- Transaksi penting tidak mudah tercatat ganda.
- Periode yang sudah ditutup menolak transaksi mundur.
- Perubahan data dicatat dalam riwayat aktivitas.
- Riwayat aktivitas tidak dapat diubah dari aplikasi.
- Backup dapat dibuat kapan saja oleh owner.
- Pemulihan data memerlukan konfirmasi.

---

## 12. Status dan batasan saat ini

Status pengembangan:

- Seluruh fungsi inti sudah tersedia.
- Perhitungan uang, alokasi, dan laba sudah diuji.
- Laporan PDF dan CSV sudah diuji.
- Uji integrasi utama berhasil.
- Aplikasi dapat dijalankan untuk layanan dan klien desktop.
- Akun demo tersedia untuk mencoba sistem.

Batasan yang perlu diketahui:

1. Foto kambing sudah didukung di sistem, tetapi tampilan thumbnail di aplikasi belum sempurna.
2. Pengujian tampilan aplikasi masih dilakukan secara manual.
3. Beberapa alur laporan masih dapat disederhanakan.
4. Fitur kas terpisah (akun kas, transfer, dan modal) tidak tersedia; uang masuk dan keluar dicatat otomatis ke kas atau bank sesuai metode pembayaran.
5. Perpindahan kambing, pakan, atau daging antar cabang belum tersedia.
6. Pemulihan data dilakukan melalui prosedur khusus, bukan tombol biasa.
7. Pajak otomatis belum tersedia.
8. Penyusutan aset belum tersedia.
9. Integrasi timbangan, RFID, payment gateway, aplikasi mobile, dan marketplace belum tersedia.
10. Pembibitan dan perkembangbiakan belum termasuk.
11. Sinkronisasi offline antar-komputer belum tersedia.
12. Laporan bersifat manajemen internal, bukan laporan keuangan statutori atau pajak.

---

## 13. Cara mulai menggunakan

### Untuk pengguna

1. Hubungi owner untuk mendapatkan akun.
2. Masuk menggunakan username dan kata sandi.
3. Ganti kata sandi jika diminta.
4. Mulai dari dashboard untuk melihat kondisi peternakan.
5. Gunakan menu sesuai tugas dan hak akses.
6. Jika tombol atau menu tidak terlihat, berarti peran pengguna tidak memiliki akses ke fitur tersebut.

### Untuk owner

1. Pastikan data usaha sudah diisi.
2. Buat akun Admin dan Petugas.
3. Atur hak akses sesuai kebutuhan.
4. Tambahkan kandang.
5. Tambahkan supplier dan pelanggan.
6. Mulai catat pembelian dan penerimaan kambing.
7. Periksa laporan secara berkala.
8. Lakukan backup data secara rutin.
9. Tutup periode setiap bulan setelah data diperiksa.

### Untuk pemasangan aplikasi

Pemasangan dan konfigurasi teknis dijelaskan di `README.md` dan `docs/OPERATIONS.md`. Dokumen tersebut ditujukan untuk pengelola sistem atau teknisi.

---

## 14. Istilah penting

| Istilah | Arti |
|---|---|
| Kambing aktif | Kambing yang masih dipelihara dan belum terjual, dipotong, atau mati |
| Siap jual | Kambing yang sudah boleh dijual atau dipotong |
| Karantina | Kambing yang belum boleh dijual, misalnya karena sakit atau baru datang |
| Dipotong | Kambing yang sudah dipotong dan dagingnya masuk persediaan |
| Pemotongan | Kegiatan mencatat hasil potong kambing menjadi daging |
| Karkas | Daging beserta tulang setelah bagian luar dan jeroan dibuang |
| Dressing | Persentase berat karkas terhadap berat hidup |
| Batch daging | Satu hasil pemotongan yang disimpan sebagai persediaan daging |
| Susut | Daging yang hilang, rusak, atau tidak terjual sehingga menjadi kerugian |
| HPP | Harga pokok penjualan, yaitu modal kambing atau modal daging per kilogram |
| Piutang | Uang yang masih harus dibayar pelanggan |
| Utang | Uang yang masih harus dibayar ke supplier |
| Uang muka | Pembayaran sebelum transaksi selesai |
| Alokasi biaya | Pembagian biaya bersama ke kambing |
| Laba sementara | Laba yang masih dapat berubah |
| Laba final | Laba yang sudah difinalisasi owner |
| Jurnal | Catatan debit dan kredit |
| Rekonsiliasi | Pemeriksaan agar catatan keuangan seimbang |
| Periode | Rentang waktu pembukuan, biasanya satu bulan |
| Backup | Salinan data untuk berjaga-jaga |
| Restore | Memulihkan data dari backup |
| Audit trail | Riwayat siapa mengubah apa dan kapan |

---

## 15. Kesimpulan

Sistem Manajemen Peternakan Kambing membantu pemilik dan pengelola peternakan bekerja lebih rapi, terukur, dan aman. Data kambing, transaksi, biaya, pembayaran, dan laporan saling terhubung sehingga laba dapat dihitung dengan lebih akurat.

Fitur utamanya mencakup:

- Pencatatan kambing dari pembelian sampai penjualan.
- Pemantauan kandang, berat, kesehatan, dan pakan.
- Pembelian dan penjualan bertahap.
- Pembayaran, piutang, utang, dan uang muka.
- Biaya langsung dan alokasi biaya bersama.
- Laba per kambing dan laba periode.
- Laporan PDF dan CSV.
- Pengguna, peran, dan hak akses.
- Riwayat aktivitas.
- Backup data.

Dengan pembagian akses yang jelas antara Owner, Admin, dan Petugas, sistem dapat digunakan oleh beberapa pengguna dalam satu usaha peternakan tanpa membuka seluruh informasi keuangan kepada semua orang.
