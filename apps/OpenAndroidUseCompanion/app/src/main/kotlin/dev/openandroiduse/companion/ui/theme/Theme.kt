package dev.openandroiduse.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Violet,
    tertiary = Teal,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = VioletLight,
    tertiary = Mint,
    surface = IndigoDeep,
)

/** Light/dark preference (Phase 4.7e): follow the system, or force light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * App theme for every Compose surface, built on the "Aurora" brand palette
 * (see Color.kt) so the product has a consistent identity. [themeMode] chooses
 * light/dark (or follows the system); dynamic color (Material You) is opt-in and
 * off by default — we lead with the brand colors that match the app icon.
 */
@Composable
fun OpenAndroidUseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
