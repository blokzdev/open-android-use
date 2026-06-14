package dev.openandroiduse.companion.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke for the Phase 4.5 History screen. Uses an empty compose rule so
 * the SessionStore can be seeded *before* the activity lists it, then launches the
 * activity with ActivityScenario.
 */
@RunWith(AndroidJUnit4::class)
class SessionsScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun seed(title: String) {
        val store = SessionStore(context)
        store.deleteAll()
        store.save(
            SessionPayload(
                id = "seed-1",
                title = title,
                createdAt = 1L,
                updatedAt = System.currentTimeMillis(),
                archived = false,
                transcript = listOf(
                    StoredMessage(AgentController.KIND_USER, "Do the thing"),
                    StoredMessage(AgentController.KIND_ASSISTANT, "Done."),
                ),
            ),
        )
    }

    @Test
    fun seededSessionRendersAndOverflowMenuOpens() {
        seed("Turn Bluetooth on")
        ActivityScenario.launch(SessionsActivity::class.java).use {
            composeTestRule.onNodeWithText("Turn Bluetooth on").assertIsDisplayed()
            // 48dp touch target (minimumInteractiveComponentSize) for a11y.
            composeTestRule.onNodeWithContentDescription("More options for Turn Bluetooth on")
                .assertTouchHeightIsEqualTo(48.dp)
            composeTestRule.onNodeWithContentDescription("More options for Turn Bluetooth on").performClick()
            composeTestRule.onNodeWithText("Rename").assertIsDisplayed()
            composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
        }
    }

    @Test
    fun emptyStateShownWhenNoSessions() {
        SessionStore(context).deleteAll()
        ActivityScenario.launch(SessionsActivity::class.java).use {
            composeTestRule.onNodeWithText("No saved conversations yet.").assertIsDisplayed()
        }
    }

    /** Phase 4.7a-2: deleting a conversation offers Undo, and Undo restores it. */
    @Test
    fun deleteThenUndoRestoresSession() {
        seed("Turn Bluetooth on")
        ActivityScenario.launch(SessionsActivity::class.java).use {
            composeTestRule.onNodeWithContentDescription("More options for Turn Bluetooth on").performClick()
            composeTestRule.onNodeWithText("Delete").performClick()
            // Confirm the destructive dialog (the dialog's error-styled Delete action).
            composeTestRule.onNodeWithText("Delete").performClick()
            // The Snackbar reports the deletion and offers Undo.
            composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
            composeTestRule.onNodeWithText("Undo").performClick()
            // The row is back.
            composeTestRule.onNodeWithText("Turn Bluetooth on").assertIsDisplayed()
        }
    }
}
