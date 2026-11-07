package com.deenzstudios.waktusolatdz.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deenzstudios.waktusolatdz.MainActivity
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.NotificationPreferences
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.data.IslamicEvents
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot

/**
 * Bina channel & paparkan notifikasi waktu solat.
 *
 * Channel sengaja tidak menetapkan bunyi sendiri, jadi Android menggunakan
 * bunyi notifikasi lalai sistem. Pengguna boleh tukar bunyi itu dalam
 * Tetapan Android > App > Waktu Solat DZ > Notifikasi.
 */
object PrayerNotifier {

    const val CHANNEL_REMINDER = "prayer_reminder"
    const val CHANNEL_ON_TIME = "prayer_time"
    const val CHANNEL_EVENT = "islamic_event"
    const val CHANNEL_SILENT = "prayer_time_silent"

    /** Cipta channel (API 26+). Selamat dipanggil berulang kali. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val localized = ContextCompat.getContextForLanguage(context)

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            localized.getString(R.string.notification_channel_reminder),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.notification_channel_reminder_description, NotificationPreferences.REMINDER_MINUTES)
            enableVibration(true)
        }

        val onTime = NotificationChannel(
            CHANNEL_ON_TIME,
            localized.getString(R.string.notification_channel_ontime),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.notification_channel_ontime_description)
            enableVibration(true)
        }

        val event = NotificationChannel(
            CHANNEL_EVENT,
            localized.getString(R.string.notification_channel_event),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localized.getString(R.string.notification_channel_event_description)
            enableVibration(true)
        }
        val silent = NotificationChannel(
            CHANNEL_SILENT,
            localized.getString(R.string.adhan_silent_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        NotificationManagerCompat.from(context)
            .createNotificationChannels(listOf(reminder, onTime, event, silent))
    }

    /** Notifikasi hanya boleh dipaparkan jika kebenaran POST_NOTIFICATIONS diberi. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        prayer: String,
        time: String,
        zone: String,
        reminder: Boolean,
        notificationId: Int,
        silent: Boolean = false,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)

        val localized = ContextCompat.getContextForLanguage(context)
        val prayerName = localizedPrayerName(localized, prayer)
        val zoneLabel = zoneLabel(zone)
        val title: String
        val body: String
        if (reminder) {
            title = localized.getString(R.string.notification_reminder_title, prayerName, NotificationPreferences.REMINDER_MINUTES)
            body = localized.getString(R.string.notification_reminder_body, prayerName, time, zoneLabel)
        } else {
            title = localized.getString(R.string.notification_ontime_title, prayerName)
            body = localized.getString(R.string.notification_ontime_body, time, zoneLabel)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            context,
            if (reminder) CHANNEL_REMINDER else if (silent) CHANNEL_SILENT else CHANNEL_ON_TIME,
        )
            .setSmallIcon(R.drawable.ic_stat_noti)
            .setLargeIcon(notificationLogo(context))
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (reminder) {
                    NotificationCompat.CATEGORY_REMINDER
                } else {
                    NotificationCompat.CATEGORY_ALARM
                },
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /** Notifikasi tarikh penting Islam (dijadualkan pada 00:00 hari kejadian). */
    @SuppressLint("MissingPermission")
    fun notifyIslamicEvent(
        context: Context,
        title: String,
        message: String,
        hijriLabel: String,
        notificationId: Int,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val localized = ContextCompat.getContextForLanguage(context)
        // Already scheduled alarms carry the original Malay text; match it at delivery time.
        val eventIndex = IslamicEvents.indexOfFirst { it.title == title && it.message == message }
        val titles = localized.resources.getStringArray(R.array.notification_event_titles)
        val messages = localized.resources.getStringArray(R.array.notification_event_messages)
        val localizedTitle = titles.getOrNull(eventIndex) ?: title
        val localizedMessage = messages.getOrNull(eventIndex) ?: message

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(R.drawable.ic_stat_noti)
            .setLargeIcon(notificationLogo(context))
            .setColor(GOLD)
            .setContentTitle(localizedTitle)
            .setContentText(localizedMessage)
            .setSubText(hijriLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun notificationLogo(context: Context): Bitmap? =
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_logo,
            BitmapFactory.Options().apply {
                inSampleSize = 8
                inScaled = false
            },
        )

    private fun localizedPrayerName(context: Context, prayer: String): String {
        val resource = when (PrayerTimeSlot.entries.firstOrNull { it.label == prayer }) {
            PrayerTimeSlot.IMSAK -> R.string.label_imsak
            PrayerTimeSlot.SUBUH -> R.string.prayer_fajr
            PrayerTimeSlot.SYURUK -> R.string.label_syuruk
            PrayerTimeSlot.DHUHA -> R.string.label_dhuha
            PrayerTimeSlot.ZOHOR -> R.string.prayer_dhuhr
            PrayerTimeSlot.ASAR -> R.string.prayer_asr
            PrayerTimeSlot.MAGHRIB -> R.string.prayer_maghrib
            PrayerTimeSlot.ISYAK -> R.string.prayer_isha
            null -> return prayer
        }
        return context.getString(resource)
    }

    private fun zoneLabel(zone: String): String {
        val info = PrayerZones.of(zone) ?: return zone
        return "${info.state} • ${info.district}"
    }

    /** Warna emas aplikasi untuk aksen notifikasi. */
    private const val GOLD = 0xFFC9A227.toInt()
}
