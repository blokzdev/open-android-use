package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityReceiptTest {

    @Test
    fun summarizesAndBucketsAcrossSynonymTools() {
        assertEquals(
            "3 taps · 1 scroll · 1 text entry",
            ActivityReceipt.summarize(mapOf("click" to 3, "scroll" to 1, "type_text" to 1)),
        )
        // click + perform_secondary_action both bucket as taps; type_text + set_value as text entries.
        assertEquals(
            "2 taps · 2 text entries",
            ActivityReceipt.summarize(mapOf("click" to 1, "perform_secondary_action" to 1, "type_text" to 1, "set_value" to 1)),
        )
    }

    @Test
    fun pluralizesSingularsAndIrregulars() {
        assertEquals("1 tap", ActivityReceipt.summarize(mapOf("click" to 1)))
        assertEquals("1 text entry", ActivityReceipt.summarize(mapOf("set_value" to 1)))
        assertEquals("2 key presses", ActivityReceipt.summarize(mapOf("press_key" to 2)))
        assertEquals("1 swipe", ActivityReceipt.summarize(mapOf("drag" to 1)))
    }

    @Test
    fun excludesReadsAndReturnsNullWhenNoActionRan() {
        assertNull(ActivityReceipt.summarize(emptyMap()))
        assertNull(ActivityReceipt.summarize(mapOf("get_app_state" to 5, "list_apps" to 2)))
        // a turn that only read still produces no receipt
        assertNull(ActivityReceipt.summarize(mapOf("get_app_state" to 1)))
    }
}
