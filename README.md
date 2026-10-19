# Stock dan Produksi by Akhtara.ID — APK standalone

Proyek Android (WebView) siap build. Aplikasi memuat `app/src/main/assets/index.html`
langsung dari dalam APK: tanpa hosting, tanpa browser, tanpa dependensi library.

## Cara build

**A. Android Studio (paling mudah)**
1. Ekstrak ZIP → `File > Open` → pilih folder proyek, tunggu Gradle sync.
2. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
3. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

**B. GitHub Actions (tanpa PC)**
Unggah isi folder ke repo GitHub → tab *Actions* → *Build APK* → *Run workflow* →
unduh artifact `StokProduksi-APK` (berisi APK debug dan release; keduanya langsung bisa dipasang).

**C. Command line**
`gradle assembleDebug` (butuh JDK 17 + Android SDK, Gradle 8.7).
Jika ingin memakai `./gradlew`, jalankan sekali: `gradle wrapper --gradle-version 8.7`.

## Yang ditambahkan ke aplikasi Anda

| Permintaan | Hasil |
|---|---|
| Layar pembuka cukup sekali | Splash bawaan Android 12+ dibuat polos (tanpa ikon), jadi hanya splash animasi di dalam aplikasi yang tampil: logo pop + bar tipis, ±0,7 dtk, keluar dengan efek iris. |
| Transisi slide antar tab | Ketuk tab di navigasi bawah → halaman lama bergeser keluar, halaman baru masuk dari kiri/kanan sesuai arah tab (300 ms). |
| Tanpa pop up salin/tempel | Tekan lama dimatikan di level native (WebView) dan di CSS/JS. |
| Statusbar dipaksa ikut tema | Statusbar dan navigation bar **digambar oleh aplikasi sendiri** (bukan diserahkan ke sistem), jadi selalu sama dengan warna header/latar tema, juga di Android 15+. Warna ikon (terang/gelap) menyesuaikan otomatis. |
| Color wheel di pengaturan tema | Atur › Tema tampilan: roda warna + slider kecerahan + kode HEX + mode Gelap/Terang. Satu warna dibuat menjadi seluruh palet tema, tampilan dan statusbar berubah langsung. Tema siap pakai yang lama tetap ada. |
| Warna bahan | Pemilih warna bahan (kemeja/celana) juga memakai color wheel yang sama. |

Semua fitur lain (tracking, produksi, stok, upah, pencarian, backup, riwayat, font, dll.) tidak diubah.

## Penyimpanan data
Data disimpan di WebView dan **diduplikasi ke file** `data.json` di penyimpanan internal aplikasi
(fungsi `Android.saveData / loadData` yang sudah ada di kode Anda). Backup/pulihkan file `.json`
memakai pemilih file Android.

## Catatan
- `INTERNET` hanya agar pilihan *Google Font* bisa diunduh. Hapus baris itu di `AndroidManifest.xml` jika ingin 100% offline.
- Splash bawaan Android 12+ berwarna tetap (`res/values/colors.xml › splash_bg`, default = header tema Sequoia). Ubah nilainya bila tema favorit Anda berbeda.
- Pemilih teks dimatikan, jadi menempel teks dengan tekan-lama tidak tersedia; gunakan *Pulihkan dari file* untuk restore backup.
- Ikon aplikasi dibuat dari logo di HTML Anda (`res/mipmap-*`).

## Versi 10

- **Tracking:** kartu "Sudah selesai" bisa dilipat dan dibuka (pilihan diingat). Awalnya terlipat.
- **Produksi:** deretan batch tidak lagi melompat ke awal saat batch dipilih; posisi geser dijaga dan batch terpilih otomatis digeser ke tengah.
- **Tombol Back:** kembali selangkah demi selangkah ke pilihan sebelumnya (sheet, batch, filter, sub-tab, tab). Hanya memindahkan tampilan, tidak pernah membatalkan data. Di halaman awal (Tracking), Back pertama menampilkan peringatan, Back kedua baru keluar.
- **Startup:** tanpa layar loading; satu kali render; logo diperkecil (±125 KB lebih ringan).
- **Penyimpanan:** perubahan digabung (300 ms) agar tidak menulis berulang, dan langsung ditulis saat aplikasi ditutup, di-pause, atau Back keluar.
- **Frame rate:** aplikasi meminta refresh rate tertinggi layar (90/120 Hz).
- **Transisi:** tab, sheet, color wheel, pencarian, akordeon, toast, dan centang baris memakai satu aturan: hanya transform dan opacity, masuk melambat, keluar dipercepat.

## Cara mendapatkan APK

Proyek ini sudah lengkap dan mandiri (WebView memuat `assets/index.html` dari dalam APK, tanpa hosting, tanpa browser).
Paling mudah: unggah isi folder ke repo GitHub, buka tab *Actions*, pilih *Build APK*, klik *Run workflow*, lalu unduh artifact `StokProduksi-APK`.


## Versi 11

- **Tracking:** sekarang menampilkan proses **Belum** dan **Proses** (dulu hanya Proses), urut Belum → Proses → Selesai di tiap batch. Ada filter status (Semua / Belum / Proses) dan lencana status di tiap baris. Ketuk ▶ pada baris Belum untuk memulai; ketuk ✔ pada baris Proses untuk menyelesaikan. Tidak perlu lagi bolak-balik ke tab Produksi untuk mengatur petugas.
- **Pop-up pilih petugas:** setiap perpindahan **Belum → Proses** (dari Tracking, tile Produksi, Per Tahap, maupun lembar gendel) membuka pop-up daftar karyawan sesuai prosesnya. Ketuk nama = langsung mulai. Bisa juga menambah karyawan baru dari pop-up. Menampilkan jumlah pekerjaan yang sedang dipegang tiap karyawan.
- **Bug lanjut proses:** saat satu tahap diselesaikan, tahap berikutnya tidak lagi otomatis jalan tanpa petugas; muncul pop-up pilih petugas tahap berikutnya (bila dilewati, tahap itu tetap Belum dan muncul di Tracking). "Selesaikan semua" tidak memunculkan pop-up berantai.
- **Tambah bahan:** ketuk ukuran yang sama berkali-kali untuk membuat beberapa gendel dengan ukuran sama (lencana ×2, ×3…). Tiap gendel punya kolom jumlah sendiri dan tombol ✕ untuk menghapus.
- **Data:** data awal aplikasi diganti dengan file backup `backup-stok-produksi-2026-10-19.json` (20 batch). Pada pemasangan baru, aplikasi langsung berisi data tersebut. Jika dipasang menimpa versi lama, data terbaru (berdasarkan waktu simpan) yang dipakai.
