package com.deenzstudios.waktusolatdz.data

/**
 * Pemformatan tarikh daripada API JAKIM.
 * Dikongsi antara UI dan widget supaya paparan sentiasa sama.
 */

/** "1448-05-06" → "6 Jamadilawal 1448H". */
fun formatHijriDate(raw: String): String {
    val parts = raw.split("-")
    if (parts.size != 3) return raw
    val day = parts[2].toIntOrNull() ?: return raw
    val month = HIJRI_MONTHS.getOrNull((parts[1].toIntOrNull() ?: return raw) - 1) ?: return raw
    return "$day $month ${parts[0]}H"
}

/** "17-Okt-2026" → "17 Oktober 2026". */
fun formatGregorianDate(raw: String): String {
    val parts = raw.split("-")
    if (parts.size != 3) return raw
    val day = parts[0].toIntOrNull()?.toString() ?: parts[0]
    val month = GREGORIAN_MONTHS[parts[1]] ?: parts[1]
    return "$day $month ${parts[2]}"
}

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
)

private val GREGORIAN_MONTHS = mapOf(
    "Jan" to "Januari", "Feb" to "Februari", "Mac" to "Mac", "Apr" to "April",
    "Mei" to "Mei", "Jun" to "Jun", "Jul" to "Julai", "Ogos" to "Ogos",
    "Sep" to "September", "Okt" to "Oktober", "Nov" to "November", "Dis" to "Disember",
)
