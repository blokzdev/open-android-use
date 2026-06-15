package dev.openandroiduse.companion.agent

/**
 * Adaptive perception (Phase 5.6): whether a turn sends the screenshot to the
 * model (vision) or acts from the accessibility-tree text only.
 *
 * All providers (Claude, Gemini, on-device Gemma) are vision-capable, so this is
 * a per-provider user preference (`AgentSettings.sendScreenshots`), not a
 * capability gate. Text-only is fully functional — the a11y tree carries every
 * interactable element's index, label, and bounds, which drives all 9 tools —
 * and is the faster/cheaper/more-private choice; vision is the capability
 * fallback for accessibility-poor surfaces (custom Canvas, WebViews, image
 * content) and visual verification.
 */
enum class PerceptionMode { VISION, TEXT_ONLY }

fun perceptionMode(sendScreenshots: Boolean): PerceptionMode =
    if (sendScreenshots) PerceptionMode.VISION else PerceptionMode.TEXT_ONLY
