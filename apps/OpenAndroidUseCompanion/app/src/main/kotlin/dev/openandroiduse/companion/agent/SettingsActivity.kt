package dev.openandroiduse.companion.agent

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import dev.openandroiduse.companion.AboutActivity
import dev.openandroiduse.companion.DeviceTier
import dev.openandroiduse.companion.OnboardingActivity
import dev.openandroiduse.companion.PrivacyActivity
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.detectDeviceCapability
import dev.openandroiduse.companion.agent.llm.LlmProvider
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
import dev.openandroiduse.companion.ui.theme.Spacing
import dev.openandroiduse.companion.ui.theme.ThemeMode

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
        enableEdgeToEdge()
        setContent {
            OpenAndroidUseTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                SettingsScreen(
                    settings = settings,
                    onDynamicColorChanged = {
                        // Recreate so the new color scheme applies immediately.
                        recreate()
                    },
                    onThemeModeChanged = { recreate() },
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
    onThemeModeChanged: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onRerunSetup: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var provider by remember { mutableStateOf(settings.selectedProvider) }
    val deviceTier = remember { detectDeviceCapability(context).tier }
    var apiKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(settings.hasApiKey(provider)) }
    var keyVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(settings.modelFor(provider)) }
    var confirmActions by remember { mutableStateOf(settings.confirmActions) }
    var speak by remember { mutableStateOf(settings.speakNarration) }
    var dynamic by remember { mutableStateOf(settings.dynamicColor) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }

    val validMsg = stringResource(R.string.settings_key_valid)
    val invalidFmt = stringResource(R.string.settings_key_invalid)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { contentPadding ->
        ResponsiveContent(contentPadding) { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // --- Provider ---
            SectionTitle(stringResource(R.string.settings_section_provider))
            ProviderSelector(provider) { picked ->
                if (picked == provider) return@ProviderSelector
                settings.selectedProvider = picked
                provider = picked
                // Re-scope every field to the newly selected provider.
                apiKey = ""
                hasKey = settings.hasApiKey(picked)
                model = settings.modelFor(picked)
            }

            HorizontalDivider()

            if (!provider.requiresApiKey) {
                // --- On-device model (no API key; downloaded locally) ---
                SectionTitle(stringResource(R.string.ondevice_title))
                OnDeviceModelCard(deviceTier)
            } else {
            // --- API key ---
            SectionTitle(stringResource(R.string.settings_section_api_key))
            Text(
                stringResource(
                    if (hasKey) R.string.settings_api_key_configured else R.string.settings_api_key_add,
                    provider.displayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (keyVisible) R.string.settings_key_hide else R.string.settings_key_show),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val key = apiKey.trim()
                        if (key.isEmpty()) return@Button
                        settings.storeApiKey(key, provider)
                        apiKey = ""
                        hasKey = true
                        Thread({ ModelCatalog.refresh(provider, settings) }, "oau-model-refresh").start()
                        Toast.makeText(context, context.getString(R.string.settings_key_saved), Toast.LENGTH_SHORT).show()
                    },
                    enabled = apiKey.isNotBlank(),
                ) { Text(stringResource(R.string.settings_save_key)) }
                // Test the entered key, or the saved key when the field is empty.
                OutlinedButton(
                    onClick = {
                        val key = apiKey.trim().ifEmpty { settings.loadApiKey(provider) } ?: return@OutlinedButton
                        testing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ModelCatalog.validateKey(provider, key, settings.baseUrlOverride)
                            }
                            testing = false
                            snackbarHost.showSnackbar(
                                when (result) {
                                    is ModelCatalog.KeyTest.Valid -> validMsg
                                    is ModelCatalog.KeyTest.Invalid -> invalidFmt.format(result.message)
                                },
                            )
                        }
                    },
                    enabled = !testing && (apiKey.isNotBlank() || hasKey),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_test_key))
                    }
                }
                OutlinedButton(
                    onClick = {
                        settings.clearApiKey(provider)
                        hasKey = false
                        Toast.makeText(context, context.getString(R.string.settings_key_cleared), Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasKey,
                ) { Text(stringResource(R.string.settings_clear_key)) }
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyHelpUrl)))
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text(stringResource(R.string.settings_get_key)) }

            HorizontalDivider()

            // --- Model ---
            SectionTitle(stringResource(R.string.label_model))
            ModelDropdown(
                models = settings.availableModels(provider),
                selected = model,
                onSelected = {
                    model = it
                    settings.setModel(it, provider)
                },
            )
            }

            HorizontalDivider()

            // --- Behavior ---
            SectionTitle(stringResource(R.string.settings_section_behavior))
            Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleSmall)
            ThemeModeSelector(themeMode) {
                themeMode = it
                settings.themeMode = it
                onThemeModeChanged()
            }
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
}

@Composable
private fun OnDeviceModelCard(tier: DeviceTier) {
    val context = LocalContext.current
    val workInfos by OnDeviceModelManager.downloadFlow(context).collectAsState(initial = emptyList())
    val info = workInfos.firstOrNull()
    val downloading = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
    val percent = info?.progress?.getInt(OnDeviceModelManager.PROGRESS_PERCENT, 0) ?: 0
    // Re-check readiness whenever the work state changes (e.g. SUCCEEDED).
    var ready by remember { mutableStateOf(OnDeviceModelManager.isReady(context)) }
    LaunchedEffect(info?.state) { ready = OnDeviceModelManager.isReady(context) }

    Text(stringResource(R.string.ondevice_desc), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.ondevice_experimental), style = MaterialTheme.typography.bodySmall)
    when (tier) {
        DeviceTier.LOW -> Text(stringResource(R.string.ondevice_tier_low), style = MaterialTheme.typography.bodySmall)
        DeviceTier.MEDIUM -> Text(stringResource(R.string.ondevice_tier_medium), style = MaterialTheme.typography.bodySmall)
        DeviceTier.HIGH -> Unit
    }
    when {
        ready -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ondevice_ready), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = {
                OnDeviceModelManager.delete(context)
                ready = false
            }) { Text(stringResource(R.string.ondevice_delete)) }
        }
        downloading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ondevice_downloading, percent))
                OutlinedButton(onClick = { OnDeviceModelManager.cancelDownload(context) }) {
                    Text(stringResource(R.string.ondevice_cancel))
                }
            }
        }
        else -> Button(
            onClick = { OnDeviceModelManager.enqueueDownload(context) },
            enabled = tier != DeviceTier.LOW,
        ) { Text(stringResource(R.string.ondevice_download)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSelector(selected: LlmProvider, onSelect: (LlmProvider) -> Unit) {
    val options = LlmProvider.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, provider ->
            SegmentedButton(
                selected = selected == provider,
                onClick = { onSelect(provider) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(provider.displayName) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
