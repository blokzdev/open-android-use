package dev.openandroiduse.companion.agent

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.showUndo
import kotlinx.coroutines.launch

/**
 * The saved-conversations list (Phase 4.5), extracted (4.6e) so it is shared by the
 * full-screen History (`SessionsActivity`) and the tablet/foldable two-pane in
 * `ChatActivity`. Shows the empty state, or rows grouped (Phase 4.7c) into Pinned / Today /
 * Yesterday / Earlier with a last-message preview, pin/star, and resume / rename / archive /
 * delete.
 */
@Composable
internal fun SessionsList(
    sessions: List<SessionMeta>,
    onResume: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.sessions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val sections = remember(sessions) {
            SessionGrouping.group(sessions, System.currentTimeMillis())
        }
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (section in sections) {
                item(key = "header-${section.bucket}") {
                    SectionHeader(section.bucket)
                }
                items(section.sessions, key = { it.id }) { meta ->
                    SessionRow(meta, onResume, onRename, onArchive, onDelete, onPin)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(bucket: SessionGrouping.Bucket) {
    val label = stringResource(
        when (bucket) {
            SessionGrouping.Bucket.PINNED -> R.string.sessions_section_pinned
            SessionGrouping.Bucket.TODAY -> R.string.sessions_section_today
            SessionGrouping.Bucket.YESTERDAY -> R.string.sessions_section_yesterday
            SessionGrouping.Bucket.EARLIER -> R.string.sessions_section_earlier
        },
    )
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).markHeading(),
    )
}

/**
 * Wraps a delete handler with a Snackbar + Undo (Phase 4.7a-2). [delete] performs the
 * deletion and returns the removed [SessionPayload] (or null when the delete was blocked,
 * e.g. the running session). When a payload comes back, a Snackbar offers Undo; tapping it
 * re-saves the payload via [restore]. Shared by the full-screen History and the two-pane
 * History so both surfaces recover a mistaken delete identically.
 */
@Composable
internal fun rememberDeleteWithUndo(
    host: SnackbarHostState,
    delete: (String) -> SessionPayload?,
    restore: (SessionPayload) -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.sessions_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    return { id ->
        val removed = delete(id)
        if (removed != null) {
            scope.launch {
                if (host.showUndo(message, undoLabel)) restore(removed)
            }
        }
    }
}

@Composable
private fun SessionRow(
    meta: SessionMeta,
    onResume: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val when_ = remember(meta.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            meta.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    ElevatedCard(Modifier.fillMaxWidth().clickable { onResume(meta.id) }) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.sessions_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        )
                    }
                    Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.weight(1f))
                }
                if (meta.preview.isNotBlank()) {
                    Text(
                        meta.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when_, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.archived) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                stringResource(R.string.sessions_archived),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Box {
                val moreDesc = stringResource(R.string.sessions_more_options, meta.title)
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = moreDesc)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sessions_resume)) }, onClick = { menuOpen = false; onResume(meta.id) })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (meta.pinned) R.string.sessions_unpin else R.string.sessions_pin)) },
                        onClick = { menuOpen = false; onPin(meta.id, !meta.pinned) },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.sessions_rename)) }, onClick = { menuOpen = false; renaming = true })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (meta.archived) R.string.sessions_unarchive else R.string.sessions_archive)) },
                        onClick = { menuOpen = false; onArchive(meta.id, !meta.archived) },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; confirmDelete = true })
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(meta.title, onDismiss = { renaming = false }) { newTitle ->
            renaming = false
            if (newTitle.isNotBlank()) onRename(meta.id, newTitle.trim())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sessions_delete_q)) },
            text = { Text(stringResource(R.string.sessions_delete_body, meta.title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(meta.id) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sessions_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
