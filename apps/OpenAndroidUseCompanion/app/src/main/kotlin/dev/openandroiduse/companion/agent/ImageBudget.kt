package dev.openandroiduse.companion.agent

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port of the Go bridge's screenshot budget model (apps/OpenAndroidUse/
 * image.go): shrink a screenshot until it fits the long-edge and byte budgets,
 * clamped at minScale. The applied scale is the snapshot's CoordinateScale
 * (screenshot_px = device_px * scale).
 */
object ImageBudget {

    const val MAX_BYTES = 900_000
    const val MAX_DIMENSION = 1280
    const val MIN_SCALE = 0.25

    /** Pure budget math, unit-testable without a device. */
    fun initialScale(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION, minScale: Double = MIN_SCALE): Double {
        val longEdge = max(width, height)
        var scale = 1.0
        if (maxDimension in 1 until longEdge) {
            scale = maxDimension.toDouble() / longEdge.toDouble()
        }
        if (scale < minScale) {
            scale = minScale
        }
        return scale
    }

    /** Same shrink step the Go loop applies when the encoded size is over budget. */
    fun nextScale(scale: Double, minScale: Double = MIN_SCALE): Double = max(scale * 0.85, minScale)

    data class Result(val png: ByteArray, val scale: Double, val width: Int, val height: Int)

    /**
     * Downscales [source] (a software bitmap) until the encoded PNG fits the
     * byte budget or the scale floor is hit. The source bitmap is not recycled.
     */
    fun downscale(source: Bitmap): Result {
        var scale = initialScale(source.width, source.height)
        while (true) {
            val targetWidth = max(1, (source.width * scale).roundToInt())
            val targetHeight = max(1, (source.height * scale).roundToInt())
            val resized = if (targetWidth == source.width && targetHeight == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            }
            val buffer = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.PNG, 100, buffer)
            if (resized !== source) {
                resized.recycle()
            }
            val encoded = buffer.toByteArray()
            if (encoded.size <= MAX_BYTES || scale <= MIN_SCALE) {
                return Result(encoded, scale, targetWidth, targetHeight)
            }
            scale = nextScale(scale)
        }
    }
}
