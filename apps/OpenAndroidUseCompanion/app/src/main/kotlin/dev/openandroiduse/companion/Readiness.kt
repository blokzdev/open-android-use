package dev.openandroiduse.companion

/**
 * The agent has two prerequisites: the accessibility service must be running (so
 * it can see and act on the screen) and an API key must be set (so the model can
 * run). This small pure helper lets the home screen and the chat surface the same
 * "what's missing" guidance, and is unit-testable without Android.
 */
enum class Readiness { READY, NEEDS_ACCESSIBILITY, NEEDS_KEY, NEEDS_BOTH }

fun readiness(accessibilityOn: Boolean, hasKey: Boolean): Readiness = when {
    accessibilityOn && hasKey -> Readiness.READY
    !accessibilityOn && !hasKey -> Readiness.NEEDS_BOTH
    !accessibilityOn -> Readiness.NEEDS_ACCESSIBILITY
    else -> Readiness.NEEDS_KEY
}
