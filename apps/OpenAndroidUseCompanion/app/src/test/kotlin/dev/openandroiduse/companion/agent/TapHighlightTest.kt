package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class TapHighlightTest {

    @Test fun centerMapsToHalf() {
        // device 1200 × scale 0.5333 ≈ 640 screenshot px; / 1280 = 0.5.
        val (fx, fy) = TapHighlight.devicePixelToNormalized(1200, 1200, 0.5333, 1280, 1280)
        assertEquals(0.5f, fx, 0.01f)
        assertEquals(0.5f, fy, 0.01f)
    }

    @Test fun originMapsToZero() {
        val (fx, fy) = TapHighlight.devicePixelToNormalized(0, 0, 0.5, 1080, 1920)
        assertEquals(0f, fx, 0.0001f)
        assertEquals(0f, fy, 0.0001f)
    }

    @Test fun clampsOutOfRange() {
        val (fx, fy) = TapHighlight.devicePixelToNormalized(-50, 9_999_999, 0.5, 1080, 1920)
        assertEquals(0f, fx, 0.0001f)
        assertEquals(1f, fy, 0.0001f)
    }

    @Test fun zeroDimsDoNotCrash() {
        val (fx, fy) = TapHighlight.devicePixelToNormalized(100, 100, 1.0, 0, 0)
        assertEquals(1f, fx, 0.0001f)
        assertEquals(1f, fy, 0.0001f)
    }
}
