package dev.openandroiduse.companion.agent.llm

/**
 * The egress guard for the debug-only base-URL override (Phase 5.8). The agent
 * lets a debug build point an SDK client at a loopback stub model server (the
 * emulator smoke), but must never let an override redirect real provider traffic
 * to an arbitrary remote host. This is the pure, unit-tested chokepoint for that
 * rule; `AgentController` consults it (release builds pass `null` regardless).
 */
object EgressPolicy {

    /** Accepts only http loopback URLs; anything else (incl. https/remote) is rejected. */
    fun loopbackOrNull(url: String?): String? {
        if (url == null) return null
        return if (url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")) url else null
    }
}
