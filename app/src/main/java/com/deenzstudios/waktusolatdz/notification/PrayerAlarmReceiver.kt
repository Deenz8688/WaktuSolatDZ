package com.deenzstudios.waktusolatdz.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deenzstudios.waktusolatdz.widget.PrayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Terima alarm waktu solat: papar notifikasi atau segarkan jadual mingguan. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val prayer = intent.getStringExtra(EXTRA_PRAYER) ?: return
                val reminder = intent.getBooleanExtra(EXTRA_REMINDER, false)
                PrayerNotifier.notify(
                    context = context.applicationContext,
                    prayer = prayer,
                    time = intent.getStringExtra(EXTRA_TIME).orEmpty(),
                    zone = intent.getStringExtra(EXTRA_ZONE).orEmpty(),
                    reminder = reminder,
                    notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
                )
                // Pada waktu sebenar, tukar penanda "waktu semasa" dalam widget.
                if (!reminder) {
                    PrayerWidgetProvider.updateAll(context.applicationContext)
                }
            }

            ACTION_ISLAMIC_EVENT -> {
                PrayerNotifier.notifyIslamicEvent(
                    context = context.applicationContext,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                    hijriLabel = intent.getStringExtra(EXTRA_EVENT_DATE).orEmpty(),
                    notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
                )
            }

            ACTION_RESCHEDULE -> refreshAsync(context)
        }
    }

    /** Receiver hanya ada ~10s, jadi jadual disegarkan di latar belakang. */
    private fun refreshAsync(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    PrayerAlarmScheduler(appContext).refresh()
                }
            } catch (_: Exception) {
                // Abaikan: alarm sedia ada masih berfungsi sehingga app dibuka semula.
            } finally {
                // Tarikh penting dikira daripada cache, jadi ia tetap dipasang
                // walaupun permintaan rangkaian di atas gagal.
                IslamicEventAlarmScheduler(appContext).schedule()
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.deenzstudios.waktusolatdz.action.PRAYER_ALARM"
        const val ACTION_ISLAMIC_EVENT = "com.deenzstudios.waktusolatdz.action.ISLAMIC_EVENT"
        const val ACTION_RESCHEDULE = "com.deenzstudios.waktusolatdz.action.RESCHEDULE"

        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TIME = "time"
        const val EXTRA_ZONE = "zone"
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_EVENT_DATE = "event_date"

        private const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
