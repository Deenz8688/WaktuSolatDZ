package com.deenzstudios.waktusolatdz.data

/** Waktu harian yang boleh dihidupkan/matikan notifikasinya dalam tab Tetapan. */
enum class PrayerTimeSlot(val label: String) {
    IMSAK("Imsak"),
    SUBUH("Subuh"),
    SYURUK("Syuruk"),
    DHUHA("Dhuha"),
    ZOHOR("Zohor"),
    ASAR("Asar"),
    MAGHRIB("Maghrib"),
    ISYAK("Isyak"),
    ;

    fun timeOf(times: PrayerTimes): String? = when (this) {
        IMSAK -> times.imsak
        SUBUH -> times.fajr
        SYURUK -> times.syuruk
        DHUHA -> times.dhuha
        ZOHOR -> times.dhuhr
        ASAR -> times.asr
        MAGHRIB -> times.maghrib
        ISYAK -> times.isha
    }.takeIf { it.isNotBlank() }

    companion object {
        val DEFAULT_ENABLED: Set<PrayerTimeSlot> = entries.toSet()

        fun fromName(name: String): PrayerTimeSlot? = entries.firstOrNull { it.name == name }
    }
}
