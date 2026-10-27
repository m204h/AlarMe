package com.example.alarmy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var etAlarmLabel: EditText
    private lateinit var tvTitle: TextView
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button

    private lateinit var btnMon: ToggleButton
    private lateinit var btnTue: ToggleButton
    private lateinit var btnWed: ToggleButton
    private lateinit var btnThu: ToggleButton
    private lateinit var btnFri: ToggleButton
    private lateinit var btnSat: ToggleButton
    private lateinit var btnSun: ToggleButton

    private lateinit var btnEveryDay: Button
    private lateinit var btnOnce: Button

    private var alarmId: Int = -1
    private var existingAlarm: Alarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_alarm)

        timePicker = findViewById(R.id.timePicker)
        etAlarmLabel = findViewById(R.id.etAlarmLabel)
        tvTitle = findViewById(R.id.tvTitle)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)

        btnMon = findViewById(R.id.btnMon)
        btnTue = findViewById(R.id.btnTue)
        btnWed = findViewById(R.id.btnWed)
        btnThu = findViewById(R.id.btnThu)
        btnFri = findViewById(R.id.btnFri)
        btnSat = findViewById(R.id.btnSat)
        btnSun = findViewById(R.id.btnSun)

        btnEveryDay = findViewById(R.id.btnEveryDay)
        btnOnce = findViewById(R.id.btnOnce)

        alarmId = intent.getIntExtra("EXTRA_ALARM_ID", -1)
        val storage = AlarmStorage(this)

        if (alarmId != -1) {
            existingAlarm = storage.getAlarms().find { it.id == alarmId }
        }

        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        if (existingAlarm != null) {
            tvTitle.setText(R.string.edit_alarm)
            timePicker.hour = existingAlarm!!.hour
            timePicker.minute = existingAlarm!!.minute
            etAlarmLabel.setText(existingAlarm!!.label)

            val days = existingAlarm!!.daysOfWeek
            btnMon.isChecked = days.contains(Calendar.MONDAY)
            btnTue.isChecked = days.contains(Calendar.TUESDAY)
            btnWed.isChecked = days.contains(Calendar.WEDNESDAY)
            btnThu.isChecked = days.contains(Calendar.THURSDAY)
            btnFri.isChecked = days.contains(Calendar.FRIDAY)
            btnSat.isChecked = days.contains(Calendar.SATURDAY)
            btnSun.isChecked = days.contains(Calendar.SUNDAY)
        } else {
            tvTitle.setText(R.string.new_alarm)
            val calendar = Calendar.getInstance()
            timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = calendar.get(Calendar.MINUTE)
            etAlarmLabel.setText(getString(R.string.alarm_hint))
        }

        btnEveryDay.setOnClickListener {
            val check = true
            btnMon.isChecked = check
            btnTue.isChecked = check
            btnWed.isChecked = check
            btnThu.isChecked = check
            btnFri.isChecked = check
            btnSat.isChecked = check
            btnSun.isChecked = check
        }

        btnOnce.setOnClickListener {
            val check = false
            btnMon.isChecked = check
            btnTue.isChecked = check
            btnWed.isChecked = check
            btnThu.isChecked = check
            btnFri.isChecked = check
            btnSat.isChecked = check
            btnSun.isChecked = check
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val defaultLabel = getString(R.string.alarm_hint)
            val label = etAlarmLabel.text.toString().ifBlank { defaultLabel }

            val daysOfWeek = mutableListOf<Int>()
            if (btnMon.isChecked) daysOfWeek.add(Calendar.MONDAY)
            if (btnTue.isChecked) daysOfWeek.add(Calendar.TUESDAY)
            if (btnWed.isChecked) daysOfWeek.add(Calendar.WEDNESDAY)
            if (btnThu.isChecked) daysOfWeek.add(Calendar.THURSDAY)
            if (btnFri.isChecked) daysOfWeek.add(Calendar.FRIDAY)
            if (btnSat.isChecked) daysOfWeek.add(Calendar.SATURDAY)
            if (btnSun.isChecked) daysOfWeek.add(Calendar.SUNDAY)

            val alarms = storage.getAlarms().toMutableList()

            if (existingAlarm == null) {
                val newId = (System.currentTimeMillis() % 1_000_000_000).toInt()
                val newAlarm = Alarm(
                    id = if (newId < 0) -newId else newId,
                    hour = hour,
                    minute = minute,
                    isEnabled = true,
                    label = label,
                    daysOfWeek = daysOfWeek
                )
                alarms.add(newAlarm)
                storage.saveAlarms(alarms)
                AlarmScheduler.scheduleAlarm(this, newAlarm)
            } else {
                val index = alarms.indexOfFirst { it.id == existingAlarm!!.id }
                if (index != -1) {
                    val updated = existingAlarm!!.copy(
                        hour = hour,
                        minute = minute,
                        isEnabled = true,
                        label = label,
                        daysOfWeek = daysOfWeek
                    )
                    alarms[index] = updated
                    storage.saveAlarms(alarms)
                    AlarmScheduler.scheduleAlarm(this, updated)
                }
            }

            finish()
        }
    }
}
