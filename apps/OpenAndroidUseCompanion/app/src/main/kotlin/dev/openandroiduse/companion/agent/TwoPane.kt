package dev.openandroiduse.companion.agent

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * Decides when the chat shows the History list beside it (Phase 4.6e). Two-pane only at
 * Expanded width (tablets, unfolded foldables, large/desktop windows); compact and medium
 * stay single-pane with the existing phone flow. Pure so it is unit-testable.
 */
object TwoPane {
    fun isTwoPane(width: WindowWidthSizeClass): Boolean = width == WindowWidthSizeClass.Expanded
}
