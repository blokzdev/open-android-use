package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentController
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.ConversationExport
import dev.openandroiduse.companion.agent.SessionStore
import dev.openandroiduse.companion.agent.SessionTitle
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.showUndo
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
import kotlinx.coroutines.launch

/**
 * Privacy & data (Phase 4.5): the trust story made browsable — what the app can
 * see, what leaves the device, how the key is stored, and the kill switch —
 * plus honest data controls (clear key, clear the current conversation, delete
 * all saved conversations). Restyles the onboarding privacy step into a
 * first-class screen; About keeps the licenses/attribution it needs for Play.
 *
 * Phase 4.7a-2: destructive controls now confirm, then report via a Snackbar
 * with **Undo** — each action captures its pre-state (the key, the live
 * conversation, or every saved session) and restores it if the user taps Undo,
 * so a mistaken clear/delete is recoverable for the life of the snackbar.
 */
class PrivacyActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private lateinit var sessions: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
        enableEdgeToEdge()
        setContent {
            OpenAndroidUseTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val usage = sessions.usage()
                PrivacyScreen(
                    storageSummary = getString(
                        R.string.privacy_storage_summary,
                        usage.count,
                        Formatter.formatShortFileSize(this, usage.bytes),
                    ),
                    onExportAll = ::exportAllConversations,
                    onClearKey = {
                        val previous = settings.loadApiKey()
                        settings.clearApiKey();
                        { if (previous != null) settings.storeApiKey(previous) }
                    },
                    onClearConversation = {
                        val snapshot = AgentController.snapshotForPersistence()
                        AgentController.resetConversation();
                        { if (snapshot != null) AgentController.restore(snapshot) }
                    },
                    onDeleteAllSessions = {
                        val saved = sessions.list().mapNotNull { sessions.load(it.id) }
                        sessions.deleteAll();
                        { saved.forEach { sessions.save(it) } }
                    },
                )
            }
        }
    }

    /** Export every saved conversation as one Markdown file shared via FileProvider (Phase 4.7e). */
    private fun exportAllConversations() {
        val payloads = sessions.list().mapNotNull { sessions.load(it.id) }
        if (payloads.isEmpty()) {
            Toast.makeText(this, getString(R.string.privacy_export_all_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val markdown = payloads.joinToString("\n\n---\n\n") { payload ->
            ConversationExport.toMarkdown(
                payload.title.ifBlank { SessionTitle.FALLBACK },
                payload.transcript.map { it.kind to it.text },
            )
        }
        val uri = runCatching {
            val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "conversations-${System.currentTimeMillis()}.md")
            file.writeText(markdown)
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrElse {
            Toast.makeText(this, getString(R.string.privacy_export_all_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.privacy_export_all_chooser)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyScreen(
    storageSummary: String,
    onExportAll: () -> Unit,
    onClearKey: () -> (() -> Unit),
    onClearConversation: () -> (() -> Unit),
    onDeleteAllSessions: () -> (() -> Unit),
) {
    val snackbarHost = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.privacy_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { contentPadding ->
        ResponsiveContent(contentPadding) { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.privacy_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            PrivacyPoint(stringResource(R.string.privacy_on_device_title), stringResource(R.string.privacy_on_device_body))
            PrivacyPoint(stringResource(R.string.privacy_leaves_title), stringResource(R.string.privacy_leaves_body))
            PrivacyPoint(stringResource(R.string.privacy_key_title), stringResource(R.string.privacy_key_body))
            PrivacyPoint(stringResource(R.string.privacy_saved_title), stringResource(R.string.privacy_saved_body))
            PrivacyPoint(stringResource(R.string.privacy_kill_title), stringResource(R.string.privacy_kill_body))

            HorizontalDivider()

            // --- Storage ---
            Text(stringResource(R.string.privacy_storage_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            Text(storageSummary, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onExportAll, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_export_all))
            }

            HorizontalDivider()

            Text(stringResource(R.string.privacy_data_controls), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            DangerControl(
                label = stringResource(R.string.privacy_clear_key),
                confirmTitle = stringResource(R.string.privacy_clear_key_q),
                confirmBody = stringResource(R.string.privacy_clear_key_body),
                successMessage = stringResource(R.string.privacy_clear_key_toast),
                host = snackbarHost,
                onConfirmed = onClearKey,
            )
            DangerControl(
                label = stringResource(R.string.privacy_clear_conv),
                confirmTitle = stringResource(R.string.privacy_clear_conv_q),
                confirmBody = stringResource(R.string.privacy_clear_conv_body),
                successMessage = stringResource(R.string.privacy_clear_conv_toast),
                host = snackbarHost,
                onConfirmed = onClearConversation,
            )
            DangerControl(
                label = stringResource(R.string.privacy_delete_all),
                confirmTitle = stringResource(R.string.privacy_delete_all_q),
                confirmBody = stringResource(R.string.privacy_delete_all_body),
                successMessage = stringResource(R.string.privacy_delete_all_toast),
                host = snackbarHost,
                onConfirmed = onDeleteAllSessions,
            )
        }
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

/**
 * A destructive control: confirm via dialog, run [onConfirmed] (which performs
 * the change and returns an undo lambda capturing the pre-state), then show a
 * Snackbar offering Undo. Tapping Undo runs the returned lambda to restore.
 */
@Composable
private fun DangerControl(
    label: String,
    confirmTitle: String,
    confirmBody: String,
    successMessage: String,
    host: SnackbarHostState,
    onConfirmed: () -> (() -> Unit),
) {
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
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
                TextButton(onClick = {
                    asking = false
                    val undo = onConfirmed()
                    scope.launch {
                        if (host.showUndo(successMessage, undoLabel)) undo()
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
