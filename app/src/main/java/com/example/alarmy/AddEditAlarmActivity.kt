package com.example.alarmy

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private val weekdayButtons by lazy {
        listOf(
            btnMon to Calendar.MONDAY,
            btnTue to Calendar.TUESDAY,
            btnWed to Calendar.WEDNESDAY,
            btnThu to Calendar.THURSDAY,
            btnFri to Calendar.FRIDAY,
            btnSat to Calendar.SATURDAY,
            btnSun to Calendar.SUNDAY
        )
    }

    private var alarmId: Int = -1
    private var existingAlarm: Alarm? = null

    private var selectedSoundUri: String = ""
    private var previewMediaPlayer: MediaPlayer? = null
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePickedAudio(uri)
    }

    data class SystemSound(val title: String, val uri: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_edit_alarm)
        setupWindowInsets()

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

        alarmId = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
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
            weekdayButtons.forEach { (button, day) ->
                button.isChecked = days.contains(day)
            }

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

            val daysOfWeek = weekdayButtons
                .filter { it.first.isChecked }
                .map { it.second }

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

            val calculatedTriggerTime = AlarmScheduler.calculateNextTriggerTime(alarmToSave, isNewOrUserEdit = true)
            val finalAlarm = alarmToSave.copy(triggerTimeMillis = calculatedTriggerTime)

            storage.saveOrUpdateAlarm(finalAlarm)
            AlarmScheduler.scheduleAlarm(this, finalAlarm)

            finish()
        }
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(R.id.addEditAlarmRoot)
        val initialLeft = rootView.paddingLeft
        val initialTop = rootView.paddingTop
        val initialRight = rootView.paddingRight
        val initialBottom = rootView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val topInset = systemBars.top
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            val leftInset = systemBars.left
            val rightInset = systemBars.right

            view.setPadding(
                initialLeft + leftInset,
                initialTop + topInset,
                initialRight + rightInset,
                initialBottom + bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun setAllDaysChecked(checked: Boolean) {
        weekdayButtons.forEach { (button, _) ->
            button.isChecked = checked
        }
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
            rm.cursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = rm.getRingtoneUri(cursor.position)
                    if (uri != null && uri.toString() != defaultUri?.toString()) {
                        sounds.add(SystemSound(title ?: getString(R.string.alarm_sound), uri.toString()))
                    }
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
            val uri = selectedSoundUri.toUri()
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (selectedSoundUri == defaultUri?.toString()) {
                tvSelectedSound.setText(R.string.default_alarm_sound)
            } else if (uri.scheme == "content" && uri.authority != "settings") {
                tvSelectedSound.text = queryAudioName(uri) ?: getString(R.string.custom_audio_unavailable)
            } else {
                val title = RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                tvSelectedSound.text = title ?: getString(R.string.default_alarm_sound)
            }
        } catch (e: Exception) {
            tvSelectedSound.setText(R.string.custom_audio_unavailable)
        }
    }

    private fun showSoundSelectionDialog() {
        val sounds = getAvailableSystemAlarmSounds()
        val titles = (sounds.map { it.title } + getString(R.string.choose_from_device)).toTypedArray()
        var selectedIndex = sounds.indexOfFirst { it.uri == selectedSoundUri }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.select_alarm_sound)
            .setSingleChoiceItems(titles, selectedIndex) { currentDialog, which ->
                if (which == sounds.size) {
                    stopPreviewSound()
                    currentDialog.dismiss()
                    audioPicker.launch(arrayOf("audio/*"))
                } else {
                    selectedIndex = which
                    previewSound(sounds[which].uri)
                }
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                stopPreviewSound()
                selectedSoundUri = sounds[selectedIndex].uri
                updateSelectedSoundDisplay()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> stopPreviewSound() }
            .setOnDismissListener { stopPreviewSound() }
            .show()
    }

    private fun handlePickedAudio(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            previewSound(uri.toString())
            if (previewMediaPlayer == null) return
            AlertDialog.Builder(this)
                .setTitle(queryAudioName(uri) ?: getString(R.string.custom_audio))
                .setMessage(R.string.preview_custom_audio)
                .setPositiveButton(R.string.use_this_sound) { _, _ ->
                    stopPreviewSound()
                    selectedSoundUri = uri.toString()
                    updateSelectedSoundDisplay()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> stopPreviewSound() }
                .setOnCancelListener { stopPreviewSound() }
                .show()
        } catch (e: Exception) {
            stopPreviewSound()
            Toast.makeText(this, R.string.custom_audio_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun queryAudioName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun previewSound(uriStr: String) {
        stopPreviewSound()
        if (uriStr.isEmpty()) return

        var player: MediaPlayer? = null
        try {
            val uri = uriStr.toUri()
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            player.setDataSource(applicationContext, uri)
            player.prepare()
            player.start()
            previewMediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing sound: $uriStr", e)
            try {
                player?.release()
            } catch (_: Exception) {}
            previewMediaPlayer = null
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
