package dev.openandroiduse.companion.agent

import android.content.Context
import java.io.File

/**
 * Phase 6.5c-3: an append-only, capped audit of what the agent did on a *trusted* app's
 * screens, so the user can answer "what has it done?" in Privacy. Text-only by construction
 * — only the tool name and the already-resolved [ActionSummary] string, never a field value
 * (redaction guarantees the summary can't contain a secret). Separate file from sessions
 * (`filesDir/trust/audit.json`) because the lifecycles differ. Ring-buffered to [MAX_ENTRIES].
 */
class TrustAuditLog(context: Context) {

    private val dir = File(context.filesDir, "trust")
    private val file = File(dir, "audit.json")
    private val lock = Any()

    private fun readAll(): List<AuditEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { TrustCodec.decodeAudit(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeAll(entries: List<AuditEntry>) {
        dir.mkdirs()
        val tmp = File(dir, "audit.json.tmp")
        tmp.writeText(TrustCodec.encodeAudit(entries))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun record(entry: AuditEntry) = synchronized(lock) {
        writeAll(TrustCodec.capped(readAll() + entry, MAX_ENTRIES))
    }

    /** All entries, newest first. */
    fun all(): List<AuditEntry> = synchronized(lock) { readAll().sortedByDescending { it.at } }

    /** Entries for one app, newest first. */
    fun forPackage(packageName: String): List<AuditEntry> = synchronized(lock) {
        readAll().filter { it.packageName == packageName }.sortedByDescending { it.at }
    }

    fun clear() = synchronized(lock) { writeAll(emptyList()) }

    companion object {
        const val MAX_ENTRIES = 500
    }
}
