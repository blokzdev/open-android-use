package dev.openandroiduse.companion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)

        // First run: route to the onboarding wizard, then never again.
        if (!settings.onboardingCompleted) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContent {
            OpenAndroidUseTheme {
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
                    onOpenAbout = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when (readiness(running, hasApiKey)) {
                            Readiness.READY -> "Agent ready"
                            Readiness.NEEDS_ACCESSIBILITY -> "Accessibility off"
                            Readiness.NEEDS_KEY -> "API key needed"
                            Readiness.NEEDS_BOTH -> "Setup needed"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (running) {
                            "Service: running — endpoint live on 127.0.0.1:$port"
                        } else {
                            "Service: off — enable the accessibility service to start"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!hasApiKey) {
                        Text(
                            text = "API key not set — add it in Agent Chat to enable the agent.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (!running) {
                Text(text = restrictedHint, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                Text("Open Accessibility Settings")
            }
            Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                Text("Open Agent Chat")
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_button))
            }

            Text(
                text = "Disable the service at any time to cut the agent off. The " +
                    "endpoint binds 127.0.0.1 only and is reachable from a computer " +
                    "solely via `adb forward`.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
