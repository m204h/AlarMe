package com.example.alarmy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val storage = AlarmStorage(context)
            val alarms = storage.getAlarms()
            Log.d(TAG, "Restoring ${alarms.size} alarms after boot")
            for (alarm in alarms) {
                if (alarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(context, alarm)
                }
            }
        }
    }
}
