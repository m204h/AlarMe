package com.example.alarmy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat
import org.json.JSONArray
import org.json.JSONObject

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
        val alarms = mutableListOf<Alarm>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idLong = obj.optLong("id", System.currentTimeMillis())
                val idInt = (idLong % 1_000_000_000).toInt().let { if (it < 0) -it else it }

                val daysArray = obj.optJSONArray("daysOfWeek")
                val daysList = mutableListOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        daysList.add(daysArray.getInt(j))
                    }
                }

                alarms.add(
                    Alarm(
                        id = idInt,
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        isEnabled = obj.getBoolean("isEnabled"),
                        label = obj.optString("label", "Alarm"),
                        daysOfWeek = daysList,
                        soundUri = obj.optString("soundUri", ""),
                        triggerTimeMillis = obj.optLong("triggerTimeMillis", 0L),
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading alarms from SharedPreferences", e)
        }

        // If boot storage is empty (first run or migration), populate it from primary storage
        if (alarms.isNotEmpty() && bootStorage.getBootAlarms().isEmpty()) {
            Log.d(TAG, "Migrating ${alarms.size} alarms from Primary to Boot Storage")
            bootStorage.saveBootAlarms(alarms)
        }

        return alarms
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val primary = getPrimaryPrefs()
        if (primary == null) {
            // User is locked: write directly to Device Protected Storage and flag for sync upon unlock
            Log.d(TAG, "User locked: saving ${alarms.size} alarms to Device Protected Storage")
            bootStorage.saveBootAlarms(alarms)
            bootStorage.markPendingSync(pending = true)
            return
        }

        // User is unlocked: save to primary and synchronize to boot storage
        val jsonArray = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("isEnabled", alarm.isEnabled)
                put("label", alarm.label)
                put("daysOfWeek", JSONArray(alarm.daysOfWeek))
                put("soundUri", alarm.soundUri)
                put("triggerTimeMillis", alarm.triggerTimeMillis)
            }
            jsonArray.put(obj)
        }
        primary.edit { putString(KEY_ALARMS, jsonArray.toString()) }
        Log.d(TAG, "Saved ${alarms.size} alarms to Primary SharedPreferences")

        // Keep Device Protected Storage synchronized with minimal alarm information
        bootStorage.saveBootAlarms(alarms)
        bootStorage.markPendingSync(false)
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
        bootStorage.saveBootAlarms(currentAlarms)
        bootStorage.markPendingSync(false)
        Log.d(TAG, "syncOnUnlock completed successfully. Synchronized ${currentAlarms.size} alarms.")
    }

    private fun reconcileBootChangesWithPrimary(primary: SharedPreferences) {
        try {
            val bootAlarms = bootStorage.getBootAlarms()
            val primaryAlarms = mutableListOf<Alarm>()
            val jsonStr = primary.getString(KEY_ALARMS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idLong = obj.optLong("id", System.currentTimeMillis())
                val idInt = (idLong % 1_000_000_000).toInt().let { if (it < 0) -it else it }
                val daysArray = obj.optJSONArray("daysOfWeek")
                val daysList = mutableListOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        daysList.add(daysArray.getInt(j))
                    }
                }
                primaryAlarms.add(
                    Alarm(
                        id = idInt,
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        isEnabled = obj.getBoolean("isEnabled"),
                        label = obj.optString("label", "Alarm"),
                        daysOfWeek = daysList,
                        soundUri = obj.optString("soundUri", ""),
                        triggerTimeMillis = obj.optLong("triggerTimeMillis", 0L),
                    )
                )
            }

            var modified = false
            for (bootAlarm in bootAlarms) {
                val pIndex = primaryAlarms.indexOfFirst { it.id == bootAlarm.id }
                if (pIndex != -1) {
                    val pAlarm = primaryAlarms[pIndex]
                    if ((pAlarm.isEnabled != bootAlarm.isEnabled) || (pAlarm.triggerTimeMillis != bootAlarm.triggerTimeMillis)) {
                        primaryAlarms[pIndex] = pAlarm.copy(
                            isEnabled = bootAlarm.isEnabled,
                            triggerTimeMillis = bootAlarm.triggerTimeMillis
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
                val updatedJsonArray = JSONArray()
                for (alarm in primaryAlarms) {
                    val obj = JSONObject().apply {
                        put("id", alarm.id)
                        put("hour", alarm.hour)
                        put("minute", alarm.minute)
                        put("isEnabled", alarm.isEnabled)
                        put("label", alarm.label)
                        put("daysOfWeek", JSONArray(alarm.daysOfWeek))
                        put("soundUri", alarm.soundUri)
                        put("triggerTimeMillis", alarm.triggerTimeMillis)
                    }
                    updatedJsonArray.put(obj)
                }
                primary.edit { putString(KEY_ALARMS, updatedJsonArray.toString()) }
                Log.d(TAG, "Reconciled boot storage changes into primary storage.")
            }
            bootStorage.markPendingSync(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling boot changes with primary", e)
        }
    }
}
