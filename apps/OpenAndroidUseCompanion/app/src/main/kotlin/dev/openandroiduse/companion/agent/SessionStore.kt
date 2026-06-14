package dev.openandroiduse.companion.agent

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * On-device persistence for conversation sessions (Phase 4.5): one JSON file per
 * session under filesDir/sessions, encoded by [SessionCodec]. Text-only by
 * construction — screenshots are stripped before writing — so the saved history
 * is small and the privacy story ("screenshots never touch disk") holds.
 *
 * Saves can arrive from the agent loop thread (on task end) while the UI reads
 * the list, so every file operation is guarded by a single lock.
 */
class SessionStore(context: Context) {

    private val dir = File(context.filesDir, "sessions")
    private val lock = Any()

    private fun fileFor(id: String): File = File(dir, "${sanitize(id)}.json")

    /** Newest first; archived sessions are included (the UI groups them). */
    fun list(): List<SessionMeta> = synchronized(lock) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        files.mapNotNull { f ->
            runCatching { SessionCodec.decodeMeta(f.readText()) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): SessionPayload? = synchronized(lock) {
        val f = fileFor(id)
        if (!f.exists()) return null
        runCatching { SessionCodec.decode(f.readText()) }.getOrNull()
    }

    fun save(payload: SessionPayload) = synchronized(lock) {
        dir.mkdirs()
        val target = fileFor(payload.id)
        // Write to a temp file then rename so a crash can't leave a half-written
        // (unparseable) session file.
        val tmp = File(dir, "${sanitize(payload.id)}.json.tmp")
        tmp.writeText(SessionCodec.encode(payload))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun rename(id: String, title: String) = editMeta(id) { it.put("title", title) }

    fun setArchived(id: String, archived: Boolean) = editMeta(id) { it.put("archived", archived) }

    /** Pin/unpin. Doesn't bump updatedAt — pinning shouldn't reorder a conversation by recency. */
    fun setPinned(id: String, pinned: Boolean) = editMeta(id, bumpUpdatedAt = false) { it.put("pinned", pinned) }

    fun delete(id: String) = synchronized(lock) { fileFor(id).delete(); Unit }

    fun deleteAll() = synchronized(lock) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { it.delete() }
        Unit
    }

    /** In-place metadata edit that avoids decoding/re-encoding the heavy history. */
    private fun editMeta(id: String, bumpUpdatedAt: Boolean = true, edit: (JSONObject) -> Unit) = synchronized(lock) {
        val f = fileFor(id)
        if (!f.exists()) return
        val root = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return
        edit(root)
        if (bumpUpdatedAt) root.put("updatedAt", System.currentTimeMillis())
        f.writeText(root.toString())
    }

    private fun sanitize(id: String): String = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
}
