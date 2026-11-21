package com.example.alarmy

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat

object ThemeManager {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"

    fun getSavedTheme(context: Context): String {
        return try {
            val targetContext = if (UserManagerCompat.isUserUnlocked(context)) {
                context
            } else {
                try {
                    context.createDeviceProtectedStorageContext()
                } catch (_: Exception) {
                    context
                }
            }
            val prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
        } catch (_: Exception) {
            THEME_DARK
        }
    }

    fun setTheme(context: Context, theme: String) {
        try {
            if (UserManagerCompat.isUserUnlocked(context)) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putString(KEY_THEME, theme)
                }
            }
            try {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                        putString(KEY_THEME, theme)
                    }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }

        applyTheme(theme)
    }

    fun init(context: Context) {
        val theme = getSavedTheme(context)
        applyTheme(theme)
    }

    fun applyTheme(theme: String) {
        val mode = if (theme == THEME_LIGHT) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkTheme(context: Context): Boolean {
        return getSavedTheme(context) == THEME_DARK
    }
}
