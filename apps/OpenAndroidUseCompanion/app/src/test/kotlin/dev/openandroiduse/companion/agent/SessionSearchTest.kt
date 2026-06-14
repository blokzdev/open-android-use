package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSearchTest {

    private fun meta(id: String, title: String, preview: String = "") =
        SessionMeta(id = id, title = title, createdAt = 0, updatedAt = 0, archived = false, preview = preview)

    private val sessions = listOf(
        meta("a", "Turn Bluetooth on", "Done — Bluetooth is on."),
        meta("b", "Open Settings", "Android 15"),
        meta("c", "Summarize my notification"),
    )

    @Test
    fun blankQueryReturnsAll() {
        assertEquals(sessions, SessionSearch.filter(sessions, "   "))
    }

    @Test
    fun matchesTitleCaseInsensitively() {
        assertEquals(listOf("a"), SessionSearch.filter(sessions, "bluetooth").map { it.id })
    }

    @Test
    fun matchesPreview() {
        assertEquals(listOf("b"), SessionSearch.filter(sessions, "android").map { it.id })
    }

    @Test
    fun noMatchesYieldsEmpty() {
        assertEquals(emptyList<String>(), SessionSearch.filter(sessions, "zzz").map { it.id })
    }
}
