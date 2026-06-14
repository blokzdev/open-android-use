package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGroupingTest {

    private val now = SessionGrouping.startOfDay(System.currentTimeMillis()) + 12L * 60 * 60 * 1000
    private val todayStart = SessionGrouping.startOfDay(now)
    private val dayMs = 24L * 60 * 60 * 1000

    private fun meta(id: String, updatedAt: Long, pinned: Boolean = false) =
        SessionMeta(id = id, title = id, createdAt = 0, updatedAt = updatedAt, archived = false, pinned = pinned)

    @Test
    fun bucketsByCalendarDay() {
        val sessions = listOf(
            meta("today", todayStart + 1_000),
            meta("yesterday", todayStart - 1_000),
            meta("earlier", todayStart - 3 * dayMs),
        )
        val sections = SessionGrouping.group(sessions, now)
        assertEquals(
            listOf(
                SessionGrouping.Bucket.TODAY,
                SessionGrouping.Bucket.YESTERDAY,
                SessionGrouping.Bucket.EARLIER,
            ),
            sections.map { it.bucket },
        )
        assertEquals("today", sections[0].sessions.single().id)
        assertEquals("yesterday", sections[1].sessions.single().id)
        assertEquals("earlier", sections[2].sessions.single().id)
    }

    @Test
    fun pinnedFloatToOwnSectionAndNotDuplicated() {
        val sessions = listOf(
            meta("pinned-old", todayStart - 5 * dayMs, pinned = true),
            meta("today", todayStart + 1_000),
        )
        val sections = SessionGrouping.group(sessions, now)
        assertEquals(SessionGrouping.Bucket.PINNED, sections.first().bucket)
        assertEquals("pinned-old", sections.first().sessions.single().id)
        // The pinned one must not also appear in a date bucket.
        val nonPinned = sections.drop(1).flatMap { it.sessions }.map { it.id }
        assertEquals(listOf("today"), nonPinned)
    }

    @Test
    fun emptySectionsOmittedAndOrderIsRecencyDescending() {
        val sessions = listOf(
            meta("older-today", todayStart + 1_000),
            meta("newer-today", todayStart + 9_000),
        )
        val sections = SessionGrouping.group(sessions, now)
        assertEquals(1, sections.size)
        assertEquals(SessionGrouping.Bucket.TODAY, sections.single().bucket)
        assertEquals(listOf("newer-today", "older-today"), sections.single().sessions.map { it.id })
    }
}
