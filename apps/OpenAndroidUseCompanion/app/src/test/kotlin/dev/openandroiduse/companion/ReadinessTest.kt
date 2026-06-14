package dev.openandroiduse.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessTest {

    @Test
    fun bothPrerequisitesMet() {
        assertEquals(Readiness.READY, readiness(accessibilityOn = true, hasKey = true))
    }

    @Test
    fun neitherPrerequisiteMet() {
        assertEquals(Readiness.NEEDS_BOTH, readiness(accessibilityOn = false, hasKey = false))
    }

    @Test
    fun onlyAccessibilityMissing() {
        assertEquals(Readiness.NEEDS_ACCESSIBILITY, readiness(accessibilityOn = false, hasKey = true))
    }

    @Test
    fun onlyKeyMissing() {
        assertEquals(Readiness.NEEDS_KEY, readiness(accessibilityOn = true, hasKey = false))
    }
}
