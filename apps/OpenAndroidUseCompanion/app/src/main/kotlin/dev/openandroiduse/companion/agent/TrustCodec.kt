package dev.openandroiduse.companion.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Context-free, SDK-free) JSON serialization for persisted trust grants and the
 * audit log, mirroring [SessionCodec]'s style so it is trivially unit-tested on the JVM
 * with the real org.json dependency. Persistence I/O lives in [TrustStore] / [TrustAuditLog].
 */
object TrustCodec {

    const val VERSION = 1

    fun encodeGrants(grants: List<TrustGrant>): String {
        val array = JSONArray()
        for (g in grants) {
            array.put(
                JSONObject()
                    .put("pkg", g.packageName)
                    .put("scope", g.scope.name)
                    .put("grantedAt", g.grantedAt)
                    .put("lastUsedAt", g.lastUsedAt),
            )
        }
        return JSONObject().put("version", VERSION).put("grants", array).toString()
    }

    fun decodeGrants(text: String): List<TrustGrant> {
        val array = runCatching { JSONObject(text).optJSONArray("grants") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<TrustGrant>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg")
            if (pkg.isEmpty()) continue
            val scope = runCatching { GrantScope.valueOf(o.optString("scope")) }.getOrDefault(GrantScope.PERSISTENT)
            out.add(
                TrustGrant(
                    packageName = pkg,
                    scope = scope,
                    grantedAt = o.optLong("grantedAt"),
                    lastUsedAt = o.optLong("lastUsedAt"),
                ),
            )
        }
        return out
    }

    fun encodeAudit(entries: List<AuditEntry>): String {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("pkg", e.packageName)
                    .put("tool", e.tool)
                    .put("summary", e.summary)
                    .put("at", e.at)
                    .put("scope", e.grantScope.name),
            )
        }
        return JSONObject().put("version", VERSION).put("entries", array).toString()
    }

    fun decodeAudit(text: String): List<AuditEntry> {
        val array = runCatching { JSONObject(text).optJSONArray("entries") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<AuditEntry>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg")
            if (pkg.isEmpty()) continue
            val scope = runCatching { GrantScope.valueOf(o.optString("scope")) }.getOrDefault(GrantScope.PERSISTENT)
            out.add(
                AuditEntry(
                    packageName = pkg,
                    tool = o.optString("tool"),
                    summary = o.optString("summary"),
                    at = o.optLong("at"),
                    grantScope = scope,
                ),
            )
        }
        return out
    }

    /** Keep only the most recent [max] entries (ring-buffer cap for the audit log). */
    fun capped(entries: List<AuditEntry>, max: Int): List<AuditEntry> =
        if (entries.size <= max) entries else entries.subList(entries.size - max, entries.size)
}
