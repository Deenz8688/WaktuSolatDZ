package com.deenzstudios.waktusolatdz.data

import android.content.Context

/** Tetapan notifikasi yang dipilih pengguna dalam tab Tetapan. */
data class NotificationSettings(
    val enabled: Boolean = false,
    val remindBefore: Boolean = true,
    val notifyOnTime: Boolean = true,
    /** Waktu yang dihidupkan notifikasinya (Imsak hingga Isyak). */
    val slots: Set<PrayerTimeSlot> = PrayerTimeSlot.DEFAULT_ENABLED,
)

/** Simpan pilihan notifikasi supaya kekal selepas app ditutup. */
class NotificationPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Peringatan beberapa minit sebelum masuk waktu. */
    var remindBefore: Boolean
        get() = prefs.getBoolean(KEY_REMIND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REMIND, value).apply()
        }

    /** Notifikasi tepat pada waktu solat. */
    var notifyOnTime: Boolean
        get() = prefs.getBoolean(KEY_ON_TIME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ON_TIME, value).apply()
        }

    /** Waktu yang dipilih untuk menerima notifikasi. */
    var slots: Set<PrayerTimeSlot>
        get() = prefs.getStringSet(KEY_SLOTS, null)
            ?.mapNotNull(PrayerTimeSlot::fromName)
            ?.toSet()
            ?: PrayerTimeSlot.DEFAULT_ENABLED
        set(value) {
            prefs.edit().putStringSet(KEY_SLOTS, value.map { it.name }.toSet()).apply()
        }

    fun soundMode(slot: PrayerTimeSlot): String =
        prefs.getString("sound_${slot.name}", "normal") ?: "normal"

    fun setSoundMode(slot: PrayerTimeSlot, mode: String) {
        require(mode in setOf("normal", "azan", "silent"))
        prefs.edit().putString("sound_${slot.name}", mode).apply()
    }

    fun settings(): NotificationSettings = NotificationSettings(
        enabled = enabled,
        remindBefore = remindBefore,
        notifyOnTime = notifyOnTime,
        slots = slots,
    )

    companion object {
        /** Notifikasi peringatan dihantar 5 minit sebelum masuk waktu. */
        const val REMINDER_MINUTES = 5

        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_ENABLED = "notifications_enabled"
        private const val KEY_REMIND = "remind_before"
        private const val KEY_ON_TIME = "notify_on_time"
        private const val KEY_SLOTS = "notify_slots"
    }
}
