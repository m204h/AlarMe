package com.example.alarmy

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

/**
 * Direct Boot compatible storage mechanism using Device Protected Storage.
 *
 * This storage operates on Context.createDeviceProtectedStorageContext(), allowing
 * alarms to be recovered and scheduled before the user unlocks their device.
 */
class AlarmBootStorage(private val context: Context) {

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
        private const val KEY_LAST_RECOVERY_BOOT_ID = "last_recovery_boot_id"
        private const val KEY_PENDING_UNLOCK_SYNC = "pending_unlock_sync"

        fun getBootSessionId(context: Context): String {
            return try {
                val bootCount = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    -1
                )
                if (bootCount != -1) {
                    "boot_count_$bootCount"
                } else {
                    val bootTimeSec = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 10000L
                    "boot_time_$bootTimeSec"
                }
            } catch (_: Exception) {
                val bootTimeSec = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 10000L
                "boot_time_$bootTimeSec"
            }
        }
    }

    fun getBootAlarmsResult(): Result<List<Alarm>> {
        val jsonStr = try {
            prefs.getString(KEY_BOOT_ALARMS, "[]") ?: "[]"
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing Device Protected SharedPreferences", e)
            return Result.failure(e)
        }
        return Alarm.listFromJsonStringResult(jsonStr)
    }

    fun getBootAlarms(): List<Alarm> {
        return getBootAlarmsResult().getOrDefault(emptyList())
    }

    fun saveBootAlarms(alarms: List<Alarm>): Boolean {
        return try {
            prefs.edit { putString(KEY_BOOT_ALARMS, Alarm.listToJsonString(alarms)) }
            Log.d(TAG, "Saved ${alarms.size} alarms to Device Protected Storage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving boot alarms to Device Protected Storage", e)
            false
        }
    }

    fun recordBootRecovery(bootSessionId: String = getBootSessionId(context)) {
        try {
            prefs.edit { putString(KEY_LAST_RECOVERY_BOOT_ID, bootSessionId) }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording boot recovery session ID", e)
        }
    }

    fun hasRecoveredForCurrentBoot(currentBootSessionId: String = getBootSessionId(context)): Boolean {
        return try {
            val lastRecordedBootId = prefs.getString(KEY_LAST_RECOVERY_BOOT_ID, null)
            lastRecordedBootId != null && lastRecordedBootId == currentBootSessionId
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
