package com.example.alarmy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("EXTRA_ALARM_ID", -1)
        val label = intent.getStringExtra("EXTRA_ALARM_LABEL") ?: "Alarm"

        Log.d(TAG, "onReceive triggered for alarmId=$alarmId, label='$label', action='${intent.action}'")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_LABEL, label)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Started AlarmService for alarmId=$alarmId")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AlarmService for alarmId=$alarmId", e)
        }
    }
}
