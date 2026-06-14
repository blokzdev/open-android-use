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
        topBar = { TopAppBar(title = { Text("Set up · Step ${step + 1} of $STEP_COUNT") }) },
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
                    OutlinedButton(onClick = { step-- }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                when (step) {
                    0 -> Button(onClick = { step = 1 }) { Text("Get started") }
                    1 -> Button(onClick = { step = 2 }) {
                        Text(if (serviceRunning) "Continue" else "Skip for now")
                    }
                    2 -> Button(onClick = { step = 3 }) { Text("Continue") }
                    3 -> {
                        TextButton(onClick = { step = 4 }) { Text("Skip for now") }
                        Button(onClick = {
                            val key = apiKey.trim()
                            if (key.isNotEmpty()) {
                                settings.storeApiKey(key)
                                settings.model = model
                                onRefreshModels()
                            }
                            step = 4
                        }) { Text("Save & continue") }
                    }
                    4 -> Button(onClick = {
                        settings.confirmActions = confirmActions
                        settings.speakNarration = speakNarration
                        step = 5
                    }) { Text("Continue") }
                    else -> {
                        TextButton(onClick = { onComplete(false) }) { Text("Done") }
                        Button(onClick = { onComplete(true) }) { Text("Open Agent Chat") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("A second pair of hands", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Open Android Use is an AI agent that operates this phone for you — it sees " +
            "the screen and taps, types, and scrolls on your behalf. Everything runs " +
            "on this device; you stay in control and can stop it at any time.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("This quick setup takes about a minute.", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AccessibilityStep(serviceRunning: Boolean, onOpen: () -> Unit) {
    Text("Enable the accessibility service", style = MaterialTheme.typography.headlineSmall)
    Text(
        "The agent works through Android's accessibility service — that's how it sees " +
            "the screen and performs taps and gestures. Turn it on for \"Open Android " +
            "Use Companion\".",
        style = MaterialTheme.typography.bodyMedium,
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (serviceRunning) "Service: running ✓" else "Service: not enabled yet",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text("Open Accessibility Settings")
    }
    Text(stringResource(R.string.restricted_setting_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PrivacyStep() {
    Text("Privacy & control", style = MaterialTheme.typography.headlineSmall)
    PrivacyPoint("On-device", "The agent runs here. The control endpoint binds to this device only (127.0.0.1).")
    PrivacyPoint("What leaves the device", "Only your task and screen context, sent to the model provider you choose (api.anthropic.com) to decide the next action.")
    PrivacyPoint("Your API key", "Stored encrypted in the Android Keystore — it never leaves the device except to the provider.")
    PrivacyPoint("Kill switch", "Press Stop any time. Disabling the accessibility service fully cuts the agent off.")
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
    Text("Add your API key", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Paste an Anthropic API key to let the agent run. You can skip this and add " +
            "it later — you'll be prompted when you start a task.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("Anthropic API key (sk-ant-…)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Model", style = MaterialTheme.typography.titleSmall)
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
    Text("Preferences", style = MaterialTheme.typography.headlineSmall)
    ToggleRow(
        "Ask before each action batch",
        "Show a confirmation sheet before the agent taps or types.",
        confirmActions,
        onConfirmChange,
    )
    ToggleRow(
        "Speak narration aloud",
        "The agent reads its narration out loud while it works.",
        speakNarration,
        onSpeakChange,
    )
    Text("You can change these any time in Agent Chat → Settings.", style = MaterialTheme.typography.bodySmall)
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
    Text("You're set", style = MaterialTheme.typography.headlineSmall)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (serviceRunning) "Accessibility: on ✓" else "Accessibility: off — enable it to let the agent act")
            Text(if (hasKey) "API key: set ✓" else "API key: not set — add it when you start a task")
        }
    }
    Text("Try a first task:", style = MaterialTheme.typography.titleSmall)
    Text("\"Open Settings and tell me the Android version\"", style = MaterialTheme.typography.bodyMedium)
}
