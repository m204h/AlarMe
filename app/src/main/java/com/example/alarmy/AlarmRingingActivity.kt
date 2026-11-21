package com.example.alarmy

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat

class AlarmRingingActivity : AppCompatActivity() {

    private var alarmId: Int = -1
    private var alarmLabel: String = Alarm.DEFAULT_LABEL

    companion object {
        private const val TAG = "AlarmRingingActivity"
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_DISMISS_ALARM) {
                Log.d(TAG, "Received ACTION_DISMISS_ALARM, finishing activity")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (UserManagerCompat.isUserUnlocked(this)) {
            try {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request dismiss keyguard", e)
            }
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        setContentView(R.layout.activity_alarm_ringing)

        alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
        alarmLabel = intent.getStringExtra(AlarmService.EXTRA_ALARM_LABEL) ?: getString(R.string.alarm_hint)

        Log.d(TAG, "onCreate alarmId=$alarmId, label='$alarmLabel'")

        val tvLabel = findViewById<TextView>(R.id.tvRingingLabel)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)

        tvLabel.text = alarmLabel

        btnStop.setOnClickListener {
            Log.d(TAG, "Stop clicked")
            val stopIntent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            }
            startService(stopIntent)
            finish()
        }

        btnSnooze.setOnClickListener {
            Log.d(TAG, "Snooze clicked")
            val snoozeIntent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_SNOOZE_ALARM
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmService.EXTRA_ALARM_LABEL, alarmLabel)
            }
            startService(snoozeIntent)
            finish()
        }

        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(AlarmService.ACTION_DISMISS_ALARM),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering dismissReceiver", e)
        }
    }
}
