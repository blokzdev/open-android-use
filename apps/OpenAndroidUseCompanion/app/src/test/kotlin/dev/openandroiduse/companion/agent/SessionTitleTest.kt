package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTitleTest {

    @Test
    fun shortPromptUsedVerbatim() {
        assertEquals("Turn Bluetooth on", SessionTitle.derive("Turn Bluetooth on"))
    }

    @Test
    fun blankFallsBack() {
        assertEquals(SessionTitle.FALLBACK, SessionTitle.derive("   \n  "))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("Open Settings and check version", SessionTitle.derive("Open  Settings\nand   check version"))
    }

    @Test
    fun longPromptTruncatedWithEllipsisOnWordBoundary() {
        val title = SessionTitle.derive(
            "Open the Settings app and tell me which Android version this phone is running right now",
        )
        assertTrue("ends with ellipsis: $title", title.endsWith("…"))
        assertTrue("trimmed to <= 41 chars: ${title.length}", title.length <= 41)
        assertTrue("cut on a word boundary: $title", !title.dropLast(1).endsWith(" "))
    }
}
