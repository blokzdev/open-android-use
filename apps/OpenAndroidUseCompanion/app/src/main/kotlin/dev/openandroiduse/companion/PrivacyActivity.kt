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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentController
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.SessionStore
import dev.openandroiduse.companion.ui.markHeading
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.privacy_title)) }) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.privacy_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            PrivacyPoint(stringResource(R.string.privacy_on_device_title), stringResource(R.string.privacy_on_device_body))
            PrivacyPoint(stringResource(R.string.privacy_leaves_title), stringResource(R.string.privacy_leaves_body))
            PrivacyPoint(stringResource(R.string.privacy_key_title), stringResource(R.string.privacy_key_body))
            PrivacyPoint(stringResource(R.string.privacy_saved_title), stringResource(R.string.privacy_saved_body))
            PrivacyPoint(stringResource(R.string.privacy_kill_title), stringResource(R.string.privacy_kill_body))

            HorizontalDivider()

            Text(stringResource(R.string.privacy_data_controls), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            val clearedKey = stringResource(R.string.privacy_clear_key_toast)
            val clearedConv = stringResource(R.string.privacy_clear_conv_toast)
            val deletedAll = stringResource(R.string.privacy_delete_all_toast)
            DangerControl(
                label = stringResource(R.string.privacy_clear_key),
                confirmTitle = stringResource(R.string.privacy_clear_key_q),
                confirmBody = stringResource(R.string.privacy_clear_key_body),
                onConfirmed = {
                    onClearKey()
                    Toast.makeText(context, clearedKey, Toast.LENGTH_SHORT).show()
                },
            )
            DangerControl(
                label = stringResource(R.string.privacy_clear_conv),
                confirmTitle = stringResource(R.string.privacy_clear_conv_q),
                confirmBody = stringResource(R.string.privacy_clear_conv_body),
                onConfirmed = {
                    onClearConversation()
                    Toast.makeText(context, clearedConv, Toast.LENGTH_SHORT).show()
                },
            )
            DangerControl(
                label = stringResource(R.string.privacy_delete_all),
                confirmTitle = stringResource(R.string.privacy_delete_all_q),
                confirmBody = stringResource(R.string.privacy_delete_all_body),
                onConfirmed = {
                    onDeleteAllSessions()
                    Toast.makeText(context, deletedAll, Toast.LENGTH_SHORT).show()
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
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
