package com.example.alarmy

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

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
                    val newTriggerTime = if (isEnabled) {
                        AlarmScheduler.calculateNextTriggerTime(alarm.copy(isEnabled = true), isNewOrUserEdit = true)
                    } else {
                        0L
                    }
                    val updated = alarm.copy(isEnabled = isEnabled, triggerTimeMillis = newTriggerTime)
                    alarms[index] = updated
                    storage.saveAlarms(alarms)
                    if (isEnabled) {
                        AlarmScheduler.scheduleAlarm(this, updated)
                    } else {
                        AlarmScheduler.cancelAlarm(this, updated)
                    }
                    adapter.updateAlarms(alarms)
                    updateHeading()
                }
            },
            onDelete = { alarm ->
                showDeleteDialog(alarm)
            },
            onEdit = { alarm ->
                val intent = Intent(this, AddEditAlarmActivity::class.java).apply {
                    putExtra("EXTRA_ALARM_ID", alarm.id)
                }
                startActivity(intent)
            }
        )
        recyclerView.adapter = adapter
        updateHeading()

        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddAlarm)
        fabAdd.setOnClickListener {
            val intent = Intent(this, AddEditAlarmActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateHeading() {
        val tvHeading = findViewById<TextView>(R.id.tvAlarmHeading)
        if (alarms.isEmpty()) {
            tvHeading.text = getString(R.string.no_alarm)
            tvHeading.visibility = View.VISIBLE
        } else {
            tvHeading.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Checking setup and syncing alarms")
        checkAndSetupPermissions()
    }

    private fun checkAndSetupPermissions() {
        if (isCheckingPermissions) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                isCheckingPermissions = true
                AlertDialog.Builder(this)
                    .setTitle("Notification Permission Needed")
                    .setMessage("Notification permission is required so AlarMe can alert you when an alarm fires.")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                isCheckingPermissions = true
                AlertDialog.Builder(this)
                    .setTitle("Exact Alarm Permission Needed")
                    .setMessage("AlarMe needs Exact Alarm access to trigger alarms precisely on time.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        isCheckingPermissions = false
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (_: Exception) {
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
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                }
                                startActivity(intent)
                            } catch (_: Exception) {
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
        storage.syncOnUnlock()
        alarms = storage.getAlarms().toMutableList()
        adapter.updateAlarms(alarms)
        updateHeading()
        for (alarm in alarms) {
            if (alarm.isEnabled) {
                AlarmScheduler.scheduleAlarm(this, alarm)
            }
        }
    }

    private fun showDeleteDialog(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_alarm_title)
            .setMessage(R.string.delete_alarm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancelAlarm(this, alarm)
                alarms.removeAll { it.id == alarm.id }
                storage.saveAlarms(alarms)
                adapter.updateAlarms(alarms)
                updateHeading()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
