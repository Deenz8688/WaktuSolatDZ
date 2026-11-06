package com.deenzstudios.waktusolatdz.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.deenzstudios.waktusolatdz.MainActivity
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.NotificationPreferences
import com.deenzstudios.waktusolatdz.data.PrayerZones

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

    /** Cipta channel (API 26+). Selamat dipanggil berulang kali. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "Peringatan Solat",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "${NotificationPreferences.REMINDER_MINUTES} minit sebelum masuk waktu solat"
            enableVibration(true)
        }

        val onTime = NotificationChannel(
            CHANNEL_ON_TIME,
            "Masuk Waktu Solat",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifikasi tepat pada waktu solat"
            enableVibration(true)
        }

        val event = NotificationChannel(
            CHANNEL_EVENT,
            "Tarikh Penting Islam",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifikasi pada awal hari tarikh penting Islam"
            enableVibration(true)
        }

        NotificationManagerCompat.from(context)
            .createNotificationChannels(listOf(reminder, onTime, event))
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
    ) {
        if (!canPost(context)) return
        ensureChannels(context)

        val zoneLabel = zoneLabel(zone)
        val title: String
        val body: String
        if (reminder) {
            title = "$prayer ${NotificationPreferences.REMINDER_MINUTES} minit lagi"
            body = "Waktu $prayer masuk pukul $time • $zoneLabel"
        } else {
            title = "Masuk waktu $prayer"
            body = "Pukul $time • $zoneLabel. Jom tunaikan solat."
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
            if (reminder) CHANNEL_REMINDER else CHANNEL_ON_TIME,
        )
            .setSmallIcon(R.drawable.ic_stat_noti)
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
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText(hijriLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun zoneLabel(zone: String): String {
        val info = PrayerZones.of(zone) ?: return zone
        return "${info.state} • ${info.district}"
    }

    /** Warna emas aplikasi untuk aksen notifikasi. */
    private const val GOLD = 0xFFC9A227.toInt()
}
