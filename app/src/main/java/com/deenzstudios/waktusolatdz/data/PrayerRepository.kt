package com.deenzstudios.waktusolatdz.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sumber data waktu solat daripada API e-Solat JAKIM.
 *
 * Contoh: https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=WLY01
 * API ini tidak rasmi, jadi hasilnya dicache dalam memori untuk elak permintaan berulang.
 */
class PrayerRepository(
    private val baseUrl: String = "https://www.e-solat.gov.my/index.php",
) {

    private val cache = mutableMapOf<String, PrayerTimes>()

    suspend fun getToday(zone: String = DEFAULT_ZONE): PrayerTimes = withContext(Dispatchers.IO) {
        cache[zone]?.let { return@withContext it }

        val body = request(zone, PERIOD_TODAY)
        parseDays(zone, body).first().also { cache[zone] = it }
    }

    /**
     * Ambil jadual beberapa hari (JAKIM memulangkan hari ini + 7 hari).
     * Digunakan untuk menjadualkan notifikasi tanpa perlu buka app setiap hari.
     */
    suspend fun getWeek(zone: String = DEFAULT_ZONE): List<PrayerTimes> =
        withContext(Dispatchers.IO) {
            parseDays(zone, request(zone, PERIOD_WEEK))
        }

    /** Buang cache supaya panggilan seterusnya ambil data baru. */
    fun clearCache() {
        cache.clear()
    }

    private fun request(zone: String, period: String): String {
        val encodedZone = URLEncoder.encode(zone, Charsets.UTF_8.name())
        val url = URL("$baseUrl?r=esolatApi/takwimsolat&period=$period&zone=$encodedZone")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WaktuSolatDZ/1.0")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Pelayan JAKIM memulangkan HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDays(zone: String, body: String): List<PrayerTimes> {
        val root = JSONObject(body)
        val status = root.optString("status")
        if (status != "OK!") {
            throw IOException("Respons JAKIM tidak sah: ${status.ifBlank { "tiada status" }}")
        }

        val prayerTimeArray = root.optJSONArray("prayerTime")
            ?: throw IOException("Respons JAKIM tiada medan prayerTime")
        if (prayerTimeArray.length() == 0) {
            throw IOException("Respons JAKIM kosong untuk zon $zone")
        }

        val responseZone = root.optString("zone", zone)
        return (0 until prayerTimeArray.length()).map { index ->
            parseDay(responseZone, prayerTimeArray.getJSONObject(index))
        }
    }

    private fun parseDay(zone: String, day: JSONObject): PrayerTimes = PrayerTimes(
        zone = zone,
        date = day.optString("date"),
        hijriDate = day.optString("hijri"),
        day = day.optString("day"),
        imsak = day.shortTime("imsak"),
        fajr = day.shortTime("fajr"),
        syuruk = day.shortTime("syuruk"),
        dhuha = day.shortTime("dhuha"),
        dhuhr = day.shortTime("dhuhr"),
        asr = day.shortTime("asr"),
        maghrib = day.shortTime("maghrib"),
        isha = day.shortTime("isha"),
    )

    /** JAKIM pulangkan "05:50:00"; kita simpan "05:50" sahaja. */
    private fun JSONObject.shortTime(key: String): String {
        val raw = optString(key)
        return if (raw.length >= 5) raw.substring(0, 5) else raw
    }

    companion object {
        /** Zon lalai: Kuala Lumpur / Putrajaya. */
        const val DEFAULT_ZONE = "WLY01"

        private const val PERIOD_TODAY = "today"
        private const val PERIOD_WEEK = "week"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
