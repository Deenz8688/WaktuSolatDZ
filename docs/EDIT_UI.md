# Panduan ubah reka bentuk app dalam XML

Buka fail layout dalam Android Studio dan pilih **Split** atau **Design**.
Teks tetap ditulis terus dalam `android:text` dalam lima fail `screen_*.xml` supaya mudah disunting.
Lokasi, tarikh, waktu dan kira detik mempunyai contoh teks dalam XML untuk reka bentuk;
binding menggantikannya dengan data sebenar sebelum kandungan dipaparkan dalam app.

`app/src/main/res/layout` kini hanya mengandungi lima skrin menu (`screen_*.xml`).
Layout komponen, shell activity, menu bawah dan widget berada dalam
`app/src/main/res-support/layout`. Direktori sokongan ini didaftarkan melalui
`sourceSets` dalam `app/build.gradle.kts`, jadi nama resource dan View Binding kekal sama.

Untuk melihat dua folder ini secara berasingan, pilih paparan **Project** dalam Android Studio.
Paparan **Android** mungkin menggabungkan resource daripada kedua-dua direktori.

| Bahagian | Fail |
| --- | --- |
| Shell activity | `app/src/main/res-support/layout/activity_main.xml` |
| Menu bawah | `app/src/main/res-support/layout/view_bottom_navigation.xml` |
| Waktu Solat | `app/src/main/res/layout/screen_schedule.xml` |
| Kiblat | `app/src/main/res/layout/screen_qibla.xml` |
| Masjid dan peta | `app/src/main/res/layout/screen_mosque.xml` |
| Baris senarai masjid | `app/src/main/res-support/layout/item_mosque.xml` |
| Tetapan, lokasi GPS dan suis notifikasi | `app/src/main/res/layout/screen_settings.xml` |
| Skrin Home penuh, padding dan keadaan loading/error | `app/src/main/res/layout/screen_home.xml` |
| Kandungan penuh Home dan kad solat seterusnya | `app/src/main/res/layout/screen_home.xml` |
| Cuaca: posisi, saiz dan ikon (`weather_group`) | `app/src/main/res/layout/screen_home.xml` |
| Gerakan cuaca | `app/src/main/res/animator/weather_*.xml` |
| Ayat cuaca | `app/src/main/res/values/weather_ui.xml` |
| Kad tarikh penting Islam | `app/src/main/res-support/layout/item_home_islamic_event.xml` |
| Bilangan baris kalendar yang kelihatan (3) | `app/src/main/res/values/home_calendar.xml` |
| Warna komponen dan teks | `app/src/main/res/values/home_colors.xml` |
| Font dan gaya teks Home | `app/src/main/res/values/home_styles.xml` |
| Teks tetap | `app/src/main/res/values/home_strings.xml` |
| Ayat dan format data berubah: kira detik, status, jarak dan sensor | `app/src/main/res/values/ui_text.xml` |
| Gradien kad ungu | `app/src/main/res/drawable/bg_home_hero.xml` |
| Background Home | `app/src/main/res/drawable/bg_home.xml` |
| Background Waktu Solat | `app/src/main/res/drawable/bg_schedule.xml` |
| Background Kiblat | `app/src/main/res/drawable/bg_qibla.xml` |
| Background Masjid | `app/src/main/res/drawable/bg_mosque.xml` |
| Background Tetapan | `app/src/main/res/drawable/bg_settings.xml` |

Setiap `screen_*.xml` menetapkan `android:background` pada elemen paling luar.
Lima background itu berasingan, termasuk warna gradien dan corak di dalam setiap fail.
Ubah background satu menu tanpa mengubah menu lain. Tiada lagi background bersama dalam Kotlin.
Untuk menggunakan gambar, simpan gambar dalam `res/drawable-nodpi` dan tukar
`android:background` pada skrin berkenaan, contohnya `@drawable/background_home_baru`.

Untuk artwork dari Illustrator, eksport ke PNG dan simpan di `res/drawable-nodpi`.
Kemudian tukar `android:background` pada kad kepada drawable itu. Susunan teks dan data waktu kekal.
Simpan ID view yang sedia ada supaya sambungan Kotlin terus berfungsi.

`HomeXmlScreen.kt` dan `XmlTabs.kt` menyambungkan data serta tindakan kepada view XML menggunakan View Binding.
Kandungan statik Home dan Waktu Solat berada terus dalam fail skrin utama.
Kad kalendar menggunakan `item_home_islamic_event.xml` yang sama untuk pratonton Home dan senarai sebenar.
Include `calendar_preview` hanya kelihatan dalam editor; binding membuangnya sebelum mengisi data sebenar.
Senarai tarikh penting menunjukkan nama, tarikh Hijrah dan baki hari. Tiga baris kelihatan dahulu;
baris lain boleh ditatal dalam kad yang sama. Tinggi sebenar mengikut tiga baris yang diukur.
Nama waktu dan nombor baris ditulis dalam XML; Kotlin hanya mengemas kini masa serta status.

Cuaca Home menggunakan koordinat zon yang dipilih dan sumber Open-Meteo yang sedia ada.
Paparan dikemas kini setiap 30 minit semasa Home aktif. Tanpa bacaan cuaca, ikon disembunyikan.
Matahari/bulan dipilih mengikut waktu Syuruk dan Maghrib jadual semasa.
Animasi dihentikan apabila Home ditinggalkan atau tetingkap tidak aktif.
Satu kitaran animasi mengambil 5 saat; gerakan ulang-alik menggunakan 2.5 saat setiap arah.
XML menunjukkan contoh cuaca dalam editor; app memaparkan data yang berjaya diperoleh.
Semua tab dan menu bawah sudah mempunyai layout XML. Kod layout Compose yang lama sudah dibuang.
Navigasi sedia ada masih menggunakan jambatan Compose dalam `PrayerScreen.kt`.

Peta interaktif dan cakera kompas ialah komponen lukisan khas dalam `MosqueMapCanvas.kt`
dan `QiblaCompass.kt`. Kedudukan dan saiz komponennya ditetapkan melalui `screen_mosque.xml`
dan `screen_qibla.xml`; pan, zoom, jubin peta dan bacaan sensor kekal dalam Kotlin.
Ia menggunakan `ComposeView` di dalam XML, jadi plugin Compose masih diperlukan.

Zon dikesan melalui GPS apabila app dibuka. Butang **Kesan Kawasan Saya** boleh mengesan semula lokasi.
Kad pilihan zon manual sudah dibuang; zon terakhir kekal tersimpan untuk kegunaan app dan notifikasi.
Jangan buang ID view tanpa mengemas kini binding Kotlin yang berkaitan.
