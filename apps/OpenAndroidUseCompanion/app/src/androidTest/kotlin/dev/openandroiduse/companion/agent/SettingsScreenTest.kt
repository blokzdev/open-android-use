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
        composeTestRule.onNodeWithText("Save key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Model").assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Re-run setup").assertIsDisplayed()
    }
}
