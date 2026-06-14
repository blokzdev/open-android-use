package dev.openandroiduse.companion.agent

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme

/**
 * History (Phase 4.5 multi-session): the full-screen saved-conversations list, used on
 * compact (phone) widths. The list UI lives in [SessionsList] so it is shared with the
 * tablet/foldable two-pane in ChatActivity (Phase 4.6e). Resuming or deleting the in-memory
 * session is blocked while a task is running.
 */
class SessionsActivity : ComponentActivity() {

    private lateinit var settings: AgentSettings
    private lateinit var store: SessionStore
    private var sessions by mutableStateOf<List<SessionMeta>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        store = SessionStore(this)
        enableEdgeToEdge()
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                SessionsScreen(
                    sessions = sessions,
                    onResume = ::resume,
                    onRename = { id, title -> store.rename(id, title); AgentController.noteRenamed(id, title); reload() },
                    onArchive = { id, archived -> store.setArchived(id, archived); reload() },
                    onDelete = { id ->
                        if (AgentController.isRunning && id == AgentController.currentSessionId) {
                            toast(getString(R.string.sessions_busy_delete))
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
            toast(getString(R.string.sessions_busy_open))
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sessions_title)) }) },
    ) { padding ->
        SessionsList(
            sessions = sessions,
            onResume = onResume,
            onRename = onRename,
            onArchive = onArchive,
            onDelete = onDelete,
            modifier = Modifier.padding(padding),
        )
    }
}
