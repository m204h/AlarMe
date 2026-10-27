package com.example.alarmy

import android.content.Context
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object DiagnosticsSettingsDialog {

    fun show(context: Context, diagnosticManager: DiagnosticManager) {
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_diagnostics, null)
        val cbEnable = dialogView.findViewById<CheckBox>(R.id.cbEnableDiagnostics)

        cbEnable.isChecked = diagnosticManager.isConsentEnabled()

        AlertDialog.Builder(context)
            .setTitle(R.string.diagnostics_title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val enabled = cbEnable.isChecked
                diagnosticManager.setConsentEnabled(enabled)
                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
