package dev.openandroiduse.companion.agent

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * History (Phase 4.5 multi-session): the saved conversations, newest first, with
 * resume (restores full model context), rename, archive, and delete. Resuming or
 * deleting the in-memory session is blocked while a task is running.
 */
class SessionsActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private lateinit var store: SessionStore
    private var sessions by mutableStateOf<List<SessionMeta>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        store = SessionStore(this)
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                SessionsScreen(
                    sessions = sessions,
                    onResume = ::resume,
                    onRename = { id, title -> store.rename(id, title); AgentController.noteRenamed(id, title); reload() },
                    onArchive = { id, archived -> store.setArchived(id, archived); reload() },
                    onDelete = { id ->
                        if (AgentController.isRunning && id == AgentController.currentSessionId) {
                            toast("Stop the current task before deleting it")
                        } else {
                            store.delete(id); reload()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        sessions = store.list()
    }

    private fun resume(id: String) {
        if (AgentController.isRunning) {
            toast("Stop the current task before opening another conversation")
            return
        }
        startActivity(
            Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_SESSION_ID, id),
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsScreen(
    sessions: List<SessionMeta>,
    onResume: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No saved conversations yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions, key = { it.id }) { meta ->
                    SessionRow(meta, onResume, onRename, onArchive, onDelete)
                }
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
                Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when_, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.archived) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "Archived",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("⋯") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Resume") }, onClick = { menuOpen = false; onResume(meta.id) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                    DropdownMenuItem(
                        text = { Text(if (meta.archived) "Unarchive" else "Archive") },
                        onClick = { menuOpen = false; onArchive(meta.id, !meta.archived) },
                    )
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; confirmDelete = true })
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
            title = { Text("Delete conversation?") },
            text = { Text("This permanently deletes \"${meta.title}\".") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(meta.id) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
