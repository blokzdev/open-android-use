package dev.openandroiduse.companion.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale (Phase 4.7a-3): formalizes the de-facto 4dp grid the app already used
 * (8/12/16dp dominant) so structural spacing — screen padding, `Arrangement.spacedBy`, card
 * padding — is consistent and named rather than a scatter of magic numbers. Genuine one-off values
 * (icon sizes, max widths) stay inline.
 */
object Spacing {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 24.dp
}
