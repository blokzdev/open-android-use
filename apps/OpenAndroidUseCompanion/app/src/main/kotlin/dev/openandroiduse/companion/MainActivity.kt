package dev.openandroiduse.companion

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Status and consent surface: shows whether the accessibility service (and so
 * the loopback endpoint) is running, and routes the user to system settings to
 * enable or disable it. Disabling the service is the kill switch.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var restrictedHintView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, padding, 0, padding)
        }
        layout.addView(statusView)

        layout.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        // Shown only while the service is off: sideloaded apps on Android 13+
        // must clear the "Restricted setting" gate before accessibility can be
        // enabled. Hidden once the service is running so it does not nag.
        restrictedHintView = TextView(this).apply {
            textSize = 13f
            setPadding(0, padding, 0, 0)
            text = getString(R.string.restricted_setting_hint)
        }
        layout.addView(restrictedHintView)

        layout.addView(Button(this).apply {
            text = "Open Agent Chat"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, dev.openandroiduse.companion.agent.ChatActivity::class.java))
            }
        })

        layout.addView(Button(this).apply {
            text = getString(R.string.about_button)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }
        })

        layout.addView(TextView(this).apply {
            textSize = 13f
            setPadding(0, padding, 0, 0)
            text = "Enable \"Open Android Use Companion\" to start the control endpoint " +
                "(127.0.0.1:${CompanionService.PORT}, reachable from a computer only via " +
                "`adb forward`). Disable the service at any time to cut the agent off."
        })

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val running = CompanionService.isRunning
        statusView.text = if (running) {
            "Service: running — endpoint live on 127.0.0.1:${CompanionService.PORT}"
        } else {
            "Service: OFF — enable it in Accessibility Settings to start"
        }
        restrictedHintView.visibility = if (running) android.view.View.GONE else android.view.View.VISIBLE
    }
}
