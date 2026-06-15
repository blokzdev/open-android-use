package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.ChatActivity
import dev.openandroiduse.companion.agent.SessionMeta
import dev.openandroiduse.companion.agent.SessionStore
import dev.openandroiduse.companion.agent.SessionsActivity
import dev.openandroiduse.companion.agent.SettingsActivity
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * Home dashboard (Phase 4.7d) and consent surface: shows whether the agent is ready (the
 * accessibility service / loopback endpoint and the API key), offers one context-aware primary
 * action, surfaces recent conversations and a few suggestions, and routes to Settings / History /
 * About. Disabling the accessibility service is the kill switch.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private lateinit var sessions: SessionStore
    private var serviceRunning by mutableStateOf(false)
    private var hasApiKey by mutableStateOf(false)
    private var recents by mutableStateOf<List<SessionMeta>>(emptyList())

    /** The dynamic-color value this instance was themed with, to detect a Settings toggle. */
    private var appliedDynamicColor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
        appliedDynamicColor = settings.dynamicColor

        // First run: route to the onboarding wizard, then never again.
        if (!settings.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                MainScreen(
                    running = serviceRunning,
                    hasApiKey = hasApiKey,
                    port = CompanionService.PORT,
                    recents = recents,
                    restrictedHint = stringResource(R.string.restricted_setting_hint),
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenChat = { startActivity(Intent(this, ChatActivity::class.java)) },
                    onFinishSetup = {
                        // Route to the first missing prerequisite.
                        if (!serviceRunning) {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        }
                    },
                    onResumeSession = { id ->
                        startActivity(
                            Intent(this, ChatActivity::class.java)
                                .putExtra(ChatActivity.EXTRA_SESSION_ID, id),
                        )
                    },
                    onSuggestion = { prompt ->
                        startActivity(
                            Intent(this, ChatActivity::class.java)
                                .putExtra(ChatActivity.EXTRA_PROMPT, prompt),
                        )
                    },
                    onOpenHistory = { startActivity(Intent(this, SessionsActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-theme if Material You was toggled in Settings since this screen was built.
        if (this::settings.isInitialized && settings.dynamicColor != appliedDynamicColor) {
            recreate()
            return
        }
        serviceRunning = CompanionService.isRunning
        hasApiKey = settings.hasApiKey()
        recents = sessions.list().filterNot { it.archived }.take(3)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    running: Boolean,
    hasApiKey: Boolean,
    port: Int,
    recents: List<SessionMeta>,
    restrictedHint: String,
    onOpenAccessibility: () -> Unit,
    onOpenChat: () -> Unit,
    onFinishSetup: () -> Unit,
    onResumeSession: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val ready = readiness(running, hasApiKey) == Readiness.READY
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        ResponsiveContent(contentPadding) { inner ->
            Column(
                modifier = inner
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Hero()
                ReadinessCard(running, hasApiKey, port, ready, restrictedHint, onOpenChat, onFinishSetup)
                if (recents.isNotEmpty()) {
                    RecentsSection(recents, onResumeSession, onOpenHistory)
                }
                SuggestionsSection(onSuggestion)
                SecondaryNav(onOpenSettings, onOpenHistory, onOpenAbout)
                AccessibilityFooter(onOpenAccessibility)
            }
        }
    }
}

@Composable
private fun Hero() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.main_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadinessCard(
    running: Boolean,
    hasApiKey: Boolean,
    port: Int,
    ready: Boolean,
    restrictedHint: String,
    onOpenChat: () -> Unit,
    onFinishSetup: () -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ready) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (readiness(running, hasApiKey)) {
                            Readiness.READY -> R.string.main_status_ready
                            Readiness.NEEDS_ACCESSIBILITY -> R.string.main_status_accessibility_off
                            Readiness.NEEDS_KEY -> R.string.main_status_key_needed
                            Readiness.NEEDS_BOTH -> R.string.main_status_setup_needed
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.markHeading(),
                )
            }
            Text(
                if (ready) {
                    stringResource(R.string.main_ready_caption)
                } else if (!running) {
                    stringResource(R.string.main_service_off)
                } else {
                    stringResource(R.string.main_key_not_set)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ready) {
                Text(
                    stringResource(R.string.main_service_running, port),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(restrictedHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = if (ready) onOpenChat else onFinishSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (ready) R.string.main_cta_open_chat else R.string.main_cta_finish_setup))
            }
        }
    }
}

@Composable
private fun RecentsSection(
    recents: List<SessionMeta>,
    onResumeSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.main_recent_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).markHeading(),
            )
            TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.main_recent_see_all)) }
        }
        recents.forEach { meta ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onResumeSession(meta.id) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta.preview.isNotBlank()) {
                        Text(
                            meta.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsSection(onSuggestion: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.main_suggestions_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.markHeading(),
        )
        HOME_SUGGESTIONS.forEach { promptId ->
            val prompt = stringResource(promptId)
            AssistChip(
                onClick = { onSuggestion(prompt) },
                label = { Text(prompt) },
                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun SecondaryNav(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.main_settings))
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.main_history))
        }
        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.main_about))
        }
    }
}

@Composable
private fun AccessibilityFooter(onOpenAccessibility: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_open_accessibility))
        }
        Text(
            stringResource(R.string.main_kill_switch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val HOME_SUGGESTIONS = listOf(
    R.string.suggested_prompt_1,
    R.string.suggested_prompt_2,
    R.string.suggested_prompt_3,
)
