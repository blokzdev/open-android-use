package dev.openandroiduse.companion.agent

/**
 * Pure filtering for the History list (Phase 4.7c-2): a case-insensitive match over a
 * conversation's title and its last-message preview. No Android/SDK types, so it unit-tests on the
 * JVM. An empty/blank query returns the list unchanged.
 */
object SessionSearch {

    fun filter(sessions: List<SessionMeta>, query: String): List<SessionMeta> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return sessions
        return sessions.filter {
            it.title.lowercase().contains(q) || it.preview.lowercase().contains(q)
        }
    }
}
