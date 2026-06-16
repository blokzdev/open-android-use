package dev.openandroiduse.companion.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises TrustStore + TrustAuditLog against the real filesDir on a device/emulator:
 * the JVM tests cover the pure codec/policy, this covers the file I/O — grant/revoke,
 * lazy decay pruning on read, and the audit ring-buffer — so a persistence regression is
 * caught by emulator-smoke CI. (Phase 6.5c-3a.)
 */
@RunWith(AndroidJUnit4::class)
class TrustStoreInstrumentedTest {

    private val day = 24L * 60L * 60L * 1000L
    private lateinit var store: TrustStore
    private lateinit var audit: TrustAuditLog

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = TrustStore(ctx)
        audit = TrustAuditLog(ctx)
        store.revokeAll()
        audit.clear()
    }

    @Test
    fun grantRevokeAndActiveSet() {
        val now = 10 * day
        store.grant("com.a", now)
        store.grant("com.b", now)
        assertEquals(setOf("com.a", "com.b"), store.activeGrants(now))

        store.revoke("com.a")
        assertEquals(setOf("com.b"), store.activeGrants(now))

        store.revokeAll()
        assertTrue(store.activeGrants(now).isEmpty())
    }

    @Test
    fun decayedGrantsArePrunedOnRead() {
        store.grant("com.stale", now = 0L)
        // 31 days later, unused → decayed and pruned.
        assertFalse(store.activeGrants(now = 31 * day).contains("com.stale"))
        assertTrue(store.list(now = 31 * day).isEmpty())
    }

    @Test
    fun touchKeepsAGrantAlive() {
        store.grant("com.live", now = 0L)
        store.touch("com.live", now = 25 * day)
        assertTrue(store.activeGrants(now = 50 * day).contains("com.live"))
    }

    @Test
    fun auditRecordsAndCaps() {
        repeat(TrustAuditLog.MAX_ENTRIES + 5) { i ->
            audit.record(AuditEntry("com.a", "click", "n$i", at = i.toLong(), grantScope = GrantScope.SESSION))
        }
        val all = audit.all()
        assertEquals(TrustAuditLog.MAX_ENTRIES, all.size)
        // Newest first; the oldest 5 were dropped.
        assertEquals("n${TrustAuditLog.MAX_ENTRIES + 4}", all.first().summary)
        assertEquals(TrustAuditLog.MAX_ENTRIES, audit.forPackage("com.a").size)
        assertTrue(audit.forPackage("com.other").isEmpty())
    }
}
