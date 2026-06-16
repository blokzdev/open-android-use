package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustCodecTest {

    @Test
    fun grantsRoundTrip() {
        val grants = listOf(
            TrustGrant("com.a", GrantScope.PERSISTENT, grantedAt = 1L, lastUsedAt = 2L),
            TrustGrant("com.b", GrantScope.PERSISTENT, grantedAt = 3L, lastUsedAt = 4L),
        )
        assertEquals(grants, TrustCodec.decodeGrants(TrustCodec.encodeGrants(grants)))
    }

    @Test
    fun auditRoundTrips() {
        val entries = listOf(
            AuditEntry("com.a", "click", "Tap 'Send'", at = 10L, grantScope = GrantScope.SESSION),
            AuditEntry("com.a", "scroll", "Scroll down", at = 11L, grantScope = GrantScope.PERSISTENT),
        )
        assertEquals(entries, TrustCodec.decodeAudit(TrustCodec.encodeAudit(entries)))
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(TrustCodec.decodeGrants("not json").isEmpty())
        assertTrue(TrustCodec.decodeAudit("{}").isEmpty())
    }

    @Test
    fun cappedKeepsTheMostRecent() {
        val entries = (1..10).map { AuditEntry("com.a", "click", "n$it", at = it.toLong(), grantScope = GrantScope.ONCE) }
        val capped = TrustCodec.capped(entries, max = 3)
        assertEquals(listOf("n8", "n9", "n10"), capped.map { it.summary })
        assertEquals(entries, TrustCodec.capped(entries, max = 10))
    }
}
