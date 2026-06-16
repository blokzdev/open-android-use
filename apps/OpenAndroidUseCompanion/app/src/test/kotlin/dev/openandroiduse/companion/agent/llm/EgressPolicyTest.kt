package dev.openandroiduse.companion.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EgressPolicyTest {

    @Test
    fun nullPassesThrough() {
        assertNull(EgressPolicy.loopbackOrNull(null))
    }

    @Test
    fun loopbackHostsAreAccepted() {
        assertEquals("http://127.0.0.1:8080", EgressPolicy.loopbackOrNull("http://127.0.0.1:8080"))
        assertEquals("http://localhost:5000/v1", EgressPolicy.loopbackOrNull("http://localhost:5000/v1"))
    }

    @Test
    fun remoteAndNonHttpAreRejected() {
        // No arbitrary egress redirect: remote http, https (even loopback), and
        // look-alike hosts must all be refused.
        assertNull(EgressPolicy.loopbackOrNull("http://example.com"))
        assertNull(EgressPolicy.loopbackOrNull("http://api.anthropic.com"))
        assertNull(EgressPolicy.loopbackOrNull("https://127.0.0.1:8080"))
        assertNull(EgressPolicy.loopbackOrNull("http://127.0.0.1.evil.com"))
        assertNull(EgressPolicy.loopbackOrNull("http://localhost.evil.com"))
    }
}
