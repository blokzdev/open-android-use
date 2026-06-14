package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.ChatActivity
import dev.openandroiduse.companion.agent.ModelCatalog
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * First-run onboarding wizard (Phase 4.1): welcome → enable accessibility →
 * privacy → API key → preferences → ready. Skippable API key; the app degrades
 * gracefully elsewhere when a prerequisite is missing (see Readiness). Gated by
 * AgentSettings.onboardingCompleted; MainActivity routes here on first run.
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private var serviceRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        setContent {
            OpenAndroidUseTheme {
                OnboardingScreen(
                    settings = settings,
                    serviceRunning = serviceRunning,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshModels = {
                        Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
                    },
                    onComplete = ::completeOnboarding,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceRunning = CompanionService.isRunning
    }

    private fun completeOnboarding(openChat: Boolean) {
        settings.onboardingCompleted = true
        startActivity(Intent(this, MainActivity::class.java))
        if (openChat) {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        finish()
    }
}

private const val STEP_COUNT = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(
    settings: AgentSettings,
    serviceRunning: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshModels: () -> Unit,
    onComplete: (openChat: Boolean) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    val models = remember { settings.availableModels() }
    var model by remember { mutableStateOf(settings.model) }
    var confirmActions by remember { mutableStateOf(settings.confirmActions) }
    var speakNarration by remember { mutableStateOf(settings.speakNarration) }

    // Auto-advance off the accessibility step once the service connects.
    LaunchedEffect(serviceRunning, step) {
        if (step == 1 && serviceRunning) step = 2
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_step, step + 1, STEP_COUNT)) }) },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            Crossfade(targetState = step, label = "onboarding-step", modifier = Modifier.weight(1f)) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (current) {
                        0 -> WelcomeStep()
                        1 -> AccessibilityStep(serviceRunning, onOpenAccessibilitySettings)
                        2 -> PrivacyStep()
                        3 -> ApiKeyStep(
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            models = models,
                            model = model,
                            onModelChange = { model = it },
                        )
                        4 -> PreferencesStep(
                            confirmActions = confirmActions,
                            onConfirmChange = { confirmActions = it },
                            speakNarration = speakNarration,
                            onSpeakChange = { speakNarration = it },
                        )
                        else -> ReadyStep(serviceRunning, settings.hasApiKey())
                    }
                }
            }

            // Bottom action bar — buttons vary per step.
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0 && step < 5) {
                    OutlinedButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                when (step) {
                    0 -> Button(onClick = { step = 1 }) { Text(stringResource(R.string.onboarding_get_started)) }
                    1 -> Button(onClick = { step = 2 }) {
                        Text(stringResource(if (serviceRunning) R.string.onboarding_continue else R.string.onboarding_skip))
                    }
                    2 -> Button(onClick = { step = 3 }) { Text(stringResource(R.string.onboarding_continue)) }
                    3 -> {
                        TextButton(onClick = { step = 4 }) { Text(stringResource(R.string.onboarding_skip)) }
                        Button(onClick = {
                            val key = apiKey.trim()
                            if (key.isNotEmpty()) {
                                settings.storeApiKey(key)
                                settings.model = model
                                onRefreshModels()
                            }
                            step = 4
                        }) { Text(stringResource(R.string.onboarding_save_continue)) }
                    }
                    4 -> Button(onClick = {
                        settings.confirmActions = confirmActions
                        settings.speakNarration = speakNarration
                        step = 5
                    }) { Text(stringResource(R.string.onboarding_continue)) }
                    else -> {
                        TextButton(onClick = { onComplete(false) }) { Text(stringResource(R.string.onboarding_done)) }
                        Button(onClick = { onComplete(true) }) { Text(stringResource(R.string.action_open_chat)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.onboarding_welcome_time), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AccessibilityStep(serviceRunning: Boolean, onOpen: () -> Unit) {
    Text(stringResource(R.string.onboarding_accessibility_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_accessibility_body), style = MaterialTheme.typography.bodyMedium)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(if (serviceRunning) R.string.onboarding_service_running else R.string.onboarding_service_not_enabled),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_open_accessibility))
    }
    Text(stringResource(R.string.restricted_setting_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PrivacyStep() {
    Text(stringResource(R.string.onboarding_privacy_title), style = MaterialTheme.typography.headlineSmall)
    PrivacyPoint(stringResource(R.string.privacy_on_device_title), stringResource(R.string.onboarding_privacy_on_device_body))
    PrivacyPoint(stringResource(R.string.privacy_leaves_title), stringResource(R.string.onboarding_privacy_leaves_body))
    PrivacyPoint(stringResource(R.string.privacy_key_title), stringResource(R.string.onboarding_privacy_key_body))
    PrivacyPoint(stringResource(R.string.privacy_kill_title), stringResource(R.string.privacy_kill_body))
}

@Composable
private fun PrivacyPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApiKeyStep(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    models: List<String>,
    model: String,
    onModelChange: (String) -> Unit,
) {
    Text(stringResource(R.string.onboarding_apikey_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_apikey_body), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.onboarding_apikey_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.label_model), style = MaterialTheme.typography.titleSmall)
    ModelDropdown(models = models, selected = model, onSelected = onModelChange)
}

@Composable
private fun ModelDropdown(models: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(models, selected) {
        if (selected in models) models else listOf(selected) + models
    }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(selected)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { id ->
            DropdownMenuItem(text = { Text(id) }, onClick = {
                onSelected(id)
                expanded = false
            })
        }
    }
}

@Composable
private fun PreferencesStep(
    confirmActions: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    speakNarration: Boolean,
    onSpeakChange: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.onboarding_prefs_title), style = MaterialTheme.typography.headlineSmall)
    ToggleRow(
        stringResource(R.string.pref_confirm_title),
        stringResource(R.string.pref_confirm_body),
        confirmActions,
        onConfirmChange,
    )
    ToggleRow(
        stringResource(R.string.pref_speak_title),
        stringResource(R.string.pref_speak_body),
        speakNarration,
        onSpeakChange,
    )
    Text(stringResource(R.string.onboarding_prefs_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReadyStep(serviceRunning: Boolean, hasKey: Boolean) {
    Text(stringResource(R.string.onboarding_ready_title), style = MaterialTheme.typography.headlineSmall)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (serviceRunning) R.string.onboarding_ready_accessibility_on else R.string.onboarding_ready_accessibility_off))
            Text(stringResource(if (hasKey) R.string.onboarding_ready_key_set else R.string.onboarding_ready_key_unset))
        }
    }
    Text(stringResource(R.string.onboarding_try_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.onboarding_try_example), style = MaterialTheme.typography.bodyMedium)
}
