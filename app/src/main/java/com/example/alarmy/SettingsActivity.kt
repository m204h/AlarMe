package com.example.alarmy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.menu_settings)
        }

        val settingsLayout = findViewById<View>(R.id.settingsLayout)
        val actionBarContainer = window.decorView.findViewById<View>(androidx.appcompat.R.id.action_bar_container)

        val actionBarHeight = android.util.TypedValue().let { value ->
            if (theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true)) {
                android.util.TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
            } else {
                0
            }
        }

        var startInset = 0
        var endInset = 0
        var bottomInset = 0
        var statusBarTopInset = 0

        fun updateContentPadding(view: View) {
            val topPadding = if (actionBarContainer != null && actionBarContainer.height > 0) {
                val actionBarLocation = IntArray(2)
                val viewLocation = IntArray(2)
                actionBarContainer.getLocationOnScreen(actionBarLocation)
                view.getLocationOnScreen(viewLocation)
                val actionBarBottom = actionBarLocation[1] + actionBarContainer.height
                val viewTop = viewLocation[1]
                maxOf(0, actionBarBottom - viewTop)
            } else {
                val viewLocation = IntArray(2)
                view.getLocationOnScreen(viewLocation)
                val viewTop = viewLocation[1]
                val estimatedActionBarBottom = statusBarTopInset + actionBarHeight
                maxOf(0, estimatedActionBarBottom - viewTop)
            }

            if (view.paddingStart == startInset &&
                view.paddingTop == topPadding &&
                view.paddingEnd == endInset &&
                view.paddingBottom == bottomInset
            ) return

            view.setPaddingRelative(
                startInset,
                topPadding,
                endInset,
                bottomInset,
            )
        }

        settingsLayout.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateContentPadding(view)
        }
        ViewCompat.setOnApplyWindowInsetsListener(settingsLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            startInset = if (isRtl) insets.right else insets.left
            endInset = if (isRtl) insets.left else insets.right
            bottomInset = insets.bottom
            statusBarTopInset = insets.top
            updateContentPadding(view)
            windowInsets
        }

        val cardWebsite = findViewById<MaterialCardView>(R.id.cardWebsite)
        val tvWebsiteTitle = findViewById<TextView>(R.id.tvWebsiteTitle)
        tvWebsiteTitle.setText(R.string.website_title)

        cardWebsite.setOnClickListener {
            openOfficialWebsite()
        }
    }

    private fun openOfficialWebsite() {
        if (OFFICIAL_WEBSITE_URL.isBlank()) {
            Toast.makeText(this, R.string.website_url_not_configured, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_WEBSITE_URL))
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
