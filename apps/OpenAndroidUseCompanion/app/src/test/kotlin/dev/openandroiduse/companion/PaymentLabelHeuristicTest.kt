package dev.openandroiduse.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6.5b: the payment-field heuristic must catch real card-entry labels while
 * staying off ordinary screens (no bare "card" / "expiry" matches). Pure string in,
 * boolean out — the Android-facing wrapper just feeds it the node's labels.
 */
class PaymentLabelHeuristicTest {

    private fun match(s: String) = SnapshotBuilder.looksLikePaymentLabel(s)

    @Test
    fun matchesCommonPaymentLabels() {
        assertTrue(match("Card number"))
        assertTrue(match("Credit card"))
        assertTrue(match("Debit card"))
        assertTrue(match("CVV"))
        assertTrue(match("CVC"))
        assertTrue(match("Security code"))
        assertTrue(match("Card verification value"))
    }

    @Test
    fun matchesResourceIdAndCamelCaseForms() {
        assertTrue(match("com.shop:id/credit_card_number"))
        assertTrue(match("cardNumberInput"))
        assertTrue(match("cvvCode"))
    }

    @Test
    fun doesNotMatchOrdinaryLabels() {
        assertFalse(match("Discard"))
        assertFalse(match("CardView"))
        assertFalse(match("Dashboard"))
        assertFalse(match("Add to cart"))
        assertFalse(match("Username"))
        assertFalse(match(""))
    }
}
