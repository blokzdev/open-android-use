package dev.openandroiduse.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6.5c-3 security-critical: a per-app grant relaxes only the *screen-level* gate, so
 * `targetsSecretField` must still fire for an action aimed at an actual secret field. If this
 * regresses, a trusted app could be coaxed into typing into a password/card field.
 */
class SecretTargetingTest {

    private val snapshot = AppSnapshot(
        appName = "Shop",
        packageName = "com.shop",
        elements = listOf(
            ElementRecord(index = 1, name = "Search", controlType = "EditText", actions = listOf("set_value")),
            ElementRecord(index = 2, name = "[redacted]", controlType = "EditText", actions = listOf("set_value"), creditCard = true),
            ElementRecord(index = 3, name = "[redacted]", controlType = "EditText", actions = listOf("set_value"), password = true, focused = true),
        ),
    )

    @Test
    fun setValueOnASecretElementTargetsSecret() {
        assertTrue(SensitiveScreenDetector.targetsSecretField("set_value", "2", snapshot))
        assertTrue(SensitiveScreenDetector.targetsSecretField("click", "3", snapshot))
    }

    @Test
    fun setValueOnANonSecretElementDoesNot() {
        assertFalse(SensitiveScreenDetector.targetsSecretField("set_value", "1", snapshot))
    }

    @Test
    fun typeTextTargetsTheFocusedSecretField() {
        assertTrue(SensitiveScreenDetector.targetsSecretField("type_text", null, snapshot))
    }

    @Test
    fun typeTextOnANonSecretFocusedFieldDoesNot() {
        val s = snapshot.copy(
            elements = listOf(ElementRecord(index = 1, name = "Search", focused = true, actions = listOf("set_value"))),
        )
        assertFalse(SensitiveScreenDetector.targetsSecretField("type_text", null, s))
    }

    @Test
    fun readsAndUnknownToolsNeverTargetSecret() {
        assertFalse(SensitiveScreenDetector.targetsSecretField("get_app_state", "2", snapshot))
        assertFalse(SensitiveScreenDetector.targetsSecretField("scroll", "2", snapshot))
        assertFalse(SensitiveScreenDetector.targetsSecretField("set_value", null, snapshot))
    }
}
