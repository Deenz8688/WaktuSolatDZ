package com.deenzstudios.waktusolatdz.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deenzstudios.waktusolatdz.data.IslamicEvents
import com.deenzstudios.waktusolatdz.data.PrayerCache
import com.deenzstudios.waktusolatdz.data.UpcomingIslamicEvent
import com.deenzstudios.waktusolatdz.data.upcomingIslamicEvents

/**
 * Jadualkan notifikasi tarikh penting Islam tepat pada 00:00 (awal hari kejadian).
 *
 * Tarikh Masihi dikira daripada tarikh Hijrah + Masihi yang disimpan dalam
 * [PrayerCache] (data JAKIM terakhir), jadi penjadualan ini tidak perlukan internet.
 * Jadual dipasang semula setiap kali app dibuka, selepas but, dan selepas
 * penyegaran jadual harian.
 */
class IslamicEventAlarmScheduler(context: Context) {

    private val appContext = context.applicationContext

    /** Pasang semula semua alarm tarikh penting. Selamat dipanggil berulang kali. */
    fun schedule() {
        val alarmManager = alarmManager() ?: return
        val cached = PrayerCache(appContext).load() ?: return
        val events = upcomingIslamicEvents(
            hijriDate = cached.hijriDate,
            gregorianDate = cached.date,
            count = IslamicEvents.size,
        )

        cancel()
        val now = System.currentTimeMillis()
        events.forEachIndexed { index, event ->
            // Acara hari ini (00:00 sudah berlalu) tidak perlu dijadualkan.
            if (event.dateMillis <= now) return@forEachIndexed
            val pendingIntent = eventPendingIntent(EVENT_CODE_BASE + index, event)

            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.dateMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.dateMillis,
                    pendingIntent,
                )
            }
        }
    }

    /** Batalkan semua alarm tarikh penting. */
    fun cancel() {
        val alarmManager = alarmManager() ?: return
        repeat(IslamicEvents.size) { index ->
            alarmManager.cancel(eventPendingIntent(EVENT_CODE_BASE + index, null))
        }
    }

    private fun eventPendingIntent(
        code: Int,
        event: UpcomingIslamicEvent?,
    ): PendingIntent {
        val intent = Intent(appContext, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_ISLAMIC_EVENT
            if (event != null) {
                putExtra(PrayerAlarmReceiver.EXTRA_TITLE, event.title)
                putExtra(PrayerAlarmReceiver.EXTRA_MESSAGE, event.message)
                putExtra(PrayerAlarmReceiver.EXTRA_EVENT_DATE, event.hijriLabel)
                putExtra(
                    PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID,
                    NOTIFICATION_ID_BASE + (code - EVENT_CODE_BASE),
                )
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(): AlarmManager? =
        appContext.getSystemService(AlarmManager::class.java)

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private companion object {
        const val EVENT_CODE_BASE = 21_000
        const val NOTIFICATION_ID_BASE = 5_000
    }
}
