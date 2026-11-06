package com.deenzstudios.waktusolatdz.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simpanan senarai masjid terakhir yang berjaya dimuatkan, mengikut zon.
 *
 * Overpass (OpenStreetMap) percuma tetapi kadangkala sibuk atau tidak boleh
 * dihubungi. Cache ini membolehkan tab Masjid masih memaparkan senarai terakhir
 * walaupun rangkaian gagal — dipaparkan sebagai "data cache".
 */
class MosqueCache(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(zone: String, mosques: List<NearbyMosque>) {
        if (mosques.isEmpty()) return
        val array = JSONArray()
        mosques.forEach { mosque ->
            array.put(
                JSONObject().apply {
                    put("id", mosque.id)
                    put("name", mosque.name)
                    put("kind", mosque.kind.name)
                    put("lat", mosque.point.latitude)
                    put("lon", mosque.point.longitude)
                    put("distance", mosque.distanceKm)
                },
            )
        }
        preferences.edit().putString(key(zone), array.toString()).apply()
    }

    fun load(zone: String): List<NearbyMosque>? {
        val raw = preferences.getString(key(zone), null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                NearbyMosque(
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    kind = runCatching { PlaceKind.valueOf(item.optString("kind")) }
                        .getOrDefault(PlaceKind.MASJID),
                    point = GeoPoint(item.optDouble("lat"), item.optDouble("lon")),
                    distanceKm = item.optDouble("distance"),
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun key(zone: String): String = "mosques_$zone"

    private companion object {
        const val PREFERENCES_NAME = "mosque_cache"
    }
}
