package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
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
import dev.openandroiduse.companion.agent.TrustAuditLog
import dev.openandroiduse.companion.agent.TrustPolicy
import dev.openandroiduse.companion.agent.TrustStore
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.showUndo
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
import dev.openandroiduse.companion.ui.theme.Spacing
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
    private lateinit var trust: TrustStore
    private lateinit var audit: TrustAuditLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
        trust = TrustStore(this)
        audit = TrustAuditLog(this)
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
                    localOnly = settings.localOnlyMode,
                    onOpenSettings = {
                        startActivity(Intent(this, dev.openandroiduse.companion.agent.SettingsActivity::class.java))
                    },
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
                    loadTrustedApps = ::loadTrustedApps,
                    onRevokeApp = { pkg -> trust.revoke(pkg); { trust.grant(pkg, System.currentTimeMillis()) } },
                    onRevokeAllApps = {
                        val now = System.currentTimeMillis()
                        val saved = trust.list(now)
                        trust.revokeAll();
                        { saved.forEach { trust.grant(it.packageName, now) } }
                    },
                    sensitiveScreenGuard = settings.sensitiveScreenGuard,
                    loadActivity = ::loadActivity,
                    onClearActivity = {
                        val saved = audit.all()
                        audit.clear();
                        { saved.asReversed().forEach { audit.record(it) } }
                    },
                )
            }
        }
    }

    /** Active persistent trust grants, resolved to app labels for the Trusted-apps list (6.5c-3c). */
    private fun loadTrustedApps(): List<TrustedAppItem> {
        val now = System.currentTimeMillis()
        return trust.list(now).map { grant ->
            val label = runCatching {
                val info = packageManager.getApplicationInfo(grant.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(grant.packageName)
            TrustedAppItem(grant.packageName, label, TrustPolicy.daysUntilDecay(grant, now))
        }
    }

    /** Recent trusted-app actions, resolved to app labels + relative time (6.5c-5b). Newest first, capped. */
    private fun loadActivity(): List<ActivityItem> {
        val now = System.currentTimeMillis()
        return audit.all().take(ACTIVITY_LIMIT).map { e ->
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(e.packageName, 0)).toString()
            }.getOrDefault(e.packageName)
            val whenText = DateUtils.getRelativeTimeSpanString(e.at, now, DateUtils.MINUTE_IN_MILLIS).toString()
            ActivityItem(label, e.summary.ifBlank { e.tool }, whenText)
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
    localOnly: Boolean,
    onOpenSettings: () -> Unit,
    onExportAll: () -> Unit,
    onClearKey: () -> (() -> Unit),
    onClearConversation: () -> (() -> Unit),
    onDeleteAllSessions: () -> (() -> Unit),
    loadTrustedApps: () -> List<TrustedAppItem>,
    onRevokeApp: (String) -> (() -> Unit),
    onRevokeAllApps: () -> (() -> Unit),
    sensitiveScreenGuard: Boolean,
    loadActivity: () -> List<ActivityItem>,
    onClearActivity: () -> (() -> Unit),
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
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(stringResource(R.string.privacy_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            PrivacyPoint(stringResource(R.string.privacy_on_device_title), stringResource(R.string.privacy_on_device_body))
            PrivacyPoint(stringResource(R.string.privacy_leaves_title), stringResource(R.string.privacy_leaves_body))
            PrivacyPoint(stringResource(R.string.privacy_key_title), stringResource(R.string.privacy_key_body))
            PrivacyPoint(stringResource(R.string.privacy_saved_title), stringResource(R.string.privacy_saved_body))
            PrivacyPoint(stringResource(R.string.privacy_kill_title), stringResource(R.string.privacy_kill_body))

            HorizontalDivider()

            // --- Local-only mode status (Phase 5.7; the control lives in Settings) ---
            PrivacyPoint(
                stringResource(R.string.privacy_localonly_title),
                stringResource(if (localOnly) R.string.privacy_localonly_on else R.string.privacy_localonly_off),
            )
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_localonly_manage))
            }

            HorizontalDivider()

            // --- Trust & Safety (Phase 6.5c-5b): status + trusted apps + activity ---
            Text(stringResource(R.string.privacy_trustsafety_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
            PrivacyPoint(
                stringResource(R.string.privacy_guard_title),
                stringResource(if (sensitiveScreenGuard) R.string.privacy_guard_on else R.string.privacy_guard_off),
            )
            PrivacyPoint(stringResource(R.string.privacy_injection_title), stringResource(R.string.privacy_injection_body))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_trustsafety_manage))
            }
            TrustedAppsSection(loadTrustedApps, onRevokeApp, onRevokeAllApps, snackbarHost)
            RecentActivitySection(loadActivity, onClearActivity, snackbarHost)

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

