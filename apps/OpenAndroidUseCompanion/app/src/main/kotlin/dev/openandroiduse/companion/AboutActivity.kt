package dev.openandroiduse.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * About / attribution surface: app identity and version, project + contact
 * links, and the open-source licenses notice. Doubles as the licenses screen a
 * Play Store listing expects.
 */
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_tagline) + "\n" +
                    stringResource(R.string.about_version, version),
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
