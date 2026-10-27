package com.example.alarmy

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class AlarmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AlarmStorage"
    }

    fun getAlarms(): List<Alarm> {
        val jsonStr = prefs.getString("alarms_list", "[]") ?: "[]"
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
                        daysOfWeek = daysList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading alarms from SharedPreferences", e)
        }
        return alarms
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val jsonArray = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("isEnabled", alarm.isEnabled)
                put("label", alarm.label)
                put("daysOfWeek", JSONArray(alarm.daysOfWeek))
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("alarms_list", jsonArray.toString()).apply()
        Log.d(TAG, "Saved ${alarms.size} alarms to SharedPreferences")
    }
}
