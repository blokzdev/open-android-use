package dev.openandroiduse.companion.agent

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import dev.openandroiduse.companion.CompanionService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures a software bitmap of the default display via the accessibility
 * screenshot API (Android 11+). Kept separate from HttpServer's PNG endpoint
 * so the dependency-free control surface stays untouched by agent code.
 */
object ScreenCapture {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun capture(service: CompanionService): Bitmap? {
        if (!isSupported()) return null
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { it.run() },
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                    } catch (_: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            },
        )
        if (!latch.await(5, TimeUnit.SECONDS)) return null
        return bitmap
    }
}
