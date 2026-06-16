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
import dev.openandroiduse.companion.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 6.5c-2: the in-flow human takeover for a sensitive screen. When an action is
 * blocked because the screen has a password/payment field, the agent loop parks here and
 * shows "🔒 You take it from here" over the target app (an accessibility overlay, the same
 * technique as [ConfirmationSheet], so it works while the chat Activity is backgrounded).
 * The human enters the secret and taps Continue to resume, or Stop to end the task. The
 * agent never sees or types the secret — this replaces the old dead-end text refusal.
 *
 * Blocks the loop thread until the user decides; a timeout or a global Stop counts as STOP
 * (fail safe). There is intentionally no "do it for me" — the secret is always the human's.
 */
object HandoffSheet {

    enum class Result { CONTINUE, STOP }

    // Humans type a password, so allow longer than the confirm sheet's 120s.
    private const val TIMEOUT_MS = 300_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeLatch: CountDownLatch? = null

    @Volatile
    private var activeDismiss: (() -> Unit)? = null

    /** Releases a loop thread parked in [await] (Stop pressed elsewhere). Treated as STOP. */
    fun cancel() {
        activeDismiss?.invoke()
        activeLatch?.countDown()
    }

    /** Called on the agent loop thread. Returns [Result.CONTINUE] only on an explicit Continue tap. */
    fun await(service: CompanionService, body: String): Result {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference(Result.STOP)
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
                text = service.getString(R.string.handoff_header)
                textSize = 15f
                setTextColor(Color.BLACK)
            })
            layout.addView(TextView(service).apply {
                text = body
                textSize = 14f
                setTextColor(0xFF444444.toInt())
                setPadding(0, dp(4), 0, dp(8))
            })
            val buttons = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(service).apply {
                text = service.getString(R.string.handoff_stop)
                minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    outcome.set(Result.STOP)
                    dismiss()
                    latch.countDown()
                }
            })
            buttons.addView(Button(service).apply {
                text = service.getString(R.string.handoff_continue)
                minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    outcome.set(Result.CONTINUE)
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
                // Could not show the sheet — fail closed (Stop).
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
            return Result.STOP
        }
        // A cancel() counts the latch down without setting CONTINUE → STOP.
        return outcome.get()
    }
}
