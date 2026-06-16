package dev.openandroiduse.companion.agent

/**
 * Phase 6.5c-3 scoped per-app trust. Default-deny: the agent hands off on a sensitive
 * screen unless the user has granted that app trust. A grant only relaxes the
 * *screen-level* action gate — it never un-redacts a value (L0) and never lets the agent
 * type into an actual secret field (element-level handoff still fires). See the spec
 * `docs/design-docs/agent-security-trust-architecture.md`.
 *
 * Scope tiers, by reversibility (NN/g + NIST 800-207 least-privilege):
 *  - [GrantScope.ONCE]    — this one screen; in-memory, gone after the action.
 *  - [GrantScope.SESSION] — this task; in-memory, cleared on reset/task end.
 *  - [GrantScope.PERSISTENT] — remembered across tasks; the only tier written to disk,
 *    and it **decays** after [TrustPolicy.DECAY_DAYS] of disuse (Android auto-reset
 *    precedent) and is revocable in Privacy.
 */
enum class GrantScope { ONCE, SESSION, PERSISTENT }

/** A single app's trust grant. Only PERSISTENT grants are persisted (see [TrustStore]). */
data class TrustGrant(
    val packageName: String,
    val scope: GrantScope,
    val grantedAt: Long,
    val lastUsedAt: Long,
)

/** One audited agent action on a trusted app's screen (text-only; never a field value). */
data class AuditEntry(
    val packageName: String,
    val tool: String,
    val summary: String,
    val at: Long,
    val grantScope: GrantScope,
)

/** Pure decay / activity math for persistent grants, so it is unit-tested. */
object TrustPolicy {

    /** Days of disuse after which a persistent grant auto-expires. */
    const val DECAY_DAYS = 30L

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** When an unused grant lapses. */
    fun decayDeadline(grant: TrustGrant): Long = grant.lastUsedAt + DECAY_DAYS * DAY_MS

    /** True when the grant has gone unused past the decay window. */
    fun decayed(grant: TrustGrant, now: Long): Boolean = now >= decayDeadline(grant)

    /** True when the grant still counts. */
    fun isActive(grant: TrustGrant, now: Long): Boolean = !decayed(grant, now)

    /** A copy with the last-used time bumped (called when the grant is exercised). */
    fun touch(grant: TrustGrant, now: Long): TrustGrant = grant.copy(lastUsedAt = now)

    /** Whole days until decay (0 once lapsed), for the Privacy "decays in N days" label. */
    fun daysUntilDecay(grant: TrustGrant, now: Long): Long {
        val remaining = decayDeadline(grant) - now
        if (remaining <= 0) return 0
        return (remaining + DAY_MS - 1) / DAY_MS
    }
}
