package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionCodecTest {

    private fun sample(): SessionPayload = SessionPayload(
        id = "abc-123",
        title = "Turn Bluetooth on",
        createdAt = 1000L,
        updatedAt = 2000L,
        archived = false,
        transcript = listOf(
            StoredMessage(AgentController.KIND_USER, "Turn Bluetooth on"),
            StoredMessage(AgentController.KIND_TOOL, "▸ click Settings"),
            StoredMessage(AgentController.KIND_ASSISTANT, "Done."),
        ),
    )

    @Test
    fun roundTripsMetadataAndTranscript() {
        val back = SessionCodec.decode(SessionCodec.encode(sample()))
        assertEquals("abc-123", back.id)
        assertEquals("Turn Bluetooth on", back.title)
        assertEquals(1000L, back.createdAt)
        assertEquals(2000L, back.updatedAt)
        assertFalse(back.archived)
        assertEquals(3, back.transcript.size)
        assertEquals(AgentController.KIND_TOOL, back.transcript[1].kind)
        assertEquals("Done.", back.transcript[2].text)
    }

    @Test
    fun decodeMetaMatchesFullDecode() {
        val json = SessionCodec.encode(sample())
        val meta = SessionCodec.decodeMeta(json)
        assertEquals("abc-123", meta.id)
        assertEquals("Turn Bluetooth on", meta.title)
        assertEquals(2000L, meta.updatedAt)
        assertEquals(false, meta.archived)
    }

    @Test
    fun toleratesMissingOptionalFields() {
        val minimal = """{"id":"x","transcript":[]}"""
        val back = SessionCodec.decode(minimal)
        assertEquals("x", back.id)
        assertEquals(SessionTitle.FALLBACK, back.title)
        assertEquals(0, back.transcript.size)
    }
}
