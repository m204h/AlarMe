package com.example.alarmy

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AddEditAlarmActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AddEditAlarmActivity"
    }

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

    private lateinit var layoutSelectSound: LinearLayout
    private lateinit var tvSelectedSound: TextView

    private var alarmId: Int = -1
    private var existingAlarm: Alarm? = null

    private var selectedSoundUri: String = ""
    private var previewMediaPlayer: MediaPlayer? = null

    data class SystemSound(val title: String, val uri: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
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

        layoutSelectSound = findViewById(R.id.layoutSelectSound)
        tvSelectedSound = findViewById(R.id.tvSelectedSound)

        alarmId = intent.getIntExtra("EXTRA_ALARM_ID", -1)
        val storage = AlarmStorage(this)

        if (alarmId != -1) {
            existingAlarm = storage.getAlarms().find { it.id == alarmId }
        }

        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        existingAlarm?.let { alarm ->
            tvTitle.setText(R.string.edit_alarm)
            timePicker.hour = alarm.hour
            timePicker.minute = alarm.minute
            etAlarmLabel.setText(alarm.label)

            val days = alarm.daysOfWeek
            btnMon.isChecked = days.contains(Calendar.MONDAY)
            btnTue.isChecked = days.contains(Calendar.TUESDAY)
            btnWed.isChecked = days.contains(Calendar.WEDNESDAY)
            btnThu.isChecked = days.contains(Calendar.THURSDAY)
            btnFri.isChecked = days.contains(Calendar.FRIDAY)
            btnSat.isChecked = days.contains(Calendar.SATURDAY)
            btnSun.isChecked = days.contains(Calendar.SUNDAY)

            selectedSoundUri = alarm.soundUri
        } ?: run {
            tvTitle.setText(R.string.new_alarm)
            val calendar = Calendar.getInstance()
            timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = calendar.get(Calendar.MINUTE)
            etAlarmLabel.setText(getString(R.string.alarm_hint))
            selectedSoundUri = ""
        }

        updateSelectedSoundDisplay()

        layoutSelectSound.setOnClickListener {
            showSoundSelectionDialog()
        }

        btnEveryDay.setOnClickListener { setAllDaysChecked(true) }
        btnOnce.setOnClickListener { setAllDaysChecked(false) }

        btnCancel.setOnClickListener {
            stopPreviewSound()
            finish()
        }

        btnSave.setOnClickListener {
            stopPreviewSound()

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

            val alarmToSave = existingAlarm?.copy(
                hour = hour,
                minute = minute,
                isEnabled = true,
                label = label,
                daysOfWeek = daysOfWeek,
                soundUri = selectedSoundUri
            ) ?: run {
                val newId = (System.currentTimeMillis() % 1_000_000_000).toInt().let { if (it < 0) -it else it }
                Alarm(
                    id = newId,
                    hour = hour,
                    minute = minute,
                    isEnabled = true,
                    label = label,
                    daysOfWeek = daysOfWeek,
                    soundUri = selectedSoundUri
                )
            }

            val index = alarms.indexOfFirst { it.id == alarmToSave.id }
            if (index != -1) {
                alarms[index] = alarmToSave
            } else {
                alarms.add(alarmToSave)
            }

            storage.saveAlarms(alarms)
            AlarmScheduler.scheduleAlarm(this, alarmToSave)

            finish()
        }
    }

    private fun setAllDaysChecked(checked: Boolean) {
        btnMon.isChecked = checked
        btnTue.isChecked = checked
        btnWed.isChecked = checked
        btnThu.isChecked = checked
        btnFri.isChecked = checked
        btnSat.isChecked = checked
        btnSun.isChecked = checked
    }

    private fun getAvailableSystemAlarmSounds(): List<SystemSound> {
        val sounds = mutableListOf<SystemSound>()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val defaultTitle = getString(R.string.default_alarm_sound)

        sounds.add(SystemSound(defaultTitle, defaultUri?.toString() ?: ""))

        try {
            val rm = RingtoneManager(this).apply {
                setType(RingtoneManager.TYPE_ALARM)
            }
            val cursor = rm.cursor
            while (cursor != null && cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cursor.position)
                if (uri != null && uri.toString() != defaultUri?.toString()) {
                    sounds.add(SystemSound(title ?: "Alarm Sound", uri.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying system alarm sounds via RingtoneManager", e)
        }

        return sounds
    }

    private fun updateSelectedSoundDisplay() {
        if (selectedSoundUri.isEmpty()) {
            tvSelectedSound.setText(R.string.default_alarm_sound)
            return
        }

        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (selectedSoundUri == defaultUri?.toString()) {
                tvSelectedSound.setText(R.string.default_alarm_sound)
                return
            }

            val uri = Uri.parse(selectedSoundUri)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            val title = ringtone?.getTitle(this)
            if (!title.isNullOrEmpty()) {
                tvSelectedSound.text = title
            } else {
                tvSelectedSound.setText(R.string.default_alarm_sound)
            }
        } catch (e: Exception) {
            tvSelectedSound.setText(R.string.default_alarm_sound)
        }
    }

    private fun showSoundSelectionDialog() {
        val sounds = getAvailableSystemAlarmSounds()
        val titles = sounds.map { it.title }.toTypedArray()

        var selectedIndex = sounds.indexOfFirst { it.uri == selectedSoundUri }
        if (selectedIndex == -1) selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle(R.string.select_alarm_sound)
            .setSingleChoiceItems(titles, selectedIndex) { _, which ->
                selectedIndex = which
                val chosenSound = sounds[which]
                previewSound(chosenSound.uri)
            }
            .setPositiveButton("OK") { _, _ ->
                stopPreviewSound()
                selectedSoundUri = sounds[selectedIndex].uri
                updateSelectedSoundDisplay()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                stopPreviewSound()
            }
            .setOnDismissListener {
                stopPreviewSound()
            }
            .show()
    }

    private fun previewSound(uriStr: String) {
        stopPreviewSound()
        if (uriStr.isEmpty()) return

        try {
            val uri = Uri.parse(uriStr)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            previewMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(applicationContext, uri)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing sound: $uriStr", e)
        }
    }

    private fun stopPreviewSound() {
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreviewSound()
    }
}
