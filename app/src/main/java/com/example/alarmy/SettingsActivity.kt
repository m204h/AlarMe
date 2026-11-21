package com.example.alarmy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class SettingsActivity : AppCompatActivity() {

    companion object {
        /**
         * Official website configuration constant.
         * Project website URL:
         * https://techmh.me
         */
        const val OFFICIAL_WEBSITE_URL = "https://techmh.me"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsLayout = findViewById<View>(R.id.settingsLayout)
        val appBarContainer = findViewById<View>(R.id.appBarContainer)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.menu_settings)
        }

        ViewCompat.setOnApplyWindowInsetsListener(settingsLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val isRtl = settingsLayout.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val startInset = if (isRtl) insets.right else insets.left
            val endInset = if (isRtl) insets.left else insets.right

            appBarContainer.setPadding(startInset, insets.top, endInset, 0)
            settingsLayout.setPadding(startInset, 0, endInset, insets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(settingsLayout)

        val btnThemeLight = findViewById<View>(R.id.btnThemeLight)
        val btnThemeDark = findViewById<View>(R.id.btnThemeDark)
        val ivThemeLightIcon = findViewById<ImageView>(R.id.ivThemeLightIcon)
        val tvThemeLightLabel = findViewById<TextView>(R.id.tvThemeLightLabel)
        val ivThemeDarkIcon = findViewById<ImageView>(R.id.ivThemeDarkIcon)
        val tvThemeDarkLabel = findViewById<TextView>(R.id.tvThemeDarkLabel)

        fun updateThemeSelector(isDark: Boolean) {
            btnThemeDark.isSelected = isDark
            ivThemeDarkIcon.isSelected = isDark
            tvThemeDarkLabel.isSelected = isDark

            btnThemeLight.isSelected = !isDark
            ivThemeLightIcon.isSelected = !isDark
            tvThemeLightLabel.isSelected = !isDark
        }

        updateThemeSelector(isDark = ThemeManager.isDarkTheme(this))

        btnThemeLight.setOnClickListener {
            if (ThemeManager.isDarkTheme(this)) {
                updateThemeSelector(isDark = false)
                ThemeManager.setTheme(this, ThemeManager.THEME_LIGHT)
            }
        }

        btnThemeDark.setOnClickListener {
            if (!ThemeManager.isDarkTheme(this)) {
                updateThemeSelector(isDark = true)
                ThemeManager.setTheme(this, ThemeManager.THEME_DARK)
            }
        }

        val cardWebsite = findViewById<MaterialCardView>(R.id.cardWebsite)
        val tvWebsiteTitle = findViewById<TextView>(R.id.tvWebsiteTitle)
        tvWebsiteTitle.setText(R.string.website_title)

        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.4"
        } catch (_: Exception) {
            "1.0.4"
        }
        tvAppVersion.text = getString(R.string.version_format, versionName)

        cardWebsite.setOnClickListener {
            openOfficialWebsite()
        }
    }

    private fun openOfficialWebsite() {
        if (OFFICIAL_WEBSITE_URL.isBlank()) {
            Toast.makeText(this, R.string.website_url_not_configured, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, OFFICIAL_WEBSITE_URL.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
