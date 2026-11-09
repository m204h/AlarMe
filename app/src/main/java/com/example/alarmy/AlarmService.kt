package com.example.alarmy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "AlarmService"

        const val ACTION_START_ALARM = "com.example.alarmy.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.alarmy.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.alarmy.ACTION_SNOOZE_ALARM"

        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "EXTRA_ALARM_LABEL"

        private const val CHANNEL_ID = "alarm_service_channel"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes maximum alarm ringing
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(EXTRA_ALARM_LABEL) ?: "Alarm"

        Log.d(TAG, "onStartCommand action=$action, alarmId=$alarmId, label='$label'")

        when (action) {
            ACTION_START_ALARM -> {
                startAlarm(alarmId, label)
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
            ACTION_SNOOZE_ALARM -> {
                if (alarmId != -1) {
                    AlarmScheduler.snoozeAlarm(this, alarmId, label, 5)
                }
                stopAlarm()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAlarm(alarmId: Int, label: String) {
        acquireWakeLock()
        createNotificationChannel()

        val notifId = if (alarmId != -1) alarmId else 1001
        var customSoundUri = ""

        // Handle repeat days vs one-time alarm scheduling when ringing
        if (alarmId != -1) {
            try {
                val storage = AlarmStorage(this)
                val alarms = storage.getAlarms().toMutableList()
                val index = alarms.indexOfFirst { it.id == alarmId }
                if (index != -1) {
                    val alarm = alarms[index]
                    customSoundUri = alarm.soundUri
                    if (alarm.daysOfWeek.isNotEmpty()) {
                        // Calculate next occurrence for repeating alarm
                        AlarmScheduler.scheduleAlarm(this, alarm)
                    } else {
                        // One-time alarm: mark disabled in storage
                        val updated = alarm.copy(isEnabled = false, triggerTimeMillis = 0L)
                        alarms[index] = updated
                        storage.saveAlarms(alarms)
                        AlarmScheduler.cancelAlarm(this, updated)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating alarm state on ring", e)
            }
        }

        val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            notifId + 100000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            notifId + 200000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm Ringing")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
            Log.d(TAG, "startForeground success for notifId=$notifId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForeground", e)
        }

        startAudioAndVibration(customSoundUri)

        try {
            startActivity(ringingIntent)
            Log.d(TAG, "Launched AlarmRingingActivity")
        } catch (e: Exception) {
            Log.w(TAG, "Activity launch from background postponed to fullScreenIntent", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarMe:AlarmServiceWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(WAKELOCK_TIMEOUT_MS)
                }
                Log.d(TAG, "Acquired WakeLock for AlarmService")
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring WakeLock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d(TAG, "Released WakeLock")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun startAudioAndVibration(soundUriStr: String) {
        if (mediaPlayer == null) {
            var playerStarted = false

            // 1. Attempt to play custom sound URI if provided
            if (soundUriStr.isNotEmpty()) {
                try {
                    val customUri = Uri.parse(soundUriStr)
                    mediaPlayer = createAndStartMediaPlayer(customUri)
                    playerStarted = true
                    Log.d(TAG, "Started MediaPlayer with custom sound Uri: $customUri")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to play custom sound Uri (may be inaccessible before unlock). Falling back to default alarm.", e)
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }

            // 2. Fall back to system default alarm sound
            if (!playerStarted) {
                val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (defaultAlarmUri != null) {
                    try {
                        mediaPlayer = createAndStartMediaPlayer(defaultAlarmUri)
                        playerStarted = true
                        Log.d(TAG, "Started MediaPlayer with default alarm sound: $defaultAlarmUri")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to play default alarm sound. Trying notification sound fallback.", e)
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                }
            }

            // 3. Fall back to system default notification sound
            if (!playerStarted) {
                val defaultNotifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (defaultNotifUri != null) {
                    try {
                        mediaPlayer = createAndStartMediaPlayer(defaultNotifUri)
                        playerStarted = true
                        Log.d(TAG, "Started MediaPlayer with default notification sound: $defaultNotifUri")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to play default notification sound. Trying ringtone sound fallback.", e)
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                }
            }

            // 4. Fall back to system default ringtone
            if (!playerStarted) {
                val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (defaultRingtoneUri != null) {
                    try {
                        mediaPlayer = createAndStartMediaPlayer(defaultRingtoneUri)
                        Log.d(TAG, "Started MediaPlayer with default ringtone sound: $defaultRingtoneUri")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to play default ringtone sound.", e)
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                }
            }
        }

        if (vibrator == null) {
            try {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vibratorManager.defaultVibrator

                val pattern = longArrayOf(0, 1000, 1000)
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                Log.d(TAG, "Started Vibrator")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Vibrator", e)
            }
        }
    }

    private fun createAndStartMediaPlayer(uri: Uri): MediaPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(applicationContext, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm requested")
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        } finally {
            try {
                mediaPlayer?.release()
            } catch (e2: Exception) {
                Log.e(TAG, "Error releasing MediaPlayer", e2)
            }
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling Vibrator", e)
        }

        releaseWakeLock()

        val dismissIntent = Intent("com.example.alarmy.ACTION_DISMISS_ALARM")
        sendBroadcast(dismissIntent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active Alarm Service Notification"
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
