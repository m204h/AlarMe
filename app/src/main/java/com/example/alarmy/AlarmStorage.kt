package com.example.alarmy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat

class AlarmStorage(private val context: Context) {

    private val bootStorage = AlarmBootStorage(context)

    companion object {
        private const val TAG = "AlarmStorage"
        private const val PREFS_NAME = "alarm_prefs"
        private const val KEY_ALARMS = "alarms_list"
    }

    private fun getPrimaryPrefs(): SharedPreferences? {
        return if (UserManagerCompat.isUserUnlocked(context)) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get primary SharedPreferences even though unlocked", e)
                null
            }
        } else {
            null
        }
    }

    fun getAlarms(): List<Alarm> {
        val primary = getPrimaryPrefs()
        if (primary == null) {
            // User is locked (Direct Boot mode): read from Device Protected Storage
            Log.d(TAG, "User locked: reading alarms from Device Protected Storage")
            return bootStorage.getBootAlarms()
        }

        // User is unlocked: reconcile any pending state from boot storage first
        if (bootStorage.hasPendingSync()) {
            reconcileBootChangesWithPrimary(primary)
        }

        val jsonStr = primary.getString(KEY_ALARMS, "[]") ?: "[]"
        val alarms = Alarm.listFromJsonStringResult(jsonStr).getOrDefault(emptyList())

        // If boot storage is empty (first run or migration), populate it from primary storage
        if (alarms.isNotEmpty()) {
            val bootResult = bootStorage.getBootAlarmsResult()
            if (bootResult.isSuccess && bootResult.getOrNull()?.isEmpty() == true) {
                Log.d(TAG, "Migrating ${alarms.size} alarms from Primary to Boot Storage")
                bootStorage.saveBootAlarms(alarms)
            }
        }

        return alarms
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val primary = getPrimaryPrefs()
        if (primary == null) {
            // User is locked: write directly to Device Protected Storage and flag for sync upon unlock
            Log.d(TAG, "User locked: saving ${alarms.size} alarms to Device Protected Storage")
            val success = bootStorage.saveBootAlarms(alarms)
            if (success) {
                bootStorage.markPendingSync(pending = true)
            }
            return
        }

        primary.edit { putString(KEY_ALARMS, Alarm.listToJsonString(alarms)) }
        Log.d(TAG, "Saved ${alarms.size} alarms to Primary SharedPreferences")

        // Keep Device Protected Storage synchronized with minimal alarm information
        val success = bootStorage.saveBootAlarms(alarms)
        if (success) {
            bootStorage.markPendingSync(false)
        }
    }

    fun updateAlarm(alarmId: Int, update: (Alarm) -> Alarm): Boolean {
        val alarms = getAlarms().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarmId }
        return if (index != -1) {
            alarms[index] = update(alarms[index])
            saveAlarms(alarms)
            true
        } else {
            false
        }
    }

    fun saveOrUpdateAlarm(alarm: Alarm) {
        val alarms = getAlarms().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = alarm
        } else {
            alarms.add(alarm)
        }
        saveAlarms(alarms)
    }

    fun deleteAlarm(alarmId: Int): Boolean {
        val alarms = getAlarms().toMutableList()
        val removed = alarms.removeAll { it.id == alarmId }
        if (removed) {
            saveAlarms(alarms)
        }
        return removed
    }

    fun syncOnUnlock() {
        if (!UserManagerCompat.isUserUnlocked(context)) {
            Log.w(TAG, "syncOnUnlock called while user is still locked. Skipping.")
            return
        }
        val primary = getPrimaryPrefs() ?: return
        reconcileBootChangesWithPrimary(primary)
        // Ensure boot storage has the latest copy of alarms from primary storage
        val currentAlarms = getAlarms()
        val success = bootStorage.saveBootAlarms(currentAlarms)
        if (success) {
            bootStorage.markPendingSync(false)
        }
        Log.d(TAG, "syncOnUnlock completed successfully. Synchronized ${currentAlarms.size} alarms.")
    }

    private fun reconcileBootChangesWithPrimary(primary: SharedPreferences) {
        try {
            val bootResult = bootStorage.getBootAlarmsResult()
            if (bootResult.isFailure) {
                Log.e(TAG, "Failed to read boot storage during reconciliation; preserving primary storage")
                return
            }
            val bootAlarms = bootResult.getOrThrow()
            val jsonStr = primary.getString(KEY_ALARMS, "[]") ?: "[]"
            val primaryAlarms = Alarm.listFromJsonStringResult(jsonStr).getOrDefault(emptyList()).toMutableList()

            var modified = false
            for (bootAlarm in bootAlarms) {
                val pIndex = primaryAlarms.indexOfFirst { it.id == bootAlarm.id }
                if (pIndex != -1) {
                    val pAlarm = primaryAlarms[pIndex]
                    if (pAlarm.isEnabled != bootAlarm.isEnabled ||
                        pAlarm.triggerTimeMillis != bootAlarm.triggerTimeMillis ||
                        pAlarm.snoozeTriggerTimeMillis != bootAlarm.snoozeTriggerTimeMillis
                    ) {
                        primaryAlarms[pIndex] = pAlarm.copy(
                            isEnabled = bootAlarm.isEnabled,
                            triggerTimeMillis = bootAlarm.triggerTimeMillis,
                            snoozeTriggerTimeMillis = bootAlarm.snoozeTriggerTimeMillis
                        )
                        modified = true
                    }
                } else {
                    primaryAlarms.add(bootAlarm)
                    modified = true
                }
            }

            if (bootStorage.hasPendingSync()) {
                val bootIds = bootAlarms.map { it.id }.toSet()
                if (primaryAlarms.removeAll { it.id !in bootIds }) {
                    modified = true
                }
            }

            if (modified) {
                primary.edit { putString(KEY_ALARMS, Alarm.listToJsonString(primaryAlarms)) }
                Log.d(TAG, "Reconciled boot storage changes into primary storage.")
            }
            bootStorage.markPendingSync(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling boot changes with primary", e)
        }
    }
}
