package dev.openandroiduse.companion.agent

/**
 * Converts a dispatched gesture point (device pixels) into a normalized 0..1
 * fraction of the screenshot the agent captured, so the chat can mark where the
 * agent tapped. Respects the CoordinateScale invariant: screenshot_px =
 * device_px × scale (device_px = screenshot_px / scale). Pure logic, unit-tested.
 */
object TapHighlight {
    fun devicePixelToNormalized(
        deviceX: Int,
        deviceY: Int,
        scale: Double,
        screenshotWidth: Int,
        screenshotHeight: Int,
    ): Pair<Float, Float> {
        val w = screenshotWidth.coerceAtLeast(1)
        val h = screenshotHeight.coerceAtLeast(1)
        val fx = (deviceX * scale / w).coerceIn(0.0, 1.0)
        val fy = (deviceY * scale / h).coerceIn(0.0, 1.0)
        return fx.toFloat() to fy.toFloat()
    }
}
