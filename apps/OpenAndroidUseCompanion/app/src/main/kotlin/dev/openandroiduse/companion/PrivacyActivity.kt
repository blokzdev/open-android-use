package dev.openandroiduse.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentController
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.SessionStore
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * Privacy & data (Phase 4.5): the trust story made browsable — what the app can
 * see, what leaves the device, how the key is stored, and the kill switch —
 * plus honest data controls (clear key, clear the current conversation, delete
 * all saved conversations). Restyles the onboarding privacy step into a
 * first-class screen; About keeps the licenses/attribution it needs for Play.
 */
class PrivacyActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private lateinit var sessions: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                PrivacyScreen(
                    onClearKey = { settings.clearApiKey() },
                    onClearConversation = { AgentController.resetConversation() },
                    onDeleteAllSessions = { sessions.deleteAll() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyScreen(
    onClearKey: () -> Unit,
    onClearConversation: () -> Unit,
    onDeleteAllSessions: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Privacy & data") }) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How your data is handled", style = MaterialTheme.typography.titleMedium)
            PrivacyPoint(
                "On-device",
                "The agent runs here. Its control endpoint binds to this device only " +
                    "(127.0.0.1) and is reachable from a computer solely via USB debugging.",
            )
            PrivacyPoint(
                "What leaves the device",
                "Only your task and the on-screen context (a text view of the screen and " +
                    "screenshots) — sent to the model provider you choose (api.anthropic.com) " +
                    "to decide the next action. Nothing else is uploaded.",
            )
            PrivacyPoint(
                "Your API key",
                "Stored encrypted in the Android Keystore with a non-exportable key; the " +
                    "plaintext never touches disk and leaves the device only toward the provider.",
            )
            PrivacyPoint(
                "Saved conversations",
                "Conversations are saved on this device as text so you can revisit and resume " +
                    "them. Screenshots are never written to disk. Delete any conversation, or all " +
                    "of them, below or from History.",
            )
            PrivacyPoint(
                "Kill switch",
                "Press Stop any time. Disabling the accessibility service fully cuts the agent off.",
            )

            HorizontalDivider()

            Text("Data controls", style = MaterialTheme.typography.titleMedium)
            DangerControl(
                label = "Clear API key",
                confirmTitle = "Clear API key?",
                confirmBody = "The agent won't run until you add a key again.",
                onConfirmed = {
                    onClearKey()
                    Toast.makeText(context, "API key cleared", Toast.LENGTH_SHORT).show()
                },
            )
            DangerControl(
                label = "Clear current conversation",
                confirmTitle = "Clear current conversation?",
                confirmBody = "Removes the in-progress conversation from the chat. Saved " +
                    "conversations in History are not affected.",
                onConfirmed = {
                    onClearConversation()
                    Toast.makeText(context, "Conversation cleared", Toast.LENGTH_SHORT).show()
                },
            )
            DangerControl(
                label = "Delete all saved conversations",
                confirmTitle = "Delete all conversations?",
                confirmBody = "Permanently deletes every saved conversation on this device. " +
                    "This can't be undone.",
                onConfirmed = {
                    onDeleteAllSessions()
                    Toast.makeText(context, "All conversations deleted", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun PrivacyPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DangerControl(
    label: String,
    confirmTitle: String,
    confirmBody: String,
    onConfirmed: () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.error)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmBody) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirmed() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}
