package dev.openandroiduse.companion.agent

import dev.openandroiduse.companion.DeviceTier
import dev.openandroiduse.companion.agent.llm.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyModeTest {

    @Test
    fun lowTierIsUnsupportedRegardlessOfModel() {
        assertEquals(LocalOnlyAvailability.UNSUPPORTED, localOnlyAvailability(DeviceTier.LOW, modelReady = true))
        assertEquals(LocalOnlyAvailability.UNSUPPORTED, localOnlyAvailability(DeviceTier.LOW, modelReady = false))
    }

    @Test
    fun capableButNoModelNeedsDownload() {
        assertEquals(LocalOnlyAvailability.NEEDS_MODEL, localOnlyAvailability(DeviceTier.MEDIUM, modelReady = false))
        assertEquals(LocalOnlyAvailability.NEEDS_MODEL, localOnlyAvailability(DeviceTier.HIGH, modelReady = false))
    }

    @Test
    fun capableWithModelIsReady() {
        assertEquals(LocalOnlyAvailability.READY, localOnlyAvailability(DeviceTier.MEDIUM, modelReady = true))
        assertEquals(LocalOnlyAvailability.READY, localOnlyAvailability(DeviceTier.HIGH, modelReady = true))
    }

    @Test
    fun localOnlyForcesOnDeviceProvider() {
        assertEquals(LlmProvider.ON_DEVICE, effectiveProvider(localOnly = true, selected = LlmProvider.ANTHROPIC))
        assertEquals(LlmProvider.ON_DEVICE, effectiveProvider(localOnly = true, selected = LlmProvider.GEMINI))
        assertEquals(LlmProvider.ON_DEVICE, effectiveProvider(localOnly = true, selected = LlmProvider.ON_DEVICE))
    }

    @Test
    fun offUsesSelectedProvider() {
        assertEquals(LlmProvider.ANTHROPIC, effectiveProvider(localOnly = false, selected = LlmProvider.ANTHROPIC))
        assertEquals(LlmProvider.GEMINI, effectiveProvider(localOnly = false, selected = LlmProvider.GEMINI))
    }
}
