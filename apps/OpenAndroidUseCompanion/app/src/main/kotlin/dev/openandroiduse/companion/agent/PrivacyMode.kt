package dev.openandroiduse.companion.agent

import dev.openandroiduse.companion.DeviceTier
import dev.openandroiduse.companion.agent.llm.LlmProvider

/**
 * Privacy / Local-Only Mode (Phase 5.7): a single umbrella that forces the
 * on-device (Gemma) provider so **nothing about the user, device, or screen
 * leaves the device** — no cloud provider is ever contacted. It is the real
 * guarantee behind `docs/SECURITY.md`'s "local-only" claim.
 *
 * The mode is tier-gated (a `DeviceTier.LOW` device can't run the on-device
 * model, so the mode isn't offered) and readiness-aware (a capable device that
 * hasn't downloaded the model yet must opt into the one-time download). These
 * two pure functions are the testable core; the UI and [AgentController] consume
 * them so the enforcement (a single chokepoint) stays trivial to verify.
 */
enum class LocalOnlyAvailability {
    /** Device can't run the on-device model — the toggle is shown disabled. */
    UNSUPPORTED,

    /** Device is capable but the model isn't downloaded — enabling requires a download. */
    NEEDS_MODEL,

    /** Device is capable and the model is present — enabling takes effect immediately. */
    READY,
}

/** Whether (and how) Local-Only Mode can be enabled, from device tier + model presence. */
fun localOnlyAvailability(tier: DeviceTier, modelReady: Boolean): LocalOnlyAvailability = when {
    tier == DeviceTier.LOW -> LocalOnlyAvailability.UNSUPPORTED
    !modelReady -> LocalOnlyAvailability.NEEDS_MODEL
    else -> LocalOnlyAvailability.READY
}

/**
 * The provider the agent loop must actually use. When Local-Only Mode is on, this
 * is always [LlmProvider.ON_DEVICE] regardless of the stored selection — the
 * single chokepoint that makes "no cloud egress" structural rather than advisory.
 */
fun effectiveProvider(localOnly: Boolean, selected: LlmProvider): LlmProvider =
    if (localOnly) LlmProvider.ON_DEVICE else selected
