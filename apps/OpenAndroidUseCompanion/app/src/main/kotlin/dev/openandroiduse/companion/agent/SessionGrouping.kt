package dev.openandroiduse.companion.agent

import java.util.Calendar

/**
 * Pure ordering + date bucketing for the History list (Phase 4.7c): pinned conversations float
 * to the top, the rest fall into Today / Yesterday / Earlier by their `updatedAt`. No Android or
 * SDK types, so it unit-tests on the JVM (calendar-day boundaries use the JVM default time zone,
 * matching how the user reads "today"). `now` is injected for determinism.
 */
object SessionGrouping {

    enum class Bucket { PINNED, TODAY, YESTERDAY, EARLIER }

    data class Section(val bucket: Bucket, val sessions: List<SessionMeta>)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Start of the calendar day containing [now], in the JVM default time zone. */
    fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Groups [sessions] into ordered, non-empty sections. Pinned sessions appear only in the
     * PINNED section (never duplicated in a date bucket); within every section the order is most
     * recently updated first.
     */
    fun group(sessions: List<SessionMeta>, now: Long): List<Section> {
        val sorted = sessions.sortedByDescending { it.updatedAt }
        val pinned = sorted.filter { it.pinned }
        val rest = sorted.filterNot { it.pinned }

        val todayStart = startOfDay(now)
        val yesterdayStart = todayStart - DAY_MS

        val today = rest.filter { it.updatedAt >= todayStart }
        val yesterday = rest.filter { it.updatedAt in yesterdayStart until todayStart }
        val earlier = rest.filter { it.updatedAt < yesterdayStart }

        return buildList {
            if (pinned.isNotEmpty()) add(Section(Bucket.PINNED, pinned))
            if (today.isNotEmpty()) add(Section(Bucket.TODAY, today))
            if (yesterday.isNotEmpty()) add(Section(Bucket.YESTERDAY, yesterday))
            if (earlier.isNotEmpty()) add(Section(Bucket.EARLIER, earlier))
        }
    }
}
