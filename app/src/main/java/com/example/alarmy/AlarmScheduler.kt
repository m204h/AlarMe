package com.example.alarmy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /**
     * Calculates the next valid trigger time in milliseconds for the given alarm.
     *
     * @param alarm The alarm to evaluate.
     * @param isNewOrUserEdit True if the user is explicitly saving or editing the alarm in the UI.
     * @param isTimeOrTimezoneChange True if handling a system clock or timezone change broadcast.
     * @return The next trigger timestamp in milliseconds, or -1L if an unrepeated alarm has already passed.
     */
    fun calculateNextTriggerTime(
        alarm: Alarm,
        isNewOrUserEdit: Boolean = false,
        isTimeOrTimezoneChange: Boolean = false
    ): Long {
        val nowMillis = System.currentTimeMillis()

        if (alarm.daysOfWeek.isNotEmpty()) {
            // Repeating alarm: calculate the next matching day strictly in the future
            for (i in 0..7) {
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, i)
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayOfWeek = targetCal[Calendar.DAY_OF_WEEK]
                if ((targetCal.timeInMillis > nowMillis) && alarm.daysOfWeek.contains(dayOfWeek)) {
                    return targetCal.timeInMillis
                }
            }
            // Fallback: 24 hours later if no day matched in window
            val fallbackCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (fallbackCal.timeInMillis <= nowMillis) {
                fallbackCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return fallbackCal.timeInMillis
        } else {
            // One-time alarm
            val todayCal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return if (isNewOrUserEdit || isTimeOrTimezoneChange) {
                // If user configured or clock/timezone changed, schedule for next valid occurrence (today or tomorrow)
                if (todayCal.timeInMillis <= nowMillis) {
                    todayCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                todayCal.timeInMillis
            } else {
                // Boot recovery or process recovery:
                if (alarm.triggerTimeMillis > 0L) {
                    if (alarm.triggerTimeMillis > nowMillis) {
                        alarm.triggerTimeMillis
                    } else {
                        // The scheduled trigger time has already passed
                        -1L
                    }
                } else {
                    if (todayCal.timeInMillis > nowMillis) {
                        todayCal.timeInMillis
                    } else {
                        // The scheduled time today has already passed
                        -1L
                    }
                }
            }
        }
    }

    private fun createAlarmPendingIntent(context: Context, alarmId: Int, label: String = ""): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.alarmy.ALARM_TRIGGER_$alarmId"
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            if (label.isNotEmpty()) {
                putExtra(AlarmService.EXTRA_ALARM_LABEL, label)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createShowPendingIntent(context: Context, alarmId: Int): PendingIntent {
        val showIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            alarmId,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAlarm(context: Context, alarm: Alarm, isTimeOrTimezoneChange: Boolean = false) {
        val now = System.currentTimeMillis()
        if (alarm.snoozeTriggerTimeMillis > now) {
            // Pending snooze active
            scheduleSnoozeIntent(context, alarm.id, alarm.label, alarm.snoozeTriggerTimeMillis)
            return
        } else if (alarm.snoozeTriggerTimeMillis > 0L) {
            // Expired snooze: clear it
            try {
                AlarmStorage(context).updateAlarm(alarm.id) {
                    it.copy(snoozeTriggerTimeMillis = 0L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing expired snooze", e)
            }
        }

        if (!alarm.isEnabled) {
            Log.d(TAG, "Alarm ${alarm.id} is disabled. Cancelling any scheduled intent.")
            cancelAlarm(context, alarm)
            return
        }

        val triggerTime = calculateNextTriggerTime(
            alarm,
            isNewOrUserEdit = false,
            isTimeOrTimezoneChange = isTimeOrTimezoneChange
        )

        if (triggerTime <= 0L) {
            Log.w(TAG, "One-time alarm ID=${alarm.id} scheduled time has already passed. Marking disabled.")
            cancelAlarm(context, alarm)
            try {
                AlarmStorage(context).updateAlarm(alarm.id) {
                    it.copy(isEnabled = false, triggerTimeMillis = 0L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating expired alarm in storage", e)
            }
            return
        }

        // Persist triggerTimeMillis if it changed
        if (alarm.triggerTimeMillis != triggerTime) {
            try {
                AlarmStorage(context).updateAlarm(alarm.id) {
                    it.copy(triggerTimeMillis = triggerTime)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting triggerTimeMillis", e)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarm.id, alarm.label)
        val showPendingIntent = createShowPendingIntent(context, alarm.id)

        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "Scheduling alarm ID=${alarm.id} ('${alarm.label}') for ${timeFormatter.format(triggerTime)}")

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Alarm ID=${alarm.id} set via setAlarmClock (Exact).")
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Exact alarm permission not granted. Fallback to setAndAllowWhileIdle for alarm ID=${alarm.id}.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm ID=${alarm.id}. Using setAndAllowWhileIdle fallback.", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm ID=${alarm.id}", e)
        }
    }

    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarm.id)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm ID=${alarm.id} in AlarmManager.")
    }

    fun snoozeAlarm(context: Context, alarmId: Int, label: String, snoozeMinutes: Int = 5) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "Snoozing alarm ID=$alarmId for $snoozeMinutes minutes (Trigger at ${timeFormatter.format(triggerTime)})")

        try {
            AlarmStorage(context).updateAlarm(alarmId) {
                it.copy(snoozeTriggerTimeMillis = triggerTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving snooze time in storage", e)
        }

        scheduleSnoozeIntent(context, alarmId, label, triggerTime)
    }

    private fun scheduleSnoozeIntent(context: Context, alarmId: Int, label: String, triggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarmId, label)
        val showPendingIntent = createShowPendingIntent(context, alarmId)

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent), pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while snoozing alarm ID=$alarmId", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun rescheduleAll(context: Context, alarms: List<Alarm>, isTimeOrTimezoneChange: Boolean = false) {
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (alarm.isEnabled || alarm.snoozeTriggerTimeMillis > now) {
                scheduleAlarm(context, alarm, isTimeOrTimezoneChange = isTimeOrTimezoneChange)
            }
        }
    }

    fun rescheduleAllEnabled(context: Context, alarms: List<Alarm>) {
        rescheduleAll(context, alarms, isTimeOrTimezoneChange = false)
    }

    fun recalculateAndRescheduleAll(context: Context, alarms: List<Alarm>) {
        rescheduleAll(context, alarms, isTimeOrTimezoneChange = true)
    }
}
