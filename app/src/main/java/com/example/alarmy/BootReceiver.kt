package com.example.alarmy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.os.UserManagerCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isUnlocked = UserManagerCompat.isUserUnlocked(context)
        val currentUptime = SystemClock.elapsedRealtime()

        Log.d(TAG, "onReceive action=$action, userUnlocked=$isUnlocked, currentUptime=$currentUptime")

        val bootStorage = AlarmBootStorage(context)
        val storage = AlarmStorage(context)

        try {
            when (action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    Log.d(TAG, "Handling ACTION_LOCKED_BOOT_COMPLETED (Direct Boot mode)")
                    // In locked boot, we only access Device Protected Storage
                    val bootAlarms = bootStorage.getBootAlarms()
                    Log.d(TAG, "Restoring ${bootAlarms.size} alarms from Device Protected Storage")

                    for (alarm in bootAlarms) {
                        if (alarm.isEnabled) {
                            AlarmScheduler.scheduleAlarm(context, alarm)
                        }
                    }
                    bootStorage.recordBootRecovery(currentUptime)
                }

                Intent.ACTION_USER_UNLOCKED -> {
                    Log.d(TAG, "Handling ACTION_USER_UNLOCKED: syncing storage after unlock")
                    if (isUnlocked) {
                        storage.syncOnUnlock()
                    }
                }

                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                    Log.d(TAG, "Handling $action (User unlocked=$isUnlocked)")
                    if (isUnlocked) {
                        // User is unlocked: sync primary and boot storages
                        storage.syncOnUnlock()
                    }

                    // Check if alarms were already recovered for this boot session in LOCKED_BOOT_COMPLETED
                    val alreadyRecovered = bootStorage.hasRecoveredForCurrentBoot(currentUptime)

                    if (alreadyRecovered) {
                        Log.d(TAG, "Alarms were already scheduled in LOCKED_BOOT_COMPLETED. Skipping duplicate scheduling.")
                    } else {
                        Log.d(TAG, "Performing alarm recovery (Locked boot did not run or first boot recovery)")
                        val alarms = storage.getAlarms()
                        for (alarm in alarms) {
                            if (alarm.isEnabled) {
                                AlarmScheduler.scheduleAlarm(context, alarm)
                            }
                        }
                        bootStorage.recordBootRecovery(currentUptime)
                    }
                }

                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    Log.d(TAG, "Handling $action: re-evaluating scheduled alarms")
                    val alarms = storage.getAlarms()
                    for (alarm in alarms) {
                        if (alarm.isEnabled) {
                            AlarmScheduler.scheduleAlarm(context, alarm)
                        }
                    }
                }

                else -> {
                    Log.w(TAG, "Unhandled action: $action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during boot receiver processing for action=$action", e)
        }
    }
}
