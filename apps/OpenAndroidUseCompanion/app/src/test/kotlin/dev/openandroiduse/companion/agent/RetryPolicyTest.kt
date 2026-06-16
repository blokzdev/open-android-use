package dev.openandroiduse.companion.agent

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun rateLimitIsTransient() {
        assertTrue(RetryPolicy.isTransient(RuntimeException("HTTP 429 Too Many Requests")))
        assertTrue(RetryPolicy.isTransient(RuntimeException("rate limit exceeded")))
        assertTrue(RetryPolicy.isTransient(RuntimeException("overloaded_error")))
    }

    @Test
    fun serverAndNetworkErrorsAreTransient() {
        assertTrue(RetryPolicy.isTransient(RuntimeException("503 Service Unavailable")))
        assertTrue(RetryPolicy.isTransient(IOException("Connection reset by peer")))
        assertTrue(RetryPolicy.isTransient(RuntimeException("read timed out")))
    }

    @Test
    fun ioExceptionIsTransientEvenWithoutMarkers() {
        assertTrue(RetryPolicy.isTransient(IOException()))
    }

    @Test
    fun transientCauseNestedUnderWrapperIsFound() {
        val wrapped = RuntimeException("Request failed", IOException("timeout"))
        assertTrue(RetryPolicy.isTransient(wrapped))
    }

    @Test
    fun authAndBadRequestAreNotTransient() {
        assertFalse(RetryPolicy.isTransient(RuntimeException("401 Unauthorized")))
        assertFalse(RetryPolicy.isTransient(RuntimeException("400 invalid request")))
        assertFalse(RetryPolicy.isTransient(RuntimeException("authentication_error")))
    }

    @Test
    fun permanentMarkerOverridesTransientInChain() {
        // A 503-looking wrapper around a real auth failure must fail fast.
        val wrapped = RuntimeException("503 service unavailable", RuntimeException("authentication failed"))
        assertFalse(RetryPolicy.isTransient(wrapped))
    }

    @Test
    fun unknownErrorIsNotTransient() {
        assertFalse(RetryPolicy.isTransient(RuntimeException("something unexpected went wrong")))
    }

    @Test
    fun backoffIsExponentialAndCapped() {
        assertEquals(1_000L, RetryPolicy.backoffMs(1))
        assertEquals(2_000L, RetryPolicy.backoffMs(2))
        assertEquals(4_000L, RetryPolicy.backoffMs(3))
        assertEquals(8_000L, RetryPolicy.backoffMs(4))
        assertEquals(8_000L, RetryPolicy.backoffMs(10))
    }
}
