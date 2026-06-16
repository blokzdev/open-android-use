package dev.openandroiduse.companion.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSummaryTest {

    private fun snapshot(vararg elements: ElementRecord) =
        AppSnapshot(appName = "Settings", packageName = "com.android.settings", elements = elements.toList())

    private fun args(vararg pairs: Pair<String, Any>) = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    @Test
    fun clickResolvesElementLabel() {
        val snap = snapshot(ElementRecord(index = 5, name = "Send"))
        assertEquals("'Send'", ActionSummary.describe(snap, "click", args("app" to "x", "element_index" to "5")))
    }

    @Test
    fun clickFallsBackToCoordsWhenNoIndex() {
        assertEquals("(12, 34)", ActionSummary.describe(null, "click", args("app" to "x", "x" to 12.0, "y" to 34.0)))
    }

    @Test
    fun clickFallsBackToIndexWhenSnapshotMissing() {
        assertEquals("7", ActionSummary.describe(null, "click", args("app" to "x", "element_index" to "7")))
    }

    @Test
    fun labelFallsBackThroughValueTypeResourceId() {
        val snap = snapshot(ElementRecord(index = 1, controlType = "Button", resourceId = "btn_ok"))
        assertEquals("'Button'", ActionSummary.describe(snap, "click", args("app" to "x", "element_index" to "1")))
    }

    @Test
    fun setValueNamesTheFieldAndValue() {
        val snap = snapshot(ElementRecord(index = 2, name = "Email"))
        assertEquals(
            "'Email' = \"a@b.com\"",
            ActionSummary.describe(snap, "set_value", args("app" to "x", "element_index" to "2", "value" to "a@b.com")),
        )
    }

    @Test
    fun scrollNamesContainerWhenKnown() {
        val snap = snapshot(ElementRecord(index = 3, name = "Inbox"))
        assertEquals(
            "down in 'Inbox'",
            ActionSummary.describe(snap, "scroll", args("app" to "x", "element_index" to "3", "direction" to "down")),
        )
    }

    @Test
    fun scrollFallsBackToDirectionAndPages() {
        assertEquals(
            "down ×2.0",
            ActionSummary.describe(null, "scroll", args("app" to "x", "direction" to "down", "pages" to 2.0)),
        )
    }

    @Test
    fun getAppStateShowsApp() {
        assertEquals("settings", ActionSummary.describe(null, "get_app_state", args("app" to "settings")))
    }
}
