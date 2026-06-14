package dev.openandroiduse.companion

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * About / attribution surface: app identity and version, project + contact
 * links, and the open-source licenses notice (this app under PolyForm Perimeter,
 * the engine under MIT, the bundled Anthropic SDK under Apache-2.0). This
 * doubles as the open-source-licenses screen a Play Store listing expects.
 *
 * Deliberately plain Views and dependency-free, matching MainActivity; Phase 4
 * (docs/design-docs/phase4-product-ui.md) restyles it under the new design
 * system.
 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        layout.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, padding / 2, 0, 0)
            text = "${getString(R.string.about_tagline)}\nVersion ${BuildConfig.VERSION_NAME}"
        })

        layout.addView(sectionTitle(getString(R.string.about_links_title), padding))
        addLink(layout, getString(R.string.about_github_label)) {
            openUri(getString(R.string.about_github_url))
        }
        addLink(layout, getString(R.string.about_x_label)) {
            openUri(getString(R.string.about_x_url))
        }
        addLink(layout, getString(R.string.about_email_label)) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${getString(R.string.about_email)}"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }

        layout.addView(sectionTitle(getString(R.string.about_licenses_title), padding))
        layout.addView(TextView(this).apply {
            textSize = 13f
            text = getString(R.string.about_licenses_body)
        })

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun sectionTitle(text: String, padding: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, padding, 0, padding / 2)
        }

    private fun addLink(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        })
    }

    private fun openUri(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }
}
