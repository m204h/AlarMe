package com.example.alarmy

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

class DiagnosticManager(context: Context) {
    private val prefs = context.getSharedPreferences("diagnostic_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DiagnosticManager"
        private const val PREF_CONSENT_ENABLED = "pref_consent_enabled"
    }

    init {
        val enabled = isConsentEnabled()
        applyConsentToCrashlytics(enabled)
    }

    fun isConsentEnabled(): Boolean {
        return prefs.getBoolean(PREF_CONSENT_ENABLED, true)
    }

    fun setConsentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_CONSENT_ENABLED, enabled).apply()
        applyConsentToCrashlytics(enabled)
        Log.d(TAG, "Diagnostic consent set to: $enabled")
    }

    private fun applyConsentToCrashlytics(enabled: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Crashlytics collection state", e)
        }
    }

    fun logMessage(message: String) {
        if (!isConsentEnabled()) return
        try {
            FirebaseCrashlytics.getInstance().log(message)
            Log.d(TAG, "Diagnostic Log: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log message to Crashlytics", e)
        }
    }

    fun logNonFatal(throwable: Throwable, contextMessage: String) {
        if (!isConsentEnabled()) return
        try {
            logMessage("Non-fatal error context: $contextMessage")
            FirebaseCrashlytics.getInstance().recordException(throwable)
            Log.e(TAG, "Recorded non-fatal exception: $contextMessage", throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record non-fatal exception", e)
        }
    }

    fun setCustomKey(key: String, value: String) {
        if (!isConsentEnabled()) return
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key", e)
        }
    }

    fun getDiagnosticSummary(context: Context): String {
        return buildString {
            append("App Version: 1.0 (1)\n")
            append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Diagnostics Consent: ${isConsentEnabled()}")
        }
    }
}
