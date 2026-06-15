package dev.openandroiduse.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.openandroiduse.companion.agent.Motion

/**
 * Compose accessor for the system "Remove animations" setting (Phase 4.7a-3), wrapping
 * [Motion.animationsDisabled] so screens can branch on reduce-motion with one call instead of
 * threading a Context around. Read per-composition so a mid-session toggle is reflected.
 */
@Composable
fun isReducedMotion(): Boolean = Motion.animationsDisabled(LocalContext.current)
