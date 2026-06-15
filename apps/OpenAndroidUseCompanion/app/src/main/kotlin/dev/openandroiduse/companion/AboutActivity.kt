package dev.openandroiduse.companion

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.ui.ResponsiveContent
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
import dev.openandroiduse.companion.ui.theme.Spacing

/**
 * About / attribution surface: app identity and version, project + contact
 * links, and the open-source licenses notice. Doubles as the licenses screen a
 * Play Store listing expects.
 */
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenAndroidUseTheme {
                AboutScreen(
                    version = BuildConfig.VERSION_NAME,
                    onOpenUrl = ::openUri,
                    onEmail = ::sendEmail,
                )
            }
        }
    }

    private fun openUri(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun sendEmail(address: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }
}

private fun tierLabel(tier: DeviceTier): Int = when (tier) {
    DeviceTier.HIGH -> R.string.device_tier_high
    DeviceTier.MEDIUM -> R.string.device_tier_medium
    DeviceTier.LOW -> R.string.device_tier_low
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    version: String,
    onOpenUrl: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        ResponsiveContent(contentPadding) { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.about_tagline) + "\n" +
                    stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Device diagnostic (Phase 5.4): honest capability facts + tier. The
            // tier is the signal the on-device model tier (5.5) and adaptive
            // perception (5.6) will gate on; here it's surfaced for transparency.
            val context = LocalContext.current
            val capability = remember { detectDeviceCapability(context) }
            Text(
                text = stringResource(R.string.about_device_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.about_device_facts,
                    stringResource(tierLabel(capability.tier)),
                    Formatter.formatShortFileSize(context, capability.totalRamBytes),
                    capability.cpuCores,
                    Build.VERSION.RELEASE ?: capability.sdkInt.toString(),
                    stringResource(if (capability.is64Bit) R.string.device_abi_64 else R.string.device_abi_32),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.about_links_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val githubUrl = stringResource(R.string.about_github_url)
            val xUrl = stringResource(R.string.about_x_url)
            val email = stringResource(R.string.about_email)
            OutlinedButton(onClick = { onOpenUrl(githubUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_github_label))
            }
            OutlinedButton(onClick = { onOpenUrl(xUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_x_label))
            }
            OutlinedButton(onClick = { onEmail(email) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_email_label))
            }

            Text(
                text = stringResource(R.string.about_licenses_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.about_licenses_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        }
    }
}
