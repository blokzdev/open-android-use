package dev.openandroiduse.companion.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Marks a composable as a heading so screen-reader users can navigate by
 * heading (Phase 4.6b). Named to avoid shadowing the semantics `heading()`.
 */
fun Modifier.markHeading(): Modifier = semantics { heading() }
