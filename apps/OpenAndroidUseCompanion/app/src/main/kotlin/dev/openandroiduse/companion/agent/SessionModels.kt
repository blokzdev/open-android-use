package dev.openandroiduse.companion.agent

/**
 * Persisted conversation session (Phase 4.5 multi-session). A session stores its
 * [transcript] — the chat UI's (kind, text) view — and metadata. The agent's
 * model history is *not* stored: on resume it is rebuilt from the transcript
 * (see [SessionHistory.rebuild]) so the agent continues with the conversation's
 * context and re-observes the device live. This keeps persistence text-only and
 * honors the invariant that screenshots never touch disk.
 */
data class SessionPayload(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val transcript: List<StoredMessage>,
    val pinned: Boolean = false,
    val preview: String = "",
)

/**
 * One transcript line: an [AgentController] KIND_* and its rendered text, plus when it started
 * ([createdAt], epoch millis; 0 for legacy sessions saved before Phase 4.7b-3).
 */
data class StoredMessage(val kind: String, val text: String, val createdAt: Long = 0L)

/**
 * A live transcript line for the chat UI (Phase 4.7b-3): an [AgentController] KIND_*, its current
 * text, and when it started ([createdAt], epoch millis). Carries the timestamp the chat renders
 * and groups by; pure (Android/SDK-free) so export/grouping logic can use it off the UI thread.
 */
data class TranscriptEntry(val kind: String, val text: String, val createdAt: Long)

/** Lightweight session metadata for the History list, without parsing the transcript. */
data class SessionMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val pinned: Boolean = false,
    val preview: String = "",
)
