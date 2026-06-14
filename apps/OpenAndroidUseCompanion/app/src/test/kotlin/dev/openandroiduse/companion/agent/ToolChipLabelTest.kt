package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolChipLabelTest {

    @Test fun tapByIndex() = assertEquals("Tap [42]", ToolChipLabel.describe("▸ click [42]"))

    @Test fun tapByCoords() = assertEquals("Tap (240, 128)", ToolChipLabel.describe("▸ click (240, 128)"))

    @Test fun typeText() = assertEquals("Type \"hello\"", ToolChipLabel.describe("▸ type_text \"hello\""))

    @Test fun scroll() = assertEquals("Scroll down ×2.0", ToolChipLabel.describe("▸ scroll down ×2.0"))

    @Test fun openApp() = assertEquals("Open Settings", ToolChipLabel.describe("▸ get_app_state Settings"))

    @Test fun readScreenWhenNoApp() = assertEquals("Read the screen", ToolChipLabel.describe("▸ get_app_state"))

    @Test fun pressKey() = assertEquals("Press Back", ToolChipLabel.describe("▸ press_key Back"))

    @Test fun listApps() = assertEquals("List apps", ToolChipLabel.describe("▸ list_apps"))

    @Test fun errorChip() {
        assertTrue(ToolChipLabel.isError("✗ click failed — the agent will see the error and adapt"))
        assertEquals(
            "click failed — the agent will see the error and adapt",
            ToolChipLabel.describe("✗ click failed — the agent will see the error and adapt"),
        )
    }

    @Test fun nonErrorIsNotError() = assertFalse(ToolChipLabel.isError("▸ click [1]"))
}
