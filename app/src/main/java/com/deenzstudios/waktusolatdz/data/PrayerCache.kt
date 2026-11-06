package com.deenzstudios.waktusolatdz.data

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simpan waktu solat hari ini supaya widget boleh memaparkannya
 * tanpa perlu menunggu rangkaian setiap kali.
 */
class PrayerCache(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(times: PrayerTimes) {
        val json = JSONObject()
            .put(KEY_ZONE, times.zone)
            .put(KEY_DATE, times.date)
            .put(KEY_HIJRI, times.hijriDate)
            .put(KEY_DAY, times.day)
            .put(KEY_IMSAK, times.imsak)
            .put(KEY_FAJR, times.fajr)
            .put(KEY_SYURUK, times.syuruk)
            .put(KEY_DHUHA, times.dhuha)
            .put(KEY_DHUHR, times.dhuhr)
            .put(KEY_ASR, times.asr)
            .put(KEY_MAGHRIB, times.maghrib)
            .put(KEY_ISHA, times.isha)

        prefs.edit()
            .putString(KEY_TIMES, json.toString())
            .putString(KEY_SAVED_ON, todayKey())
            .apply()
    }

    fun load(): PrayerTimes? {
        val raw = prefs.getString(KEY_TIMES, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null

        return PrayerTimes(
            zone = json.optString(KEY_ZONE),
            date = json.optString(KEY_DATE),
            hijriDate = json.optString(KEY_HIJRI),
            day = json.optString(KEY_DAY),
            imsak = json.optString(KEY_IMSAK),
            fajr = json.optString(KEY_FAJR),
            syuruk = json.optString(KEY_SYURUK),
            dhuha = json.optString(KEY_DHUHA),
            dhuhr = json.optString(KEY_DHUHR),
            asr = json.optString(KEY_ASR),
            maghrib = json.optString(KEY_MAGHRIB),
            isha = json.optString(KEY_ISHA),
        )
    }

    /** Benar jika cache disimpan pada hari ini, jadi waktu solat masih relevan. */
    fun isFresh(): Boolean = prefs.getString(KEY_SAVED_ON, null) == todayKey()

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        const val PREFS_NAME = "prayer_cache"
        const val KEY_TIMES = "times"
        const val KEY_SAVED_ON = "saved_on"

        const val KEY_ZONE = "zone"
        const val KEY_DATE = "date"
        const val KEY_HIJRI = "hijri"
        const val KEY_DAY = "day"
        const val KEY_IMSAK = "imsak"
        const val KEY_FAJR = "fajr"
        const val KEY_SYURUK = "syuruk"
        const val KEY_DHUHA = "dhuha"
        const val KEY_DHUHR = "dhuhr"
        const val KEY_ASR = "asr"
        const val KEY_MAGHRIB = "maghrib"
        const val KEY_ISHA = "isha"
    }
}
