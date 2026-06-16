package dev.openandroiduse.companion.agent

import android.content.Context
import java.io.File

/**
 * Phase 6.5c-3: on-device persistence for **persistent** per-app trust grants — one small
 * JSON file (`filesDir/trust/grants.json`), encoded by [TrustCodec], written atomically
 * (temp + rename) like [SessionStore]. ONCE/SESSION grants are deliberately *not* here;
 * they live in memory and die with the task. Decay is enforced lazily: every read prunes
 * grants that have gone unused past [TrustPolicy.DECAY_DAYS] (no scheduler needed, matching
 * Android's auto-reset-of-unused-grants precedent).
 *
 * Reads (agent loop) and writes (UI revoke) can race, so all file ops hold one lock.
 */
class TrustStore(context: Context) {

    private val dir = File(context.filesDir, "trust")
    private val file = File(dir, "grants.json")
    private val lock = Any()

    private fun readAll(): List<TrustGrant> {
        if (!file.exists()) return emptyList()
        return runCatching { TrustCodec.decodeGrants(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeAll(grants: List<TrustGrant>) {
        dir.mkdirs()
        val tmp = File(dir, "grants.json.tmp")
        tmp.writeText(TrustCodec.encodeGrants(grants))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Package names with an active (non-decayed) grant; prunes decayed ones as a side effect. */
    fun activeGrants(now: Long): Set<String> = synchronized(lock) {
        val all = readAll()
        val active = all.filter { TrustPolicy.isActive(it, now) }
        if (active.size != all.size) writeAll(active)
        active.map { it.packageName }.toSet()
    }

    /** Grants for the Privacy "Trusted apps" list (active only), newest first. */
    fun list(now: Long): List<TrustGrant> = synchronized(lock) {
        val all = readAll()
        val active = all.filter { TrustPolicy.isActive(it, now) }
        if (active.size != all.size) writeAll(active)
        active.sortedByDescending { it.grantedAt }
    }

    /** Add or refresh a persistent grant for [packageName]. */
    fun grant(packageName: String, now: Long) = synchronized(lock) {
        val existing = readAll().filter { it.packageName != packageName }
        writeAll(existing + TrustGrant(packageName, GrantScope.PERSISTENT, grantedAt = now, lastUsedAt = now))
    }

    /** Bump last-used so an exercised grant doesn't decay. No-op if not present. */
    fun touch(packageName: String, now: Long) = synchronized(lock) {
        val all = readAll()
        if (all.none { it.packageName == packageName }) return@synchronized
        writeAll(all.map { if (it.packageName == packageName) TrustPolicy.touch(it, now) else it })
    }

    fun revoke(packageName: String) = synchronized(lock) {
        writeAll(readAll().filter { it.packageName != packageName })
    }

    fun revokeAll() = synchronized(lock) { writeAll(emptyList()) }
}
