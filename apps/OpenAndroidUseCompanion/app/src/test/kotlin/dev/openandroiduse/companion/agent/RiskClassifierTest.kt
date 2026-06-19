package dev.openandroiduse.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskClassifierTest {

    @Test
    fun flagsIrreversibleOrExternalActions() {
        assertTrue(RiskClassifier.isHighRisk("click", "Tap 'Send'"))
        assertTrue(RiskClassifier.isHighRisk("click", "Tap 'Pay $42.00'"))
        assertTrue(RiskClassifier.isHighRisk("click", "Tap 'Delete account'"))
        assertTrue(RiskClassifier.isHighRisk("click", "Tap 'Post'"))
        assertTrue(RiskClassifier.isHighRisk("perform_secondary_action", "Long-press 'Remove'"))
        assertTrue(RiskClassifier.isHighRisk("click", "Tap 'Place order'"))
    }

    @Test
    fun doesNotFlagRoutineNavigation() {
        assertFalse(RiskClassifier.isHighRisk("click", "Tap 'Back'"))
        assertFalse(RiskClassifier.isHighRisk("click", "Tap 'Next'"))
        assertFalse(RiskClassifier.isHighRisk("click", "Tap 'Open Settings'"))
        assertFalse(RiskClassifier.isHighRisk("click", "Tap 'Search'"))
        // word-boundaried: 'sender' / 'deleted items' headers don't trip a tap on them
        assertFalse(RiskClassifier.isHighRisk("click", "Tap 'Sender details'"))
    }

    @Test
    fun onlyTriggerToolsCount() {
        // Scrolling/typing/dragging onto a risky label is not the trigger; the tap is.
        assertFalse(RiskClassifier.isHighRisk("scroll", "Scroll to 'Send'"))
        assertFalse(RiskClassifier.isHighRisk("type_text", "Type 'send me money'"))
        assertFalse(RiskClassifier.isHighRisk("get_app_state", "send"))
    }
}
