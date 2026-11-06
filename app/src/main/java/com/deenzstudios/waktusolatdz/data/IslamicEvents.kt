package com.deenzstudios.waktusolatdz.data

import java.util.Calendar

/**
 * Tarikh-tarikh penting dalam kalendar Hijrah.
 *
 * Tarikh Masihi dikira daripada tarikh Hijrah hari ini yang diberikan API JAKIM
 * (`hijriDate`), menggunakan panjang bulan Hijrah tabular (30/29 hari). Ini
 * anggaran — tarikh rasmi Aidilfitri/Aidiladha di Malaysia ditentukan JAKIM
 * melalui rukyah, jadi paparan sentiasa ditanda sebagai anggaran.
 */
data class IslamicEvent(
    val name: String,
    val hijriMonth: Int,
    val hijriDay: Int,
    /** Tajuk notifikasi pada hari kejadian. */
    val title: String,
    /** Ayat ringkas di bawah tajuk notifikasi. */
    val message: String,
)

/** Tarikh penting yang sudah dikira untuk dipaparkan. */
data class UpcomingIslamicEvent(
    val name: String,
    val hijriLabel: String,
    val dateLabel: String,
    val daysAway: Int,
    /** Awal hari kejadian (00:00 waktu tempatan) dalam epoch millis. */
    val dateMillis: Long,
    val title: String,
    val message: String,
)

val IslamicEvents = listOf(
    IslamicEvent(
        name = "Awal Muharram",
        hijriMonth = 1,
        hijriDay = 1,
        title = "Selamat Tahun Baru Hijrah",
        message = "Awal Muharram — semoga tahun baharu ini lebih baik daripada sebelumnya.",
    ),
    IslamicEvent(
        name = "Hari Asyura",
        hijriMonth = 1,
        hijriDay = 10,
        title = "Hari Asyura Hari Ini",
        message = "10 Muharram — disunatkan berpuasa pada hari Asyura.",
    ),
    IslamicEvent(
        name = "Maulidur Rasul",
        hijriMonth = 3,
        hijriDay = 12,
        title = "Selamat Maulidur Rasul",
        message = "12 Rabiulawal — selawat dan salam buat junjungan Nabi Muhammad SAW.",
    ),
    IslamicEvent(
        name = "Israk & Mikraj",
        hijriMonth = 7,
        hijriDay = 27,
        title = "Israk & Mikraj Hari Ini",
        message = "27 Rejab — peringatan peristiwa perjalanan malam Rasulullah SAW.",
    ),
    IslamicEvent(
        name = "Awal Ramadan",
        hijriMonth = 9,
        hijriDay = 1,
        title = "Selamat Menyambut Ramadan",
        message = "1 Ramadan — semoga ibadah puasa kita diterima Allah.",
    ),
    IslamicEvent(
        name = "Nuzul Al-Quran",
        hijriMonth = 9,
        hijriDay = 17,
        title = "Nuzul Al-Quran Hari Ini",
        message = "17 Ramadan — peringatan turunnya Al-Quran.",
    ),
    IslamicEvent(
        name = "Aidilfitri",
        hijriMonth = 10,
        hijriDay = 1,
        title = "Selamat Menyambut Hari Raya Aidilfitri",
        message = "1 Syawal — maaf zahir dan batin daripada kami, Waktu Solat DZ.",
    ),
    IslamicEvent(
        name = "Hari Arafah",
        hijriMonth = 12,
        hijriDay = 9,
        title = "Hari Arafah Hari Ini",
        message = "9 Zulhijjah — hari terbaik untuk berdoa dan berpuasa.",
    ),
    IslamicEvent(
        name = "Aidiladha",
        hijriMonth = 12,
        hijriDay = 10,
        title = "Selamat Menyambut Hari Raya Aidiladha",
        message = "10 Zulhijjah — selamat berkorban, semoga amal ibadah diterima.",
    ),
)

/**
 * Senarai tarikh penting seterusnya, disusun mengikut jarak hari.
 *
 * @param hijriDate tarikh Hijrah hari ini daripada API, cth. "1448-05-06".
 * @param gregorianDate tarikh Masihi hari ini daripada API, cth. "17-Okt-2026".
 */
fun upcomingIslamicEvents(
    hijriDate: String,
    gregorianDate: String,
    count: Int = 4,
): List<UpcomingIslamicEvent> {
    val today = parseHijri(hijriDate) ?: return emptyList()
    val calendar = parseGregorian(gregorianDate) ?: return emptyList()
    val todayOrdinal = dayOfYear(today.year, today.month, today.day)

    return IslamicEvents
        .map { event ->
            val thisYear = dayOfYear(today.year, event.hijriMonth, event.hijriDay) - todayOrdinal
            val hijriYear: Int
            val daysAway: Int
            if (thisYear >= 0) {
                hijriYear = today.year
                daysAway = thisYear
            } else {
                hijriYear = today.year + 1
                daysAway = yearDays(today.year) - todayOrdinal +
                    dayOfYear(hijriYear, event.hijriMonth, event.hijriDay)
            }

            val date = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, daysAway) }
            val monthAbbrev = GREGORIAN_ABBREV[date.get(Calendar.MONTH)]
            val gregorian = "${date.get(Calendar.DAY_OF_MONTH)}-$monthAbbrev-${date.get(Calendar.YEAR)}"

            UpcomingIslamicEvent(
                name = event.name,
                hijriLabel = formatHijriDate(
                    "$hijriYear-${pad(event.hijriMonth)}-${pad(event.hijriDay)}",
                ),
                dateLabel = formatGregorianDate(gregorian),
                daysAway = daysAway,
                dateMillis = date.timeInMillis,
                title = event.title,
                message = event.message,
            )
        }
        .sortedBy { it.daysAway }
        .take(count)
}

private data class HijriDate(val year: Int, val month: Int, val day: Int)

private fun parseHijri(raw: String): HijriDate? {
    val parts = raw.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year <= 0 || month !in 1..12 || day !in 1..30) return null
    return HijriDate(year, month, day)
}

private fun parseGregorian(raw: String): Calendar? {
    val parts = raw.split("-")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = GREGORIAN_ABBREV.indexOf(parts[1])
    if (month < 0) return null
    val year = parts[2].toIntOrNull() ?: return null
    return Calendar.getInstance().apply {
        clear()
        set(year, month, day)
    }
}

private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

/** Tahun Hijrah tabular: 354 hari, atau 355 hari dalam tahun lompat. */
private fun yearDays(year: Int): Int = if (isLeap(year)) 355 else 354

private fun isLeap(year: Int): Boolean = (11 * year + 14) % 30 < 11

private fun monthDays(year: Int, month: Int): Int = when {
    month == 12 && isLeap(year) -> 30
    month % 2 == 1 -> 30
    else -> 29
}

/** Hari ke-berapa dalam tahun Hijrah (1 Muharram = 1). */
private fun dayOfYear(year: Int, month: Int, day: Int): Int {
    var total = day
    for (m in 1 until month) total += monthDays(year, m)
    return total
}

private val GREGORIAN_ABBREV = arrayOf(
    "Jan", "Feb", "Mac", "Apr", "Mei", "Jun",
    "Jul", "Ogos", "Sep", "Okt", "Nov", "Dis",
)
