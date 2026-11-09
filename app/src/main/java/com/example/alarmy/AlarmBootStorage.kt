package com.example.alarmy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct Boot compatible storage mechanism using Device Protected Storage.
 *
 * This storage operates on Context.createDeviceProtectedStorageContext(), allowing
 * alarms to be recovered and scheduled before the user unlocks their device.
 */
class AlarmBootStorage(context: Context) {

    private val dpContext: Context = if (context.isDeviceProtectedStorage) {
        context
    } else {
        context.createDeviceProtectedStorageContext()
    }

    private val prefs: SharedPreferences by lazy {
        dpContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "AlarmBootStorage"
        private const val PREFS_NAME = "alarm_boot_prefs"
        private const val KEY_BOOT_ALARMS = "boot_alarms_list"
        private const val KEY_LAST_RECOVERY_BOOT_UPTIME = "last_recovery_boot_uptime"
        private const val KEY_PENDING_UNLOCK_SYNC = "pending_unlock_sync"
    }

    fun getBootAlarms(): List<Alarm> {
        val jsonStr = try {
            prefs.getString(KEY_BOOT_ALARMS, "[]") ?: "[]"
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing Device Protected SharedPreferences", e)
            return emptyList()
        }

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
            Log.e(TAG, "Error parsing boot alarms from JSON", e)
        }
        return alarms
    }

    fun saveBootAlarms(alarms: List<Alarm>) {
        try {
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
            prefs.edit { putString(KEY_BOOT_ALARMS, jsonArray.toString()) }
            Log.d(TAG, "Saved ${alarms.size} alarms to Device Protected Storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving boot alarms to Device Protected Storage", e)
        }
    }

    fun recordBootRecovery(uptimeMillis: Long) {
        try {
            prefs.edit { putLong(KEY_LAST_RECOVERY_BOOT_UPTIME, uptimeMillis) }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording boot recovery uptime", e)
        }
    }

    fun hasRecoveredForCurrentBoot(currentUptimeMillis: Long): Boolean {
        return try {
            val lastRecordedUptime = prefs.getLong(KEY_LAST_RECOVERY_BOOT_UPTIME, -1L)
            // SystemClock.elapsedRealtime() resets on boot.
            // If last recorded uptime was within the current boot cycle (i.e. > 0 and <= currentUptimeMillis),
            // then recovery already occurred during this boot cycle.
            lastRecordedUptime in 1L..currentUptimeMillis
        } catch (_: Exception) {
            false
        }
    }

    fun markPendingSync(pending: Boolean) {
        try {
            prefs.edit { putBoolean(KEY_PENDING_UNLOCK_SYNC, pending) }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting pending sync flag", e)
        }
    }

    fun hasPendingSync(): Boolean {
        return try {
            prefs.getBoolean(KEY_PENDING_UNLOCK_SYNC, false)
        } catch (_: Exception) {
            false
        }
    }
}
