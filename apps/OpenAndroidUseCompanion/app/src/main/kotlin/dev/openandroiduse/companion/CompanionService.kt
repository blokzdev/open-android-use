package dev.openandroiduse.companion

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The companion's core: while the user has this accessibility service enabled,
 * a loopback-only HTTP server (see [HttpServer]) exposes the screen state and
 * gesture/action surface defined in docs/design-docs/on-device-companion.md.
 * Disabling the service in system settings is the kill switch.
 */
class CompanionService : AccessibilityService() {

    private var server: HttpServer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        server = HttpServer(this, PORT).also { it.start() }
        Log.i(TAG, "companion service connected; serving on 127.0.0.1:$PORT")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopServer()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun stopServer() {
        server?.shutdown()
        server = null
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "companion service stopped")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // State is pulled on demand via rootInActiveWindow; no event buffering.
        // Direct-manipulation events feed touch-to-pause while a task runs.
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            -> dev.openandroiduse.companion.agent.AgentController.onInteractionEvent(
                event.packageName?.toString(),
                packageName,
            )
            else -> Unit
        }
    }

    override fun onInterrupt() {}

    /**
     * Runs [block] on the main looper and waits up to [timeoutMs]; accessibility
     * tree access and gesture dispatch are main-thread affine while HTTP request
     * handling happens on worker threads.
     */
    fun <T> onMainThread(timeoutMs: Long = 5_000, block: () -> T): T? {
        val latch = CountDownLatch(1)
        var value: T? = null
        Handler(mainLooper).post {
            try {
                value = block()
            } catch (error: Exception) {
                Log.w(TAG, "main-thread block failed", error)
            } finally {
                latch.countDown()
            }
        }
        return if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) value else null
    }

    companion object {
        const val TAG = "OpenAndroidUse"
        const val PORT = 8355

        @Volatile
        var instance: CompanionService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
