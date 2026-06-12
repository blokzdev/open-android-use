package dev.openandroiduse.companion.agent

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.openandroiduse.companion.CompanionService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The visible consent surface (Phase 3.1b): a bottom sheet drawn as an
 * accessibility overlay — it works while the agent is driving *other* apps,
 * which is exactly when the chat Activity is backgrounded and a dialog could
 * not show. Blocks the agent loop thread until the user decides; timing out
 * counts as Deny.
 */
object ConfirmationSheet {

    private const val TIMEOUT_MS = 120_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeLatch: CountDownLatch? = null

    @Volatile
    private var activeDismiss: (() -> Unit)? = null

    /** Releases a loop thread parked in [ask] (Stop pressed). Treated as a deny. */
    fun cancel() {
        activeDismiss?.invoke()
        activeLatch?.countDown()
    }

    /** Called on the agent loop thread. Returns true only on explicit Allow. */
    fun ask(service: CompanionService, summary: String): Boolean {
        val latch = CountDownLatch(1)
        val allowed = AtomicBoolean(false)
        var sheet: LinearLayout? = null
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        fun dismiss() {
            mainHandler.post {
                sheet?.let {
                    try {
                        wm.removeView(it)
                    } catch (_: Exception) {
                    }
                }
                sheet = null
            }
        }
        activeLatch = latch
        activeDismiss = ::dismiss

        mainHandler.post {
            val density = service.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val layout = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xF2FFFFFF.toInt())
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            layout.addView(TextView(service).apply {
                text = "The agent wants to:"
                textSize = 13f
                setTextColor(0xFF666666.toInt())
            })
            layout.addView(TextView(service).apply {
                text = summary
                textSize = 15f
                setTextColor(Color.BLACK)
                setPadding(0, dp(4), 0, dp(8))
            })
            val buttons = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(service).apply {
                text = "Deny"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    allowed.set(false)
                    dismiss()
                    latch.countDown()
                }
            })
            buttons.addView(Button(service).apply {
                text = "Allow"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    allowed.set(true)
                    dismiss()
                    latch.countDown()
                }
            })
            layout.addView(buttons)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM }
            try {
                wm.addView(layout, params)
                sheet = layout
            } catch (_: Exception) {
                // Could not show the sheet — fail closed (deny).
                latch.countDown()
            }
        }

        val decided = try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        activeLatch = null
        activeDismiss = null
        if (!decided) {
            dismiss()
        }
        // A cancel() counts the latch down without setting allowed → deny.
        return decided && allowed.get()
    }
}
