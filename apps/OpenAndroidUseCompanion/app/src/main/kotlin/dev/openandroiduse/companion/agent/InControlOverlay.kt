package dev.openandroiduse.companion.agent

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.openandroiduse.companion.CompanionService

/**
 * The "agent is in control" chip, shown over every app while a task runs: the
 * user always knows the agent is acting and can Stop from anywhere. Touchable
 * accessibility overlay (FLAG_NOT_FOCUSABLE only, no SYSTEM_ALERT_WINDOW), in the
 * style of ConfirmationSheet. Best-effort: if it can't show, the chat's Stop
 * still works.
 */
object InControlOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var view: View? = null

    fun attach(service: CompanionService, onStop: () -> Unit) {
        mainHandler.post {
            if (view != null) return@post
            val density = service.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val chip = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(0xF21E1B4B.toInt())
                }
                setPadding(dp(14), dp(6), dp(6), dp(6))
            }
            chip.addView(TextView(service).apply {
                text = "👐 Open Android Use is acting"
                // Screen readers should hear the message, not "open hands"; the chip
                // opens the chat, so describe it as such.
                contentDescription = "Open Android Use is acting. Opens the agent chat."
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, dp(8), 0)
                setOnClickListener {
                    try {
                        service.startActivity(
                            Intent(service, ChatActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                    }
                }
            })
            chip.addView(Button(service).apply {
                text = "Stop"
                setOnClickListener { onStop() }
            })

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(24)
            }
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(chip, params)
                view = chip
            } catch (_: Exception) {
            }
        }
    }

    fun detach(service: CompanionService) {
        mainHandler.post {
            val chip = view ?: return@post
            view = null
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(chip)
            } catch (_: Exception) {
            }
        }
    }
}
