package com.deenzstudios.waktusolatdz.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Alarm hilang selepas peranti dimulakan semula. Pasang semula jadual tersimpan
 * dahulu (tanpa internet), kemudian cuba ambil data terbaru di latar belakang.
 */
class PrayerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Dihantar sistem apabila kebenaran "Alarm & peringatan" diberi.
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            -> rescheduleAsync(context)
        }
    }

    private fun rescheduleAsync(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val scheduler = PrayerAlarmScheduler(appContext)
            try {
                scheduler.rearm()
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { scheduler.refresh() }
            } catch (_: Exception) {
                // Jadual tersimpan sudah dipasang semula; data baru akan diambil kemudian.
            } finally {
                try {
                    IslamicEventAlarmScheduler(appContext).schedule()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 8_000L

        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31). */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
