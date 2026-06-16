package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMarkTest {

    // A 1080x2400 device captured at scale 0.5 → 540x1200 screenshot.
    private val scale = 0.5
    private val shotW = 540
    private val shotH = 1200

    @Test
    fun tapHasEqualEndpointsAndIsNotASwipe() {
        // device (540, 600) → screenshot (270, 300) → normalized (0.5, 0.25)
        val mark = GestureMark.tap(540, 600, scale, shotW, shotH)
        assertEquals(0.5f, mark.fromX, 1e-4f)
        assertEquals(0.25f, mark.fromY, 1e-4f)
        assertEquals(mark.fromX, mark.toX, 0f)
        assertEquals(mark.fromY, mark.toY, 0f)
        assertFalse(mark.isSwipe)
    }

    @Test
    fun swipeKeepsDistinctEndpointsAndIsASwipe() {
        val mark = GestureMark.swipe(540, 1800, 540, 600, scale, shotW, shotH)
        assertEquals(0.5f, mark.fromX, 1e-4f)
        assertEquals(0.75f, mark.fromY, 1e-4f) // 1800*0.5/1200
        assertEquals(0.5f, mark.toX, 1e-4f)
        assertEquals(0.25f, mark.toY, 1e-4f) // 600*0.5/1200
        assertTrue(mark.isSwipe)
    }

    @Test
    fun coordinatesAreClampedToUnitRange() {
        // Way off-screen device points clamp into 0..1 (via TapHighlight).
        val mark = GestureMark.swipe(-500, -500, 99999, 99999, scale, shotW, shotH)
        assertEquals(0f, mark.fromX, 0f)
        assertEquals(0f, mark.fromY, 0f)
        assertEquals(1f, mark.toX, 0f)
        assertEquals(1f, mark.toY, 0f)
    }
}
