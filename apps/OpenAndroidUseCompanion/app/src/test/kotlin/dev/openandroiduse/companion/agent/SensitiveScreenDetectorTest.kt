package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveScreenDetectorTest {

    private fun snap(elements: List<ElementRecord>) = AppSnapshot(
        appName = "Login",
        packageName = "com.example.app",
        elements = elements,
    )

    @Test
    fun nullSnapshotIsNotSensitive() {
        assertFalse(SensitiveScreenDetector.isSensitive(null))
    }

    @Test
    fun screenWithoutCredentialFieldsIsNotSensitive() {
        val s = snap(
            listOf(
                ElementRecord(index = 1, name = "Username", actions = listOf("set_value")),
                ElementRecord(index = 2, name = "Sign in", actions = listOf("click")),
            ),
        )
        assertFalse(SensitiveScreenDetector.isSensitive(s))
    }

    @Test
    fun passwordFieldIsSensitive() {
        val s = snap(
            listOf(
                ElementRecord(index = 1, name = "Username", actions = listOf("set_value")),
                ElementRecord(index = 2, name = "Password", actions = listOf("set_value"), password = true),
            ),
        )
        assertTrue(SensitiveScreenDetector.isSensitive(s))
    }

    @Test
    fun maskedPasswordFieldWithNoVisibleValueIsStillSensitive() {
        // The framework masks the value (empty/dots), but isPassword is authoritative.
        val s = snap(
            listOf(ElementRecord(index = 1, name = "", value = "", actions = listOf("set_value"), password = true)),
        )
        assertTrue(SensitiveScreenDetector.isSensitive(s))
    }

    @Test
    fun creditCardFieldIsSensitive() {
        val s = snap(
            listOf(
                ElementRecord(index = 1, name = "Cardholder", actions = listOf("set_value")),
                ElementRecord(index = 2, name = "Card number", actions = listOf("set_value"), creditCard = true),
            ),
        )
        assertTrue(SensitiveScreenDetector.isSensitive(s))
    }

    @Test
    fun bothPasswordAndCreditCardIsSensitive() {
        val s = snap(
            listOf(ElementRecord(index = 1, name = "PIN", actions = listOf("set_value"), password = true, creditCard = true)),
        )
        assertTrue(SensitiveScreenDetector.isSensitive(s))
    }

    @Test
    fun classifyDistinguishesPasswordPaymentAndBoth() {
        assertNull(
            SensitiveScreenDetector.classify(
                listOf(ElementRecord(index = 1, name = "Search", actions = listOf("set_value"))),
            ),
        )
        assertEquals(
            SensitiveScreenDetector.Kind.PASSWORD,
            SensitiveScreenDetector.classify(listOf(ElementRecord(index = 1, password = true))),
        )
        assertEquals(
            SensitiveScreenDetector.Kind.PAYMENT,
            SensitiveScreenDetector.classify(listOf(ElementRecord(index = 1, creditCard = true))),
        )
        assertEquals(
            SensitiveScreenDetector.Kind.BOTH,
            SensitiveScreenDetector.classify(
                listOf(
                    ElementRecord(index = 1, password = true),
                    ElementRecord(index = 2, creditCard = true),
                ),
            ),
        )
    }
}
