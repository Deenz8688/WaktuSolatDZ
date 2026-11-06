package com.deenzstudios.waktusolatdz.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Cuaca semasa bagi satu kawasan (untuk widget).
 *
 * [code] ialah kod cuaca WMO yang digunakan oleh Open-Meteo:
 * 0 = cerah, 1–3 = berawan, 45–48 = kabus, 51–67/80–82 = hujan, 95–99 = ribut.
 */
data class WeatherNow(val temperature: Int, val code: Int) {

    /** Kategori ringkas untuk memilih ikon widget. */
    val kind: Kind
        get() = when (code) {
            0 -> Kind.Sunny
            1, 2 -> Kind.Partly
            in 51..67, in 80..82, in 95..99 -> Kind.Rain
            else -> Kind.Cloudy
        }

    enum class Kind { Sunny, Partly, Cloudy, Rain }
}

/** Ambil cuaca semasa daripada Open-Meteo (percuma, tiada kunci API). */
object WeatherRepository {

    suspend fun current(latitude: Double, longitude: Double): WeatherNow? =
        withContext(Dispatchers.IO) {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code" +
                    "&timezone=Asia%2FKuala_Lumpur",
            )
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                if (connection.responseCode !in 200..299) return@withContext null

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val current = JSONObject(body).optJSONObject("current") ?: return@withContext null
                val temperature = current.optDouble("temperature_2m", Double.NaN)
                if (temperature.isNaN()) return@withContext null

                WeatherNow(
                    temperature = Math.round(temperature).toInt(),
                    code = current.optInt("weather_code", 0),
                )
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    private const val TIMEOUT_MS = 6_000
}

/** Simpan bacaan cuaca terakhir supaya widget masih berguna tanpa internet. */
class WeatherStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): WeatherNow? {
        if (!prefs.contains(KEY_TEMPERATURE)) return null
        return WeatherNow(
            temperature = prefs.getInt(KEY_TEMPERATURE, 0),
            code = prefs.getInt(KEY_CODE, 0),
        )
    }

    fun save(weather: WeatherNow) {
        prefs.edit()
            .putInt(KEY_TEMPERATURE, weather.temperature)
            .putInt(KEY_CODE, weather.code)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Bacaan dianggap basi selepas [MAX_AGE] (widget dikemas kini setiap 30 minit). */
    fun isFresh(): Boolean {
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        return System.currentTimeMillis() - updatedAt in 0..MAX_AGE_MS
    }

    private companion object {
        const val PREFS_NAME = "weather_cache"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_CODE = "code"
        const val KEY_UPDATED_AT = "updated_at"
        val MAX_AGE_MS = TimeUnit.MINUTES.toMillis(45)
    }
}
