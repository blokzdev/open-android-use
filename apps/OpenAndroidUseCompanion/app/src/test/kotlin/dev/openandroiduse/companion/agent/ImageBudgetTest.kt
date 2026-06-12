package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The budget math mirrors apps/OpenAndroidUse/image.go: 1280px long edge,
 * 0.85 shrink steps, 0.25 floor.
 */
class ImageBudgetTest {

    @Test
    fun smallImagesKeepScaleOne() {
        assertEquals(1.0, ImageBudget.initialScale(800, 600), 0.0001)
        assertEquals(1.0, ImageBudget.initialScale(1280, 720), 0.0001)
    }

    @Test
    fun largeImagesScaleToLongEdgeBudget() {
        assertEquals(1280.0 / 2400.0, ImageBudget.initialScale(1080, 2400), 0.0001)
        assertEquals(1280.0 / 2560.0, ImageBudget.initialScale(2560, 1440), 0.0001)
    }

    @Test
    fun shrinkStepIsProportionalToOvershoot() {
        // 4x over budget → sqrt(1/4)*0.95 = 0.475 in a single step.
        assertEquals(0.475, ImageBudget.nextScale(1.0, encodedBytes = 3_600_000), 0.0001)
        // Slightly over budget → gentle step, never a no-op (capped at 0.95).
        val gentle = ImageBudget.nextScale(1.0, encodedBytes = 950_000)
        assertEquals(true, gentle < 1.0 && gentle > 0.9)
    }

    @Test
    fun scaleNeverDropsBelowFloor() {
        assertEquals(0.25, ImageBudget.initialScale(100_000, 100), 0.0001)
        assertEquals(0.25, ImageBudget.nextScale(0.26, encodedBytes = 100_000_000), 0.0001)
    }
}
