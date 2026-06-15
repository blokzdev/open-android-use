package dev.openandroiduse.companion.agent

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        // Column, so scroll each into view before asserting it renders (stronger
        // and screen-height-independent vs. a bare existence check).
        composeTestRule.onNodeWithText("Save key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear key").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Model").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & data").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Re-run setup").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sectionTitleIsAHeadingForScreenReaders() {
        composeTestRule.onNodeWithText("API key")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun rendersProviderSelector() {
        // Phase 5.2: both providers are selectable at the top of the screen.
        composeTestRule.onNodeWithText("Claude (Anthropic)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gemini (Google)").assertIsDisplayed()
    }
}
