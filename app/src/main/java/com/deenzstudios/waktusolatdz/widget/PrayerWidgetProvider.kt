package com.deenzstudios.waktusolatdz.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.deenzstudios.waktusolatdz.MainActivity
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.PrayerCache
import com.deenzstudios.waktusolatdz.data.PrayerRepository
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.data.WeatherNow
import com.deenzstudios.waktusolatdz.data.WeatherRepository
import com.deenzstudios.waktusolatdz.data.WeatherStore
import com.deenzstudios.waktusolatdz.data.ZonePreferences
import com.deenzstudios.waktusolatdz.data.formatGregorianDate
import com.deenzstudios.waktusolatdz.data.formatHijriDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/**
 * Widget 4x2: papar solat seterusnya dan lima waktu fardu hari ini.
 *
 * Data dibaca daripada cache yang ditulis oleh app. Jika cache tiada atau
 * tarikhnya sudah lama, widget ambil data terbaru JAKIM di latar belakang.
 */
class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val appContext = context.applicationContext
        val cache = PrayerCache(appContext)
        val times = cache.load()

        if (times != null) {
            render(appContext, appWidgetManager, appWidgetIds, times)
        }

        if (times == null || !cache.isFresh()) {
            refreshAsync(appContext)
        }
    }

    override fun onEnabled(context: Context) {
        refreshAsync(context.applicationContext)
    }

    /**
     * Pemicu pada setiap masuk waktu solat supaya penanda "semasa" dalam widget
     * bertukar tepat pada waktunya (bukan menunggu kemas kini berkala 30 minit).
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TICK) return

        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(appContext, PrayerWidgetProvider::class.java),
        )
        if (ids.isEmpty()) {
            // Widget sudah dibuang: hentikan rantaian pemicu.
            cancelTicks(appContext)
            return
        }

        val cache = PrayerCache(appContext)
        updateAll(appContext)
        if (!cache.isFresh()) refreshAsync(appContext)
        scheduleTicks(appContext, cache.load())
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context.applicationContext)
    }

    private fun refreshAsync(context: Context) {
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val zone = ZonePreferences(context).selectedZone
                val times = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    PrayerRepository().getToday(zone)
                }
                if (times != null) {
                    PrayerCache(context).save(times)
                    updateAll(context)
                }
            } catch (_: Exception) {
                // Widget kekal dengan data cache (jika ada).
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REFRESH_TIMEOUT_MS = 8_000L
        private const val WEATHER_TIMEOUT_MS = 10_000L
        private const val DAY_MINUTES = 24 * 60
        private const val ACTION_TICK = "com.deenzstudios.waktusolatdz.action.WIDGET_TICK"
        private const val TICK_REQUEST_BASE = 5_000
        private const val TICK_COUNT = 5

        /** Papar semula widget daripada cache (selepas app memuatkan data baru). */
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, PrayerWidgetProvider::class.java),
            )
            val times = PrayerCache(appContext).load() ?: return
            render(appContext, manager, ids, times)
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            times: PrayerTimes,
        ) {
            if (ids.isEmpty()) return
            val weatherStore = WeatherStore(context)
            val localizedContext = ContextCompat.getContextForLanguage(context)
            manager.updateAppWidget(ids, buildViews(localizedContext, times, weatherStore.load()))
            scheduleTicks(context, times)
            if (!weatherStore.isFresh()) refreshWeatherAsync(context)
        }

        /**
         * Ambil suhu & cuaca terkini bagi zon terpilih (di latar belakang).
         * Widget kekal memakai bacaan cache jika rangkaian gagal.
         */
        private fun refreshWeatherAsync(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val zone = PrayerZones.of(ZonePreferences(context).selectedZone)
                    if (zone != null) {
                        val weather = withTimeoutOrNull(WEATHER_TIMEOUT_MS) {
                            WeatherRepository.current(zone.latitude, zone.longitude)
                        }
                        if (weather != null) {
                            WeatherStore(context).save(weather)
                            updateAll(context)
                        }
                    }
                } catch (_: Exception) {
                    // Cuaca tidak tersedia (tiada internet): guna cache sedia ada.
                }
            }
        }

        /**
         * Jadualkan pemicu pada setiap masuk waktu (hari ini & Subuh esok) supaya
         * penanda waktu semasa sentiasa tepat walaupun notifikasi dimatikan.
         */
        private fun scheduleTicks(context: Context, times: PrayerTimes?) {
            cancelTicks(context)
            if (times == null) return
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

            val now = System.currentTimeMillis()
            val slots = listOf(
                times.fajr,
                times.dhuhr,
                times.asr,
                times.maghrib,
                times.isha,
            )

            val triggers = slots.map { time ->
                val minutes = time.toMinutesOfDay()
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, minutes / 60)
                    set(Calendar.MINUTE, minutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var triggerAt = calendar.timeInMillis
                if (triggerAt <= now) triggerAt += 24 * 60 * 60 * 1000L
                triggerAt
            }.sorted()

            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            triggers.forEachIndexed { index, triggerAt ->
                val pendingIntent = tickPendingIntent(context, TICK_REQUEST_BASE + index)
                try {
                    if (exactAllowed) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent,
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent,
                        )
                    }
                } catch (_: SecurityException) {
                    // Kebenaran alarm tepat ditarik: guna alarm biasa.
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
            }
        }

        private fun cancelTicks(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            repeat(TICK_COUNT) { index ->
                alarmManager.cancel(tickPendingIntent(context, TICK_REQUEST_BASE + index))
            }
        }

        private fun tickPendingIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun buildViews(
            context: Context,
            times: PrayerTimes,
            weather: WeatherNow?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer)

            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_next_label, context.getString(R.string.widget_next_label))
            views.setTextViewText(R.id.widget_dates, datesLabel(context, times))
            views.setTextViewText(R.id.widget_zone, zoneLabel(times.zone))
            views.setTextViewText(R.id.widget_next, nextPrayer(context, times))

            val entries = prayerEntries(times)
            val current = currentPrayerLabel(entries)
            val prayerNames = listOf(R.string.label_imsak, R.string.prayer_fajr, R.string.label_syuruk,
                R.string.label_dhuha, R.string.prayer_dhuhr, R.string.prayer_asr,
                R.string.prayer_maghrib, R.string.prayer_isha)
            applyWeather(views, weather)
            val density = context.resources.displayMetrics.density
            val padHorizontal = (3 * density).toInt()
            val padVertical = (2 * density).toInt()

            entries.forEachIndexed { index, entry ->
                val active = entry.label == current
                views.setTextViewText(entry.labelViewId, context.getString(prayerNames[index]))
                views.setTextViewText(entry.viewId, entry.time)
                views.setTextColor(
                    entry.labelViewId,
                    context.getColor(if (active) R.color.widget_primary else R.color.widget_muted),
                )

                if (active) {
                    // Waktu solat semasa: teks ungu pada penanda cerah.
                    views.setTextColor(entry.viewId, context.getColor(R.color.widget_highlight_text))
                    views.setInt(
                        entry.viewId,
                        "setBackgroundResource",
                        R.drawable.bg_widget_time_active,
                    )
                    views.setViewPadding(
                        entry.viewId,
                        padHorizontal,
                        padVertical,
                        padHorizontal,
                        padVertical,
                    )
                } else {
                    // Padam penanda lama (jika RemoteViews diguna semula) supaya
                    // hanya satu waktu sahaja yang berkilau pada satu masa.
                    views.setTextColor(entry.viewId, context.getColor(R.color.widget_primary))
                    views.setInt(entry.viewId, "setBackgroundResource", 0)
                    views.setViewPadding(entry.viewId, 0, 0, 0, 0)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
            views.setContentDescription(R.id.widget_root, widgetDescription(context, times))

            return views
        }

        private data class PrayerEntry(
            val label: String,
            val time: String,
            val viewId: Int,
            val labelViewId: Int,
            val isFard: Boolean,
        )

        private fun prayerEntries(times: PrayerTimes): List<PrayerEntry> = listOf(
            PrayerEntry("Imsak", times.imsak, R.id.widget_imsak, R.id.widget_imsak_label, false),
            PrayerEntry("Subuh", times.fajr, R.id.widget_subuh, R.id.widget_subuh_label, true),
            PrayerEntry("Syuruk", times.syuruk, R.id.widget_syuruk, R.id.widget_syuruk_label, false),
            PrayerEntry("Dhuha", times.dhuha, R.id.widget_dhuha, R.id.widget_dhuha_label, false),
            PrayerEntry("Zohor", times.dhuhr, R.id.widget_zohor, R.id.widget_zohor_label, true),
            PrayerEntry("Asar", times.asr, R.id.widget_asar, R.id.widget_asar_label, true),
            PrayerEntry("Maghrib", times.maghrib, R.id.widget_maghrib, R.id.widget_maghrib_label, true),
            PrayerEntry("Isyak", times.isha, R.id.widget_isyak, R.id.widget_isyak_label, true),
        )

        /**
         * Waktu fardu yang sedang berlangsung: waktu fardu terakhir yang sudah masuk.
         * Imsak, Syuruk dan Dhuha tidak ditanda kerana bukan solat fardu.
         */
        private fun currentPrayerLabel(entries: List<PrayerEntry>): String {
            val now = Calendar.getInstance()
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val fardu = entries.filter { it.isFard }
            val passed = fardu.filter { it.time.toMinutesOfDay() <= nowMinutes }
            return passed.lastOrNull()?.label ?: fardu.last().label
        }

        private fun applyWeather(views: RemoteViews, weather: WeatherNow?) {
            if (weather == null) {
                // Tiada bacaan langsung: sembunyikan ikon & suhu.
                views.setViewVisibility(R.id.widget_weather_icon, View.GONE)
                views.setViewVisibility(R.id.widget_temp, View.GONE)
                return
            }
            views.setTextViewText(R.id.widget_temp, "${weather.temperature}°C")
            views.setImageViewResource(R.id.widget_weather_icon, weatherIcon(weather.kind))
            views.setViewVisibility(R.id.widget_weather_icon, View.VISIBLE)
            views.setViewVisibility(R.id.widget_temp, View.VISIBLE)
        }

        private fun weatherIcon(kind: WeatherNow.Kind): Int = when (kind) {
            WeatherNow.Kind.Sunny -> R.drawable.ic_weather_sun
            WeatherNow.Kind.Partly -> R.drawable.ic_weather_partly
            WeatherNow.Kind.Cloudy -> R.drawable.ic_weather_cloud
            WeatherNow.Kind.Rain -> R.drawable.ic_weather_rain
        }

        private fun zoneLabel(code: String): String {
            val zone = PrayerZones.of(code) ?: return code
            return "${zone.code} · ${zone.state}"
        }

        /** Baris tarikh: Hijri dan Masihi, contoh "6 Rabiulakhir 1448H · 17 Oktober 2026". */
        private fun datesLabel(context: Context, times: PrayerTimes): String {
            val date = times.date.split("-")
            val gregorian = if (context.resources.configuration.locales[0].language == "en" && date.size == 3) {
                val months = mapOf("Jan" to "January", "Feb" to "February", "Mac" to "March",
                    "Apr" to "April", "Mei" to "May", "Jun" to "June", "Jul" to "July",
                    "Ogos" to "August", "Sep" to "September", "Okt" to "October",
                    "Nov" to "November", "Dis" to "December")
                "${date[0].toIntOrNull() ?: date[0]} ${months[date[1]] ?: date[1]} ${date[2]}"
            } else formatGregorianDate(times.date)
            return "${formatHijriDate(times.hijriDate)} · $gregorian"
        }

        private fun nextPrayer(context: Context, times: PrayerTimes): String {
            val now = Calendar.getInstance()
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val slots = listOf(
                R.string.prayer_fajr to times.fajr,
                R.string.prayer_dhuhr to times.dhuhr,
                R.string.prayer_asr to times.asr,
                R.string.prayer_maghrib to times.maghrib,
                R.string.prayer_isha to times.isha,
            )
            val next = slots.firstOrNull { (_, time) -> time.toMinutesOfDay() > nowMinutes }
                ?: return context.getString(R.string.widget_next_tomorrow, context.getString(R.string.prayer_fajr), times.fajr)

            return "${context.getString(next.first)} · ${next.second}"
        }

        private fun widgetDescription(context: Context, times: PrayerTimes): String {
            val rows = prayerEntries(times)
            val names = listOf(R.string.label_imsak, R.string.prayer_fajr, R.string.label_syuruk,
                R.string.label_dhuha, R.string.prayer_dhuhr, R.string.prayer_asr,
                R.string.prayer_maghrib, R.string.prayer_isha)
            val readings = rows.zip(names).joinToString(", ") { (entry, name) ->
                "${context.getString(name)} ${entry.time}"
            }
            return context.getString(R.string.widget_accessible_summary,
                datesLabel(context, times), nextPrayer(context, times), readings)
        }

        private fun launchIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun String.toMinutesOfDay(): Int {
            val parts = split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return (hour * 60 + minute) % DAY_MINUTES
        }
    }
}
