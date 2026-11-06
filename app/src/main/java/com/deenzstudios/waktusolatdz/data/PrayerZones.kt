package com.deenzstudios.waktusolatdz.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Satu zon takwim solat JAKIM. */
data class PrayerZone(
    val code: String,
    val state: String,
    val district: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Senarai zon takwim solat JAKIM (63 zon).
 *
 * JAKIM tidak menyediakan endpoint senarai zon, jadi senarai ini disimpan secara
 * statik mengikut kod zon rasmi yang digunakan oleh API e-Solat. Koordinat ialah
 * titik tengah anggaran bagi setiap zon, digunakan untuk mengesan zon terdekat.
 */
object PrayerZones {

    val all: List<PrayerZone> = listOf(
        PrayerZone("JHR01", "Johor", "Pulau Aur dan Pulau Pemanggil", 2.45, 104.52),
        PrayerZone("JHR02", "Johor", "Johor Bahru, Kota Tinggi, Mersing, Kulai", 1.49, 103.74),
        PrayerZone("JHR03", "Johor", "Kluang, Pontian", 2.03, 103.32),
        PrayerZone("JHR04", "Johor", "Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak", 2.17, 102.72),

        PrayerZone("KDH01", "Kedah", "Kota Setar, Kubang Pasu, Pokok Sena", 6.13, 100.38),
        PrayerZone("KDH02", "Kedah", "Kuala Muda, Yan, Pendang", 5.65, 100.49),
        PrayerZone("KDH03", "Kedah", "Padang Terap, Sik", 6.03, 100.68),
        PrayerZone("KDH04", "Kedah", "Baling", 5.68, 100.92),
        PrayerZone("KDH05", "Kedah", "Bandar Baharu, Kulim", 5.37, 100.56),
        PrayerZone("KDH06", "Kedah", "Langkawi", 6.35, 99.80),
        PrayerZone("KDH07", "Kedah", "Puncak Gunung Jerai", 5.79, 100.44),

        PrayerZone(
            "KTN01", "Kelantan",
            "Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai, Mukim Chiku",
            6.12, 102.24,
        ),
        PrayerZone("KTN02", "Kelantan", "Gua Musang, Jeli, Jajahan Kecil Lojing", 5.30, 101.90),

        PrayerZone("MLK01", "Melaka", "Seluruh negeri Melaka", 2.19, 102.25),

        PrayerZone("NGS01", "Negeri Sembilan", "Tampin, Jempol", 2.64, 102.32),
        PrayerZone("NGS02", "Negeri Sembilan", "Jelebu, Kuala Pilah, Rembau", 2.76, 102.13),
        PrayerZone("NGS03", "Negeri Sembilan", "Port Dickson, Seremban", 2.65, 101.90),

        PrayerZone("PHG01", "Pahang", "Pulau Tioman", 2.79, 104.17),
        PrayerZone("PHG02", "Pahang", "Kuantan, Pekan, Muadzam Shah", 3.75, 103.25),
        PrayerZone("PHG03", "Pahang", "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka", 3.66, 102.52),
        PrayerZone("PHG04", "Pahang", "Bentong, Lipis, Raub", 3.83, 101.94),
        PrayerZone("PHG05", "Pahang", "Genting Sempah, Janda Baik, Bukit Tinggi", 3.40, 101.78),
        PrayerZone("PHG06", "Pahang", "Cameron Highlands, Genting Highlands, Bukit Fraser", 4.47, 101.38),
        PrayerZone("PHG07", "Pahang", "Rompin, Endau, Mukim Pontian", 2.68, 103.55),

        PrayerZone("PRK01", "Perak", "Tapah, Slim River, Tanjung Malim", 3.94, 101.39),
        PrayerZone("PRK02", "Perak", "Kuala Kangsar, Sungai Siput, Ipoh, Batu Gajah, Kampar", 4.60, 101.08),
        PrayerZone("PRK03", "Perak", "Lenggong, Pengkalan Hulu, Grik", 5.27, 101.05),
        PrayerZone("PRK04", "Perak", "Temengor, Belum", 5.55, 101.35),
        PrayerZone(
            "PRK05", "Perak",
            "Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor",
            4.08, 100.84,
        ),
        PrayerZone("PRK06", "Perak", "Selama, Taiping, Bagan Serai, Parit Buntar", 4.95, 100.65),
        PrayerZone("PRK07", "Perak", "Bukit Larut", 4.86, 100.79),

        PrayerZone("PLS01", "Perlis", "Seluruh negeri Perlis", 6.44, 100.20),

        PrayerZone("PNG01", "Pulau Pinang", "Seluruh negeri Pulau Pinang", 5.41, 100.33),

        PrayerZone(
            "SBH01", "Sabah",
            "Bahagian Sandakan (Timur), Bukit Garam, Semawang, Temanggong, Tambisan, Bandar Sandakan, Sukau",
            5.84, 118.12,
        ),
        PrayerZone("SBH02", "Sabah", "Beluran, Telupid, Pinangah, Terusan, Kuamut, Bahagian Sandakan (Barat)", 5.75, 117.35),
        PrayerZone(
            "SBH03", "Sabah",
            "Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku, Bahagian Tawau (Timur)",
            4.73, 118.40,
        ),
        PrayerZone("SBH04", "Sabah", "Bandar Tawau, Balong, Merotai, Kalabakan, Bahagian Tawau (Barat)", 4.24, 117.89),
        PrayerZone("SBH05", "Sabah", "Kudat, Kota Marudu, Pitas, Pulau Banggi, Bahagian Kudat", 6.66, 116.88),
        PrayerZone("SBH06", "Sabah", "Gunung Kinabalu", 6.08, 116.55),
        PrayerZone(
            "SBH07", "Sabah",
            "Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan, Bahagian Pantai Barat",
            5.95, 116.22,
        ),
        PrayerZone("SBH08", "Sabah", "Pensiangan, Keningau, Tambunan, Nabawan, Bahagian Pendalaman (Atas)", 5.37, 116.33),
        PrayerZone(
            "SBH09", "Sabah",
            "Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston, Bahagian Pendalaman (Bawah)",
            5.18, 115.75,
        ),

        PrayerZone("SWK01", "Sarawak", "Limbang, Lawas, Sundar, Trusan", 4.80, 115.20),
        PrayerZone("SWK02", "Sarawak", "Miri, Niah, Bekenu, Sibuti, Marudi", 4.29, 114.16),
        PrayerZone("SWK03", "Sarawak", "Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu", 2.94, 113.41),
        PrayerZone("SWK04", "Sarawak", "Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit", 2.39, 112.28),
        PrayerZone("SWK05", "Sarawak", "Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai", 2.22, 111.62),
        PrayerZone(
            "SWK06", "Sarawak",
            "Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok",
            1.46, 111.44,
        ),
        PrayerZone("SWK07", "Sarawak", "Serian, Simunjan, Samarahan, Sebuyau, Meludam", 1.29, 110.60),
        PrayerZone("SWK08", "Sarawak", "Kuching, Bau, Lundu, Sematan", 1.55, 110.12),
        PrayerZone("SWK09", "Sarawak", "Zon khas Kampung Patarikan", 4.90, 115.30),

        PrayerZone("SGR01", "Selangor", "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam", 3.14, 101.63),
        PrayerZone("SGR02", "Selangor", "Kuala Selangor, Sabak Bernam", 3.55, 101.12),
        PrayerZone("SGR03", "Selangor", "Klang, Kuala Langat", 2.93, 101.48),

        PrayerZone("TRG01", "Terengganu", "Kuala Terengganu, Marang, Kuala Nerus", 5.30, 103.10),
        PrayerZone("TRG02", "Terengganu", "Besut, Setiu", 5.63, 102.73),
        PrayerZone("TRG03", "Terengganu", "Hulu Terengganu", 5.07, 103.03),
        PrayerZone("TRG04", "Terengganu", "Dungun, Kemaman", 4.50, 103.42),

        PrayerZone("WLY01", "Wilayah Persekutuan", "Kuala Lumpur, Putrajaya", 3.10, 101.68),
        PrayerZone("WLY02", "Wilayah Persekutuan", "Labuan", 5.28, 115.24),
    )

    /** Nama negeri tanpa ulangan, mengikut susunan senarai di atas. */
    val states: List<String> = all.map { it.state }.distinct()

    fun of(code: String): PrayerZone? = all.firstOrNull { it.code == code }

    fun inState(state: String): List<PrayerZone> = all.filter { it.state == state }

    /** Zon yang paling hampir dengan koordinat diberi. */
    fun nearest(latitude: Double, longitude: Double): PrayerZone =
        all.minBy { distanceKm(it.latitude, it.longitude, latitude, longitude) }

    /** Jarak bulatan besar (haversine) dalam kilometer. */
    private fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a))
    }
}
