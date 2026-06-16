package dev.openandroiduse.companion.agent

import java.io.IOException

/**
 * Phase 6.3a: decides whether a failed model-stream turn is worth retrying and how
 * long to wait. Pure + deterministic (no jitter) so it is unit-tested; the loop in
 * [AgentController] owns the actual sleeping, the attempt budget, and the
 * "clean attempt" guard (never retry once partial output has streamed).
 *
 * Transient = transport/server hiccups a retry can paper over (rate-limit, 5xx,
 * timeouts, connection drops, partial-stream parse failures). Permanent = client
 * errors a retry can't fix (auth, bad request); those fail fast. Unknown errors are
 * treated as permanent — we only retry when we have a positive reason to.
 */
object RetryPolicy {

    const val MAX_ATTEMPTS = 3

    /** Whether [error] (anywhere in its cause chain) looks worth retrying. */
    fun isTransient(error: Throwable): Boolean {
        val haystack = causeChain(error)
        if (PERMANENT_MARKERS.any { it in haystack }) return false
        if (anyCauseIs<IOException>(error)) return true
        return TRANSIENT_MARKERS.any { it in haystack }
    }

    /** Backoff before the next attempt: 1s, 2s, 4s… capped. [attempt] is 1-based. */
    fun backoffMs(attempt: Int): Long {
        val exp = (attempt.coerceAtLeast(1) - 1).coerceAtMost(16)
        return (BASE_BACKOFF_MS shl exp).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun causeChain(error: Throwable): String =
        generateSequence<Throwable>(error) { it.cause }
            .take(MAX_CHAIN)
            .joinToString(" ") { "${it.javaClass.simpleName} ${it.message ?: ""}" }
            .lowercase()

    private inline fun <reified T : Throwable> anyCauseIs(error: Throwable): Boolean =
        generateSequence<Throwable>(error) { it.cause }.take(MAX_CHAIN).any { it is T }

    private const val BASE_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 8_000L
    private const val MAX_CHAIN = 8

    private val PERMANENT_MARKERS = listOf(
        "authentication", "unauthorized", "401", "permissiondenied", "permission denied",
        "forbidden", "403", "badrequest", "bad request", "invalidrequest", "invalid request",
        "400", "422", "unprocessable", "not found", "notfound", "404",
    )
    private val TRANSIENT_MARKERS = listOf(
        "ratelimit", "rate limit", "429", "overloaded", "500", "502", "503", "504",
        "internalserver", "serviceunavailable", "service unavailable", "unavailable",
        "timeout", "timed out", "connect", "socket", "unknownhost", "reset", "stream",
    )
}
