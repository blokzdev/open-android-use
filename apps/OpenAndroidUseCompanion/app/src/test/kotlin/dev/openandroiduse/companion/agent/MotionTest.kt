package dev.openandroiduse.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun zeroScaleMeansReduced() {
        assertTrue(Motion.isReduced(0f))
    }

    @Test
    fun normalAndFastScalesAreNotReduced() {
        assertFalse(Motion.isReduced(1f))
        assertFalse(Motion.isReduced(0.5f))
        assertFalse(Motion.isReduced(2f))
    }
}
