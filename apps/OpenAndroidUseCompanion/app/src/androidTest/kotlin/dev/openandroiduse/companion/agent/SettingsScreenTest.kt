package dev.openandroiduse.companion.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke for the Phase 4.5 Settings screen: it composes and renders its
 * key controls. Runs on the emulator (Compose UI tests are instrumented), so it
 * also rides the emulator-smoke CI. Settings *persistence* is covered at the
 * storage layer by AgentSettingsInstrumentedTest; interaction (menus/dialogs) by
 * SessionsScreenTest.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun rendersKeyControls() {
        // The top control is on-screen; the rest live further down a scrolling
        // Column, so assert they exist in the composition rather than requiring
        // them in the viewport (which depends on the device's screen height).
        composeTestRule.onNodeWithText("Save key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear key").assertExists()
        composeTestRule.onNodeWithText("Model").assertExists()
        composeTestRule.onNodeWithText("Privacy & data").assertExists()
        composeTestRule.onNodeWithText("Re-run setup").assertExists()
    }
}
