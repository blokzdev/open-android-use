package dev.openandroiduse.companion.agent

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.AboutActivity
import dev.openandroiduse.companion.OnboardingActivity
import dev.openandroiduse.companion.PrivacyActivity
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * The settings home (Phase 4.5), promoting the former cramped in-chat dialog
 * into a real screen: API key (with a Clear control), model picker, the
 * confirmation/voice toggles, a Material You switch, and links to Privacy &
 * data, About, and re-running first-run setup. Toggles and the model apply
 * immediately; the API key has an explicit Save/Clear because it is sensitive.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                SettingsScreen(
                    settings = settings,
                    onDynamicColorChanged = {
                        // Recreate so the new color scheme applies immediately.
                        recreate()
                    },
                    onOpenPrivacy = { startActivity(Intent(this, PrivacyActivity::class.java)) },
                    onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                    onRerunSetup = {
                        settings.onboardingCompleted = false
                        startActivity(Intent(this, OnboardingActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AgentSettings,
    onDynamicColorChanged: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onRerunSetup: () -> Unit,
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(settings.hasApiKey()) }
    var model by remember { mutableStateOf(settings.model) }
    var confirmActions by remember { mutableStateOf(settings.confirmActions) }
    var speak by remember { mutableStateOf(settings.speakNarration) }
    var dynamic by remember { mutableStateOf(settings.dynamicColor) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- API key ---
            SectionTitle(stringResource(R.string.settings_section_api_key))
            Text(
                stringResource(
                    if (hasKey) R.string.settings_api_key_configured else R.string.settings_api_key_add,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val key = apiKey.trim()
                        if (key.isEmpty()) return@Button
                        settings.storeApiKey(key)
                        apiKey = ""
                        hasKey = true
                        Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
                        Toast.makeText(context, context.getString(R.string.settings_key_saved), Toast.LENGTH_SHORT).show()
                    },
                    enabled = apiKey.isNotBlank(),
                ) { Text(stringResource(R.string.settings_save_key)) }
                OutlinedButton(
                    onClick = {
                        settings.clearApiKey()
                        hasKey = false
                        Toast.makeText(context, context.getString(R.string.settings_key_cleared), Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasKey,
                ) { Text(stringResource(R.string.settings_clear_key)) }
            }

            HorizontalDivider()

            // --- Model ---
            SectionTitle(stringResource(R.string.label_model))
            ModelDropdown(
                models = settings.availableModels(),
                selected = model,
                onSelected = {
                    model = it
                    settings.model = it
                },
            )

            HorizontalDivider()

            // --- Behavior ---
            SectionTitle(stringResource(R.string.settings_section_behavior))
            SettingToggle(
                stringResource(R.string.pref_confirm_title),
                stringResource(R.string.pref_confirm_body),
                confirmActions,
            ) { confirmActions = it; settings.confirmActions = it }
            SettingToggle(
                stringResource(R.string.pref_speak_title),
                stringResource(R.string.pref_speak_body),
                speak,
            ) { speak = it; settings.speakNarration = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingToggle(
                    stringResource(R.string.settings_material_you_title),
                    stringResource(R.string.settings_material_you_body),
                    dynamic,
                ) {
                    dynamic = it
                    settings.dynamicColor = it
                    onDynamicColorChanged()
                }
            }

            HorizontalDivider()

            // --- More ---
            SectionTitle(stringResource(R.string.settings_section_more))
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_privacy))
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_about))
            }
            OutlinedButton(onClick = onRerunSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_rerun_setup))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.markHeading())
}

@Composable
private fun SettingToggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
