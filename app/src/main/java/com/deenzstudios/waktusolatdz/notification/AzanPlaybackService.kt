package com.deenzstudios.waktusolatdz.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot

/** Plays the selected full adhan only while this foreground service is active. */
class AzanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val localized = ContextCompat.getContextForLanguage(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PLAYBACK, localized.getString(R.string.adhan_playing_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setSound(null, null) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, AzanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_stat_noti)
            .setContentTitle(localized.getString(R.string.adhan_playing_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, localized.getString(R.string.adhan_stop), stopIntent)
            .build()
        startForeground(PLAYBACK_NOTIFICATION_ID, notification)

        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            stopSelf()
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        val focus = audioManager.requestAudioFocus(
            focusListener, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        player?.release()
        try {
            val audioResource = if (intent?.getStringExtra(EXTRA_PRAYER_SLOT) == PrayerTimeSlot.SUBUH.name)
                R.raw.azan_subuh else R.raw.azan_mekah_audio
            val descriptor = resources.openRawResourceFd(audioResource)
                ?: throw IllegalStateException("Audio unavailable")
            val mediaPlayer = MediaPlayer()
            player = mediaPlayer
            mediaPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                descriptor.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusListener)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PRAYER_SLOT = "prayer_slot"
        private const val CHANNEL_PLAYBACK = "adhan_playback"
        private const val ACTION_STOP = "com.deenzstudios.waktusolatdz.action.STOP_ADHAN"
        private const val PLAYBACK_NOTIFICATION_ID = 37_001
    }
}
