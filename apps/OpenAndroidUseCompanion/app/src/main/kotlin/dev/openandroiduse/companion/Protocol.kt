package dev.openandroiduse.companion

/**
 * Companion wire protocol, consumed by the Go bridge (apps/OpenAndroidUse).
 * Bump VERSION on breaking changes; the bridge refuses unknown versions.
 * Spec: docs/design-docs/on-device-companion.md.
 */
object Protocol {
    const val VERSION = 1
}
