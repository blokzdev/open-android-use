package dev.openandroiduse.companion.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * Shows a snackbar with an Undo action (Phase 4.7a-2) and returns true if the user tapped
 * Undo. Callers capture the pre-action state, perform the destructive change, then run their
 * restore lambda only when this returns true.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean =
    showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short) ==
        SnackbarResult.ActionPerformed
