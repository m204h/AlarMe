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
import androidx.core.net.toUri

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "AlarmService"

        const val ACTION_START_ALARM = "com.example.alarmy.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.alarmy.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.alarmy.ACTION_SNOOZE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.alarmy.ACTION_DISMISS_ALARM"

        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
        const val EXTRA_ALARM_LABEL = "EXTRA_ALARM_LABEL"

        private const val CHANNEL_ID = "alarm_service_channel"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes maximum alarm ringing
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(EXTRA_ALARM_LABEL) ?: Alarm.DEFAULT_LABEL

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
                val alarm = storage.getAlarms().find { it.id == alarmId }
                if (alarm != null) {
                    customSoundUri = alarm.soundUri
                    if (alarm.daysOfWeek.isNotEmpty()) {
                        // Calculate next occurrence for repeating alarm and clear snooze
                        val updated = alarm.copy(snoozeTriggerTimeMillis = 0L)
                        storage.updateAlarm(alarmId) { updated }
                        AlarmScheduler.scheduleAlarm(this, updated)
                    } else {
                        // One-time alarm: mark disabled in storage and clear snooze
                        val updated = alarm.copy(isEnabled = false, triggerTimeMillis = 0L, snoozeTriggerTimeMillis = 0L)
                        storage.updateAlarm(alarmId) { updated }
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
            .setContentTitle(getString(R.string.alarm_ringing))
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, getString(R.string.stop), stopPendingIntent)
            .addAction(0, getString(R.string.snooze), snoozePendingIntent)
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
            val candidateUris = mutableListOf<Uri>()

            // 1. Candidate: custom sound URI if provided
            if (soundUriStr.isNotEmpty()) {
                try {
                    candidateUris.add(soundUriStr.toUri())
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid custom sound URI: $soundUriStr", e)
                }
            }

            // 2. Candidates in priority order: default alarm, notification, ringtone
            listOf(
                RingtoneManager.TYPE_ALARM,
                RingtoneManager.TYPE_NOTIFICATION,
                RingtoneManager.TYPE_RINGTONE
            ).forEach { type ->
                RingtoneManager.getDefaultUri(type)?.let { candidateUris.add(it) }
            }

            for (uri in candidateUris) {
                try {
                    mediaPlayer = createAndStartMediaPlayer(uri)
                    Log.d(TAG, "Started MediaPlayer with sound: $uri")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to play sound: $uri. Trying fallback.", e)
                    mediaPlayer?.release()
                    mediaPlayer = null
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

        val player = MediaPlayer()
        try {
            player.setAudioAttributes(audioAttributes)
            player.setDataSource(applicationContext, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            return player
        } catch (e: Exception) {
            try {
                player.release()
            } catch (_: Exception) {}
            throw e
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

        val dismissIntent = Intent(ACTION_DISMISS_ALARM).apply {
            `package` = packageName
        }
        sendBroadcast(dismissIntent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_desc)
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
