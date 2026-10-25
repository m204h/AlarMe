package com.example.alarmy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("EXTRA_ALARM_ID", -1)
        val label = intent.getStringExtra("EXTRA_ALARM_LABEL") ?: "Alarm"
        val action = intent.action ?: return

        Log.d(TAG, "onReceive action=$action, alarmId=$alarmId, label='$label'")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_LABEL, label)
        }

        context.startService(serviceIntent)
    }
}
