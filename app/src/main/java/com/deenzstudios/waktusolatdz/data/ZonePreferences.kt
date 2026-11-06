package com.deenzstudios.waktusolatdz.data

import android.content.Context

/** Simpan zon pilihan pengguna supaya kekal selepas app ditutup. */
class ZonePreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedZone: String
        get() {
            val saved = prefs.getString(KEY_ZONE, null)
            return if (saved != null && PrayerZones.of(saved) != null) {
                saved
            } else {
                PrayerRepository.DEFAULT_ZONE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_ZONE, value).apply()
        }

    /** True jika pengguna memilih zon sendiri, jadi auto-detect tidak menimpa pilihan itu. */
    var hasManualZone: Boolean
        get() = prefs.getBoolean(KEY_MANUAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MANUAL, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "waktu_solat_prefs"
        const val KEY_ZONE = "selected_zone"
        const val KEY_MANUAL = "manual_zone"
    }
}
