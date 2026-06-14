package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.agent.AgentSettings
import dev.openandroiduse.companion.agent.ChatActivity
import dev.openandroiduse.companion.agent.SessionsActivity
import dev.openandroiduse.companion.agent.SettingsActivity
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * Status and consent surface: shows whether the accessibility service (and so
 * the loopback endpoint) is running, and routes the user to system settings to
 * enable or disable it. Disabling the service is the kill switch.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private var serviceRunning by mutableStateOf(false)
    private var hasApiKey by mutableStateOf(false)

    /** The dynamic-color value this instance was themed with, to detect a Settings toggle. */
    private var appliedDynamicColor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
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
                    restrictedHint = stringResource(R.string.restricted_setting_hint),
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenChat = {
                        startActivity(Intent(this, ChatActivity::class.java))
                    },
                    onOpenHistory = {
                        startActivity(Intent(this, SessionsActivity::class.java))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onOpenAbout = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    running: Boolean,
    hasApiKey: Boolean,
    port: Int,
    restrictedHint: String,
    onOpenAccessibility: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        ResponsiveContent(contentPadding) { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (readiness(running, hasApiKey)) {
                                Readiness.READY -> R.string.main_status_ready
                                Readiness.NEEDS_ACCESSIBILITY -> R.string.main_status_accessibility_off
                                Readiness.NEEDS_KEY -> R.string.main_status_key_needed
                                Readiness.NEEDS_BOTH -> R.string.main_status_setup_needed
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (running) {
                            stringResource(R.string.main_service_running, port)
                        } else {
                            stringResource(R.string.main_service_off)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!hasApiKey) {
                        Text(
                            text = stringResource(R.string.main_key_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (!running) {
                Text(text = restrictedHint, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_accessibility))
            }
            Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_open_chat))
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.main_history))
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.main_settings))
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_button))
            }

            Text(
                text = stringResource(R.string.main_kill_switch_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        }
    }
}
