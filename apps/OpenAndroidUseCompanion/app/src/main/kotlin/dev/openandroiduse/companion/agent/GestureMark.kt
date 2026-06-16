package dev.openandroiduse.companion.agent

/**
 * Phase 6.2: a single agent gesture in normalized screenshot space (0..1), for the
 * Agent's-view overlay. A tap/long-press has `from == to` (drawn as a ripple); a
 * swipe/drag has distinct endpoints (drawn as a line + arrowhead). Pure; built from
 * device pixels via [TapHighlight.devicePixelToNormalized] so the same CoordinateScale
 * invariant as the tap marker holds. Unit-tested.
 */
data class GestureMark(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
) {
    val isSwipe: Boolean get() = fromX != toX || fromY != toY

    companion object {
        /** A tap / long-press at one device point. */
        fun tap(deviceX: Int, deviceY: Int, scale: Double, shotWidth: Int, shotHeight: Int): GestureMark {
            val (x, y) = TapHighlight.devicePixelToNormalized(deviceX, deviceY, scale, shotWidth, shotHeight)
            return GestureMark(x, y, x, y)
        }

        /** A swipe / drag from one device point to another. */
        fun swipe(
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int,
            scale: Double,
            shotWidth: Int,
            shotHeight: Int,
        ): GestureMark {
            val (fx, fy) = TapHighlight.devicePixelToNormalized(fromX, fromY, scale, shotWidth, shotHeight)
            val (tx, ty) = TapHighlight.devicePixelToNormalized(toX, toY, scale, shotWidth, shotHeight)
            return GestureMark(fx, fy, tx, ty)
        }
    }
}
