package dev.openandroiduse.companion.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Context-free, SDK-free) JSON serialization for persisted sessions: just
 * the transcript and metadata via org.json. The agent's model history is rebuilt
 * from the transcript on resume ([SessionHistory.rebuild]), so nothing here ever
 * touches the Anthropic SDK types — keeping it trivially unit-testable on the JVM
 * with the real org.json test dependency.
 */
object SessionCodec {

    // v2 (Phase 4.7c) adds `pinned` + `preview`; v3 (Phase 4.7b-3) adds a per-message timestamp
    // (`t`). Decode stays back-compatible with older files via opt* defaults (pinned=false,
    // preview="", t=0).
    const val VERSION = 3

    fun encode(payload: SessionPayload): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("id", payload.id)
        root.put("title", payload.title)
        root.put("createdAt", payload.createdAt)
        root.put("updatedAt", payload.updatedAt)
        root.put("archived", payload.archived)
        root.put("pinned", payload.pinned)
        root.put("preview", payload.preview)
        val transcript = JSONArray()
        for (line in payload.transcript) {
            val o = JSONObject().put("kind", line.kind).put("text", line.text)
            if (line.createdAt != 0L) o.put("t", line.createdAt)
            transcript.put(o)
        }
        root.put("transcript", transcript)
        return root.toString()
    }

    fun decode(json: String): SessionPayload {
        val root = JSONObject(json)
        val transcript = root.optJSONArray("transcript") ?: JSONArray()
        val messages = (0 until transcript.length()).map { i ->
            val o = transcript.getJSONObject(i)
            StoredMessage(o.optString("kind"), o.optString("text"), o.optLong("t", 0L))
        }
        return SessionPayload(
            id = root.getString("id"),
            title = root.optString("title", SessionTitle.FALLBACK),
            createdAt = root.optLong("createdAt"),
            updatedAt = root.optLong("updatedAt"),
            archived = root.optBoolean("archived", false),
            transcript = messages,
            pinned = root.optBoolean("pinned", false),
            preview = root.optString("preview", ""),
        )
    }

    /** Reads metadata only — no transcript decoding — for the History list. */
    fun decodeMeta(json: String): SessionMeta {
        val root = JSONObject(json)
        return SessionMeta(
            id = root.getString("id"),
            title = root.optString("title", SessionTitle.FALLBACK),
            createdAt = root.optLong("createdAt"),
            updatedAt = root.optLong("updatedAt"),
            archived = root.optBoolean("archived", false),
            pinned = root.optBoolean("pinned", false),
            preview = root.optString("preview", ""),
        )
    }
}
