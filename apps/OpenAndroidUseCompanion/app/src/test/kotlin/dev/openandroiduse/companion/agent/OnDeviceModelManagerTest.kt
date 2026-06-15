package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the security-critical, device-free parts of the on-device model manager
 * (Phase 5.5): the integrity hash + verification and the pinned source. The
 * download/WorkManager/path parts need a device and are covered on hardware.
 */
class OnDeviceModelManagerTest {

    private fun tempFileWith(content: String): File =
        File.createTempFile("oau-model-test", ".bin").apply { writeText(content); deleteOnExit() }

    @Test
    fun sha256MatchesKnownVector() {
        // SHA-256("abc") is a well-known test vector.
        val file = tempFileWith("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", OnDeviceModelManager.sha256(file))
    }

    @Test
    fun verifyIntegrityAcceptsMatchingHashAndRejectsOthers() {
        val file = tempFileWith("abc")
        val good = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(OnDeviceModelManager.verifyIntegrity(file, good))
        assertTrue("hash compare is case-insensitive", OnDeviceModelManager.verifyIntegrity(file, good.uppercase()))
        assertFalse(OnDeviceModelManager.verifyIntegrity(file, "0".repeat(64)))
    }

    @Test
    fun pinnedSourceIsWellFormed() {
        assertEquals(64, OnDeviceModelManager.EXPECTED_SHA256.length)
        assertTrue(OnDeviceModelManager.EXPECTED_SHA256.all { it in "0123456789abcdef" })
        assertTrue(OnDeviceModelManager.EXPECTED_BYTES > 2_000_000_000L)
        val url = OnDeviceModelManager.downloadUrl()
        assertTrue(url.startsWith("https://huggingface.co/"))
        assertTrue(url.contains(OnDeviceModelManager.REPO))
        assertTrue(url.endsWith(OnDeviceModelManager.FILE_NAME))
    }
}
