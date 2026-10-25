package com.example.alarmy

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var storage: AlarmStorage
    private lateinit var adapter: AlarmAdapter
    private lateinit var alarms: MutableList<Alarm>

    private val REQUEST_CODE_NOTIFICATION_PERM = 101
    private var isCheckingPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = AlarmStorage(this)
        alarms = storage.getAlarms().toMutableList()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAlarms)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AlarmAdapter(
            alarms = alarms,
            onToggle = { alarm, isEnabled ->
                val index = alarms.indexOfFirst { it.id == alarm.id }
                if (index != -1) {
                    val updated = alarm.copy(isEnabled = isEnabled)
                    alarms[index] = updated
                    storage.saveAlarms(alarms)
                    if (isEnabled) {
                        AlarmScheduler.scheduleAlarm(this, updated)
                    } else {
                        AlarmScheduler.cancelAlarm(this, updated)
                    }
                    adapter.updateAlarms(alarms)
                }
            },
            onDelete = { alarm ->
                showDeleteDialog(alarm)
            },
            onEdit = { alarm ->
                showAddEditDialog(alarm)
            }
        )
        recyclerView.adapter = adapter

        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddAlarm)
        fabAdd.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Checking setup and syncing alarms")
        checkAndSetupPermissions()
    }

    private fun checkAndSetupPermissions() {
        if (isCheckingPermissions) return

        // Step 1: Notification Permission (Android 13+ / API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                isCheckingPermissions = true
                AlertDialog.Builder(this)
                    .setTitle("Notification Permission Needed")
                    .setMessage("Notification permission is required so Alarmy can alert you when an alarm fires.")
                    .setCancelable(false)
                    .setPositiveButton("Allow") { _, _ ->
                        isCheckingPermissions = false
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_NOTIFICATION_PERM
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        isCheckingPermissions = false
                        checkNextPermissionStep2()
                    }
                    .show()
                return
            }
        }

        checkNextPermissionStep2()
    }

    private fun checkNextPermissionStep2() {
        // Step 2: Exact Alarm Permission (Android 12+ / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                isCheckingPermissions = true
                AlertDialog.Builder(this)
                    .setTitle("Exact Alarm Permission Needed")
                    .setMessage("Alarmy needs Exact Alarm access to trigger alarms precisely on time.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        isCheckingPermissions = false
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Please allow Exact Alarms in System Settings", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        isCheckingPermissions = false
                        checkNextPermissionStep3()
                    }
                    .show()
                return
            }
        }

        checkNextPermissionStep3()
    }

    private fun checkNextPermissionStep3() {
        // Step 3: Full-Screen Intent Permission (Android 14+ / API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                isCheckingPermissions = true
                AlertDialog.Builder(this)
                    .setTitle("Full-Screen Alert Permission Needed")
                    .setMessage("Full-screen alert permission is required to open the ringing screen over your lock screen when an alarm fires.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        isCheckingPermissions = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                }
                                startActivity(intent)
                            } catch (e2: Exception) {
                                Toast.makeText(this, "Please allow Full Screen Intents in Settings", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        isCheckingPermissions = false
                        syncAndRescheduleAlarms()
                    }
                    .show()
                return
            }
        }

        syncAndRescheduleAlarms()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERM) {
            checkAndSetupPermissions()
        }
    }

    private fun syncAndRescheduleAlarms() {
        alarms = storage.getAlarms().toMutableList()
        adapter.updateAlarms(alarms)
        for (alarm in alarms) {
            if (alarm.isEnabled) {
                AlarmScheduler.scheduleAlarm(this, alarm)
            }
        }
    }

    private fun showAddEditDialog(existingAlarm: Alarm?) {
        val calendar = Calendar.getInstance()
        val initHour = existingAlarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initMinute = existingAlarm?.minute ?: calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hourOfDay, minute ->
            val labelDialogView = layoutInflater.inflate(R.layout.dialog_alarm_label, null)
            val etLabel = labelDialogView.findViewById<EditText>(R.id.etAlarmLabel)
            if (existingAlarm != null) {
                etLabel.setText(existingAlarm.label)
            }

            AlertDialog.Builder(this)
                .setTitle(if (existingAlarm == null) "Add Alarm" else "Edit Alarm")
                .setView(labelDialogView)
                .setPositiveButton("Save") { _, _ ->
                    val labelText = etLabel.text.toString().ifBlank { "Alarm" }
                    if (existingAlarm == null) {
                        val newId = (System.currentTimeMillis() % 1_000_000_000).toInt()
                        val newAlarm = Alarm(
                            id = if (newId < 0) -newId else newId,
                            hour = hourOfDay,
                            minute = minute,
                            isEnabled = true,
                            label = labelText
                        )
                        alarms.add(newAlarm)
                        storage.saveAlarms(alarms)
                        AlarmScheduler.scheduleAlarm(this, newAlarm)
                    } else {
                        val index = alarms.indexOfFirst { it.id == existingAlarm.id }
                        if (index != -1) {
                            val updated = existingAlarm.copy(
                                hour = hourOfDay,
                                minute = minute,
                                isEnabled = true,
                                label = labelText
                            )
                            alarms[index] = updated
                            storage.saveAlarms(alarms)
                            AlarmScheduler.scheduleAlarm(this, updated)
                        }
                    }
                    adapter.updateAlarms(alarms)
                }
                .setNegativeButton("Cancel", null)
                .show()

        }, initHour, initMinute, true).show()
    }

    private fun showDeleteDialog(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle("Delete Alarm")
            .setMessage("Are you sure you want to delete this alarm?")
            .setPositiveButton("Delete") { _, _ ->
                AlarmScheduler.cancelAlarm(this, alarm)
                alarms.removeAll { it.id == alarm.id }
                storage.saveAlarms(alarms)
                adapter.updateAlarms(alarms)
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
