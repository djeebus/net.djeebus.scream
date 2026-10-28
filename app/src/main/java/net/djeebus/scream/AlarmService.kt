package net.djeebus.scream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import java.util.concurrent.TimeUnit

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalVolume = -1
    private var current: AlarmInfo? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopAlarm()
            ACTION_SNOOZE -> {
                current?.let { AlarmScheduler.snooze(this, it) }
                stopAlarm()
            }
            else -> startAlarm(AlarmInfo.from(intent ?: return START_NOT_STICKY))
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(info: AlarmInfo) {
        val firstAlarm = current == null
        current = info
        AlarmState.current.value = info

        startForeground(
            NOTIFICATION_ID, buildNotification(info), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (!firstAlarm) return // already ringing; just swap in the new details

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scream:alarm")
            .apply { acquire(MAX_RING_MILLIS) }

        playSound()
        startVibration()
        handler.postDelayed({ stopAlarm() }, MAX_RING_MILLIS)
    }

    private fun playSound() {
        val audio = getSystemService(AudioManager::class.java)
        // The alarm stream ignores the ringer switch, but not its own volume: max it for the alarm.
        originalVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0), attrs)
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        player?.run { runCatching { stop() }; release() }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        if (originalVolume >= 0) {
            getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            originalVolume = -1
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        current = null
        AlarmState.current.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (current != null) stopAlarm()
        super.onDestroy()
    }

    private fun buildNotification(info: AlarmInfo): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Loud alarms for calendar events you've marked"
                setSound(null, null) // sound is played by the service on the alarm stream
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(label: String, action: String) = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
            label,
            PendingIntent.getService(
                this, action.hashCode(), Intent(this, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(info.title)
            .setContentText("Starting soon")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action("Snooze ${AlarmScheduler.SNOOZE_MINUTES} min", ACTION_SNOOZE))
            .addAction(action("Dismiss", ACTION_DISMISS))
            .build()
    }

    companion object {
        const val ACTION_DISMISS = "net.djeebus.scream.DISMISS"
        const val ACTION_SNOOZE = "net.djeebus.scream.SNOOZE"
        private const val CHANNEL_ID = "alarm"
        private const val NOTIFICATION_ID = 1
        private val MAX_RING_MILLIS = TimeUnit.MINUTES.toMillis(10)

        fun startIntent(context: Context, info: AlarmInfo): Intent =
            info.putInto(Intent(context, AlarmService::class.java))

        fun commandIntent(context: Context, action: String): Intent =
            Intent(context, AlarmService::class.java).setAction(action)
    }
}
