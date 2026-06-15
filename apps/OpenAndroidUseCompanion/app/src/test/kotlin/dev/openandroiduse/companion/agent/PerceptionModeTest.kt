package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class PerceptionModeTest {

    @Test
    fun visionWhenScreenshotsEnabled() {
        assertEquals(PerceptionMode.VISION, perceptionMode(sendScreenshots = true))
    }

    @Test
    fun textOnlyWhenScreenshotsDisabled() {
        assertEquals(PerceptionMode.TEXT_ONLY, perceptionMode(sendScreenshots = false))
    }
}
