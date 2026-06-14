package dev.openandroiduse.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Readable line length cap; on phones this is a no-op, on tablets/foldables it centers content. */
val ContentMaxWidth = 640.dp

/**
 * Wraps a screen's scaffold body so its content is centered and capped at
 * [ContentMaxWidth] on large screens (Phase 4.6c) — phones are unaffected.
 * Applies the scaffold [contentPadding] (which, with edge-to-edge, carries the
 * system-bar insets) and hands the inner content a width-capped, fill modifier.
 */
@Composable
fun ResponsiveContent(
    contentPadding: PaddingValues,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        content(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize())
    }
}
