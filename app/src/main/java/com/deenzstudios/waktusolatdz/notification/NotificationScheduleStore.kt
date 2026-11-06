package com.deenzstudios.waktusolatdz.notification

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Satu notifikasi yang telah dijadualkan.
 *
 * @param triggerAt masa (epoch millis) alarm akan berbunyi.
 * @param reminder true untuk peringatan sebelum waktu, false untuk tepat pada waktu.
 * @param alarmCode request code PendingIntent yang unik.
 * @param notificationId id notifikasi yang akan dipaparkan.
 */
data class ScheduledEvent(
    val triggerAt: Long,
    val prayer: String,
    val time: String,
    val zone: String,
    val reminder: Boolean,
    val alarmCode: Int,
    val notificationId: Int,
)

/**
 * Simpan jadual alarm dalam SharedPreferences supaya boleh dipasang semula
 * selepas peranti dimulakan semula tanpa perlu akses internet.
 */
class NotificationScheduleStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(events: List<ScheduledEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put(KEY_TRIGGER, event.triggerAt)
                    .put(KEY_PRAYER, event.prayer)
                    .put(KEY_TIME, event.time)
                    .put(KEY_ZONE, event.zone)
                    .put(KEY_REMINDER, event.reminder)
                    .put(KEY_ALARM_CODE, event.alarmCode)
                    .put(KEY_NOTIFICATION_ID, event.notificationId),
            )
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    fun load(): List<ScheduledEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ScheduledEvent(
                triggerAt = item.optLong(KEY_TRIGGER),
                prayer = item.optString(KEY_PRAYER),
                time = item.optString(KEY_TIME),
                zone = item.optString(KEY_ZONE),
                reminder = item.optBoolean(KEY_REMINDER),
                alarmCode = item.optInt(KEY_ALARM_CODE),
                notificationId = item.optInt(KEY_NOTIFICATION_ID),
            )
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_EVENTS).apply()
    }

    private companion object {
        const val PREFS_NAME = "notification_schedule"
        const val KEY_EVENTS = "events"
        const val KEY_TRIGGER = "triggerAt"
        const val KEY_PRAYER = "prayer"
        const val KEY_TIME = "time"
        const val KEY_ZONE = "zone"
        const val KEY_REMINDER = "reminder"
        const val KEY_ALARM_CODE = "alarmCode"
        const val KEY_NOTIFICATION_ID = "notificationId"
    }
}
