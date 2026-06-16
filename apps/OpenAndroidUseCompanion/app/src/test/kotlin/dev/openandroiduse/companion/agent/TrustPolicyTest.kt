package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustPolicyTest {

    private val day = 24L * 60L * 60L * 1000L
    private fun grant(lastUsedAt: Long) =
        TrustGrant("com.app", GrantScope.PERSISTENT, grantedAt = 0L, lastUsedAt = lastUsedAt)

    @Test
    fun activeUntilTheDecayWindowElapses() {
        val g = grant(lastUsedAt = 0L)
        assertTrue(TrustPolicy.isActive(g, now = 29 * day))
        assertFalse(TrustPolicy.isActive(g, now = 30 * day))
        assertTrue(TrustPolicy.decayed(g, now = 30 * day))
    }

    @Test
    fun touchResetsTheDecayClock() {
        val g = grant(lastUsedAt = 0L)
        val used = TrustPolicy.touch(g, now = 25 * day)
        assertEquals(25 * day, used.lastUsedAt)
        // Now decay is measured from the new lastUsedAt, not the original.
        assertTrue(TrustPolicy.isActive(used, now = 50 * day))
        assertFalse(TrustPolicy.isActive(used, now = 55 * day))
    }

    @Test
    fun daysUntilDecayCountsDownAndFloorsAtZero() {
        val g = grant(lastUsedAt = 0L)
        assertEquals(TrustPolicy.DECAY_DAYS, TrustPolicy.daysUntilDecay(g, now = 0L))
        assertEquals(1L, TrustPolicy.daysUntilDecay(g, now = 29 * day + 1))
        assertEquals(0L, TrustPolicy.daysUntilDecay(g, now = 30 * day))
        assertEquals(0L, TrustPolicy.daysUntilDecay(g, now = 99 * day))
    }
}