/** A persistent trust grant resolved for the Privacy "Trusted apps" list (6.5c-3c). */
data class TrustedAppItem(val packageName: String, val label: String, val daysUntilDecay: Long)

/** One audited trusted-app action resolved for the Privacy "Recent agent activity" list (6.5c-5b). */
data class ActivityItem(val label: String, val summary: String, val whenText: String)

/** Cap on the rendered audit rows (the store keeps more; this bounds the scroll). */
private const val ACTIVITY_LIMIT = 30

@Composable
private fun RecentActivitySection(
    loadActivity: () -> List<ActivityItem>,
    onClearActivity: () -> (() -> Unit),
    host: SnackbarHostState,
) {
    val refresh = remember { mutableStateOf(0) }
    val items = remember(refresh.value) { loadActivity() }

    Text(
        stringResource(R.string.privacy_activity_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.markHeading(),
    )
    Text(stringResource(R.string.privacy_activity_intro), style = MaterialTheme.typography.bodyMedium)

    if (items.isEmpty()) {
        Text(stringResource(R.string.privacy_activity_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    items.forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall)
            Text(
                "${item.summary} · ${item.whenText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    DangerControl(
        label = stringResource(R.string.privacy_activity_clear),
        confirmTitle = stringResource(R.string.privacy_activity_clear_q),
        confirmBody = stringResource(R.string.privacy_activity_clear_body),
        successMessage = stringResource(R.string.privacy_activity_clear_toast),
        host = host,
        onConfirmed = {
            val undo = onClearActivity()
            refresh.value++;
            { undo(); refresh.value = refresh.value + 1 }
        },
    )
}

@Composable
private fun TrustedAppsSection(
    loadTrustedApps: () -> List<TrustedAppItem>,
    onRevokeApp: (String) -> (() -> Unit),
    onRevokeAllApps: () -> (() -> Unit),
    host: SnackbarHostState,
) {
    // A bump counter re-reads the store after a revoke / undo so the list stays live.
    val refresh = remember { mutableStateOf(0) }
    val apps = remember(refresh.value) { loadTrustedApps() }

    Text(
        stringResource(R.string.privacy_trusted_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.markHeading(),
    )
    Text(stringResource(R.string.privacy_trusted_intro), style = MaterialTheme.typography.bodyMedium)

    if (apps.isEmpty()) {
        Text(stringResource(R.string.privacy_trusted_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    apps.forEach { app ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.privacy_trusted_decay, app.daysUntilDecay.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DangerControl(
                label = stringResource(R.string.privacy_trusted_revoke),
                confirmTitle = stringResource(R.string.privacy_trusted_revoke_q),
                confirmBody = stringResource(R.string.privacy_trusted_revoke_body),
                successMessage = stringResource(R.string.privacy_trusted_revoke_toast),
                host = host,
                onConfirmed = {
                    val undo = onRevokeApp(app.packageName)
                    refresh.value++;
                    { undo(); refresh.value = refresh.value + 1 }
                },
            )
        }
    }
    DangerControl(
        label = stringResource(R.string.privacy_trusted_revoke_all),
        confirmTitle = stringResource(R.string.privacy_trusted_revoke_all_q),
        confirmBody = stringResource(R.string.privacy_trusted_revoke_all_body),
        successMessage = stringResource(R.string.privacy_trusted_revoke_all_toast),
        host = host,
        onConfirmed = {
            val undo = onRevokeAllApps()
            refresh.value++;
            { undo(); refresh.value = refresh.value + 1 }
        },
    )
}

@Composable
private fun PrivacyPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
