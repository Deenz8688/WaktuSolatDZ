package com.deenzstudios.waktusolatdz.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deenzstudios.waktusolatdz.MainActivity
import com.deenzstudios.waktusolatdz.data.NotificationPreferences
import com.deenzstudios.waktusolatdz.data.NotificationSettings
import com.deenzstudios.waktusolatdz.data.PrayerCache
import com.deenzstudios.waktusolatdz.data.PrayerRepository
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.ZonePreferences
import com.deenzstudios.waktusolatdz.widget.PrayerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

/**
 * Jadualkan alarm bagi waktu yang dipilih pengguna (Imsak hingga Isyak) untuk
 * 8 hari akan datang (data mingguan JAKIM), termasuk peringatan sebelum masuk waktu.
 */
class PrayerAlarmScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val repository = PrayerRepository()
    private val preferences = NotificationPreferences(appContext)
    private val zones = ZonePreferences(appContext)
    private val store = NotificationScheduleStore(appContext)

    /**
     * Ambil jadual terbaru daripada JAKIM dan pasang semula semua alarm.
     *
     * @return bilangan notifikasi yang dijadualkan.
     */
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!preferences.enabled) {
                cancelEvents()
                cancelDailyRefresh()
                return@withLock 0
            }

            val settings = preferences.settings()
            if (settings.slots.isEmpty() || (!settings.remindBefore && !settings.notifyOnTime)) {
                cancelEvents()
                cancelDailyRefresh()
                return@withLock 0
            }

            // Ambil jadual baru DAHULU. Alarm lama hanya dibatalkan selepas data
            // baru tersedia, jadi notifikasi tidak hilang jika rangkaian gagal
            // atau app ditutup (di-clear) semasa permintaan sedang berjalan.
            val requestedZone = zones.selectedZone
            val week = repository.getWeek(requestedZone)
            val events = buildEvents(week, settings)

            // Settings or zone may change while the network request is in flight.
            if (preferences.settings() != settings || zones.selectedZone != requestedZone) {
                return@withLock 0
            }
            // Never replace a working schedule with unusable or empty API data.
            if (events.isEmpty()) throw IOException("Tiada jadual notifikasi masa depan yang sah")
            cancelEvents()
            store.save(events)
            events.forEach(::arm)
            armDailyRefresh()

            // Kemas kini cache & widget sekali dengan jadual notifikasi.
            week.firstOrNull()?.let { today ->
                PrayerCache(appContext).save(today)
                PrayerWidgetProvider.updateAll(appContext)
            }

            events.size
        }
    }

    /** Pasang semula alarm yang tersimpan (selepas but / tukar masa) tanpa internet. */
    suspend fun rearm() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!preferences.enabled) {
                cancelDailyRefresh()
                return@withLock
            }

            val now = System.currentTimeMillis()
            val events = store.load().filter { it.triggerAt > now }
            events.forEach(::arm)

            // Walaupun tiada acara tersimpan, penyegaran harian mesti dipasang
            // semula supaya jadual notifikasi tidak terus basi selepas but.
            armDailyRefresh()
        }
    }

    /** Batalkan semua alarm waktu solat dan buang jadual tersimpan. */
    fun cancelEvents() {
        val alarmManager = alarmManager() ?: return

        // Batalkan mengikut julat kod yang kita guna, bukan hanya acara tersimpan,
        // supaya tiada alarm lama tertinggal apabila jadual bertindih.
        repeat(MAX_DAYS * CODES_PER_DAY) { offset ->
            alarmManager.cancel(firePendingIntent(ALARM_CODE_BASE + offset))
        }
        store.clear()
    }

    /** Batalkan alarm waktu solat serta penyegaran harian. */
    suspend fun cancelAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cancelEvents()
            cancelDailyRefresh()
        }
    }

    private fun buildEvents(
        days: List<PrayerTimes>,
        settings: NotificationSettings,
    ): List<ScheduledEvent> {
        val now = System.currentTimeMillis()
        val reminderOffset = NotificationPreferences.REMINDER_MINUTES * 60_000L
        val events = mutableListOf<ScheduledEvent>()

        days.forEachIndexed { dayIndex, times ->
            val dayStart = startOfDay(times.date) ?: return@forEachIndexed

            PrayerTimeSlot.entries.forEachIndexed { prayerIndex, prayer ->
                if (prayer !in settings.slots) return@forEachIndexed
                val time = prayer.timeOf(times) ?: return@forEachIndexed
                val minutes = minutesOfDay(time) ?: return@forEachIndexed
                val triggerAt = dayStart + minutes * 60_000L
                val code = ALARM_CODE_BASE + dayIndex * CODES_PER_DAY + prayerIndex * 2
                val id = NOTIFICATION_ID_BASE + dayIndex * CODES_PER_DAY + prayerIndex * 2

                if (settings.remindBefore) {
                    val remindAt = triggerAt - reminderOffset
                    if (remindAt > now) {
                        events += ScheduledEvent(
                            triggerAt = remindAt,
                            prayer = prayer.label,
                            time = time,
                            zone = times.zone,
                            reminder = true,
                            alarmCode = code,
                            notificationId = id,
                        )
                    }
                }

                if (settings.notifyOnTime && triggerAt > now) {
                    events += ScheduledEvent(
                        triggerAt = triggerAt,
                        prayer = prayer.label,
                        time = time,
                        zone = times.zone,
                        reminder = false,
                        alarmCode = code + 1,
                        notificationId = id + 1,
                    )
                }
            }
        }

        return events.sortedBy { it.triggerAt }
    }

    private fun arm(event: ScheduledEvent) {
        val alarmManager = alarmManager() ?: return
        val pendingIntent = eventPendingIntent(event)

        try {
            if (canScheduleExact(alarmManager)) {
                // Allow-while-idle alarms share a per-app Doze quota, so a reminder
                // can delay the prayer alarm only five minutes later (including
                // when widget alarms use the same quota). User-facing prayer
                // alarms must use setAlarmClock to keep both delivery times.
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(event.triggerAt, showAlarmsPendingIntent()),
                    pendingIntent,
                )
                return
            }
        } catch (_: SecurityException) {
            // Access may be revoked between the permission check and scheduling.
        }

        // Best effort only: without exact-alarm access Android may delay delivery.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            event.triggerAt,
            pendingIntent,
        )
    }

    /** Android's next-alarm indicator opens the prayer timetable. */
    private fun showAlarmsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Segarkan jadual setiap awal pagi supaya 8 hari data sentiasa bergolek. */
    private fun armDailyRefresh() {
        val alarmManager = alarmManager() ?: return
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            dailyRefreshPendingIntent(),
        )
    }

    private fun eventPendingIntent(event: ScheduledEvent): PendingIntent =
        firePendingIntent(event.alarmCode, event)

    private fun firePendingIntent(
        code: Int,
        event: ScheduledEvent? = null,
    ): PendingIntent {
        val intent = Intent(appContext, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_FIRE
            if (event != null) {
                putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, event.prayer)
                putExtra(PrayerAlarmReceiver.EXTRA_TIME, event.time)
                putExtra(PrayerAlarmReceiver.EXTRA_ZONE, event.zone)
                putExtra(PrayerAlarmReceiver.EXTRA_REMINDER, event.reminder)
                putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, event.notificationId)
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelDailyRefresh() {
        alarmManager()?.cancel(dailyRefreshPendingIntent())
    }

    private fun dailyRefreshPendingIntent(): PendingIntent {
        val intent = Intent(appContext, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_RESCHEDULE
        }
        return PendingIntent.getBroadcast(
            appContext,
            DAILY_REFRESH_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(): AlarmManager? =
        appContext.getSystemService(AlarmManager::class.java)

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private companion object {
        /** Shared across all scheduler instances in this app process. */
        val mutex = Mutex()
        const val ALARM_CODE_BASE = 100
        const val NOTIFICATION_ID_BASE = 1_000
        const val CODES_PER_DAY = 16
        const val MAX_DAYS = 8
        const val DAILY_REFRESH_CODE = 9_000

        /** JAKIM pulangkan tarikh dalam format "17-Okt-2026". */
        val MONTHS = mapOf(
            "jan" to Calendar.JANUARY,
            "feb" to Calendar.FEBRUARY,
            "mac" to Calendar.MARCH,
            "apr" to Calendar.APRIL,
            "mei" to Calendar.MAY,
            "jun" to Calendar.JUNE,
            "jul" to Calendar.JULY,
            "ogo" to Calendar.AUGUST,
            "ogos" to Calendar.AUGUST,
            "aug" to Calendar.AUGUST,
            "mar" to Calendar.MARCH,
            "may" to Calendar.MAY,
            "dec" to Calendar.DECEMBER,
            "sep" to Calendar.SEPTEMBER,
            "okt" to Calendar.OCTOBER,
            "nov" to Calendar.NOVEMBER,
            "dis" to Calendar.DECEMBER,
        )

        fun startOfDay(date: String): Long? {
            val parts = date.split("-")
            if (parts.size != 3) return null
            val day = parts[0].toIntOrNull() ?: return null
            val month = MONTHS[parts[1].lowercase()] ?: return null
            val year = parts[2].toIntOrNull() ?: return null

            return Calendar.getInstance().apply {
                clear()
                set(year, month, day, 0, 0, 0)
            }.timeInMillis
        }

        fun minutesOfDay(time: String): Int? {
            val parts = time.split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
    }
}
