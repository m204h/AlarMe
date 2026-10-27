package com.example.alarmy

import android.content.Context
import android.os.Build
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FeedbackDialog {

    fun show(context: Context, diagnosticManager: DiagnosticManager) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_feedback, null)
        val radioGroupType = dialogView.findViewById<RadioGroup>(R.id.radioGroupType)
        val etDescription = dialogView.findViewById<EditText>(R.id.etFeedbackDesc)
        val etEmail = dialogView.findViewById<EditText>(R.id.etFeedbackEmail)
        val cbDiagnostics = dialogView.findViewById<CheckBox>(R.id.cbIncludeDiagnostics)
        val pbSubmitting = dialogView.findViewById<ProgressBar>(R.id.pbSubmitting)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitFeedback)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.feedback_title)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        btnSubmit.setOnClickListener {
            val desc = etDescription.text.toString().trim()
            if (desc.isEmpty()) {
                etDescription.error = context.getString(R.string.feedback_error_empty)
                return@setOnClickListener
            }
            if (desc.length > 5000) {
                etDescription.error = "Message is too long (max 5000 characters)"
                return@setOnClickListener
            }

            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address"
                return@setOnClickListener
            }

            val typeId = radioGroupType.checkedRadioButtonId
            val typeStr = when (typeId) {
                R.id.radioBug -> "Bug Report"
                R.id.radioSuggestion -> "Feature Suggestion"
                else -> "General Feedback"
            }

            // Build Firestore Document Payload
            val feedbackData = mutableMapOf<String, Any>(
                "type" to typeStr,
                "message" to desc,
                "createdAt" to FieldValue.serverTimestamp(),
                "status" to "new"
            )

            if (email.isNotEmpty()) {
                feedbackData["contactEmail"] = email
            }

            if (cbDiagnostics.isChecked) {
                feedbackData["appVersion"] = "1.0 (1)"
                feedbackData["androidVersion"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                feedbackData["deviceModel"] = "${Build.MANUFACTURER} ${Build.MODEL}"
            }

            // Show Loading State
            pbSubmitting.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
            etDescription.isEnabled = false
            etEmail.isEnabled = false
            cbDiagnostics.isEnabled = false
            for (i in 0 until radioGroupType.childCount) {
                radioGroupType.getChildAt(i).isEnabled = false
            }

            // Submit directly to Cloud Firestore 'feedback' collection
            val db = FirebaseFirestore.getInstance()
            db.collection("feedback")
                .add(feedbackData)
                .addOnSuccessListener {
                    Toast.makeText(context, R.string.feedback_success, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    pbSubmitting.visibility = View.GONE
                    btnSubmit.isEnabled = true
                    etDescription.isEnabled = true
                    etEmail.isEnabled = true
                    cbDiagnostics.isEnabled = true
                    for (i in 0 until radioGroupType.childCount) {
                        radioGroupType.getChildAt(i).isEnabled = true
                    }
                    Toast.makeText(context, "Failed to submit feedback: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        dialog.show()
    }
}
