package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6.5c-1: the pure redaction policy is the single chokepoint, so pin it hard. */
class RedactionTest {

    @Test
    fun shouldRedactOnlyForPasswordOrCreditCard() {
        assertFalse(Redaction.shouldRedact(password = false, creditCard = false))
        assertTrue(Redaction.shouldRedact(password = true, creditCard = false))
        assertTrue(Redaction.shouldRedact(password = false, creditCard = true))
        assertTrue(Redaction.shouldRedact(password = true, creditCard = true))
    }

    @Test
    fun emittedTextOmitsNothingForEmptyValues() {
        assertNull(Redaction.emittedText(password = false, creditCard = false, rawText = null))
        assertNull(Redaction.emittedText(password = true, creditCard = false, rawText = ""))
        assertNull(Redaction.emittedText(password = false, creditCard = true, rawText = null))
    }

    @Test
    fun emittedTextPassesThroughOrdinaryValues() {
        assertEquals(
            "user@example.com",
            Redaction.emittedText(password = false, creditCard = false, rawText = "user@example.com"),
        )
    }

    @Test
    fun emittedTextMasksSecretValues() {
        assertEquals(
            Redaction.PLACEHOLDER,
            Redaction.emittedText(password = true, creditCard = false, rawText = "hunter2"),
        )
        assertEquals(
            Redaction.PLACEHOLDER,
            Redaction.emittedText(password = false, creditCard = true, rawText = "4111 1111 1111 1111"),
        )
    }

    @Test
    fun redactedValueMasksOnlySecrets() {
        assertEquals("hello", Redaction.redactedValue(password = false, creditCard = false, rawValue = "hello"))
        assertEquals(Redaction.PLACEHOLDER, Redaction.redactedValue(password = true, creditCard = false, rawValue = "hunter2"))
        assertEquals(Redaction.PLACEHOLDER, Redaction.redactedValue(password = false, creditCard = true, rawValue = "4111111111111111"))
    }

    @Test
    fun placeholderNeverContainsASecretLikeShape() {
        // Cheap guard so the marker can't accidentally be set to something value-like.
        assertEquals("[redacted]", Redaction.PLACEHOLDER)
        assertTrue(Redaction.SCREENSHOT_WITHHELD_NOTE.contains("withheld"))
    }
}
