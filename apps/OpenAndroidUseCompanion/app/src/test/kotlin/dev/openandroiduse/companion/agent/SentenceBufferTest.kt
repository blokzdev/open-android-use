package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceBufferTest {

    @Test
    fun releasesSentencesAsTheyComplete() {
        val buffer = SentenceBuffer()
        assertTrue(buffer.add("I'll open Set").isEmpty())
        assertEquals(listOf("I'll open Settings now."), buffer.add("tings now. Then I"))
        assertEquals(listOf("Then I tap About phone."), buffer.add(" tap About phone."))
    }

    @Test
    fun handlesMultipleSentencesInOneDelta() {
        val buffer = SentenceBuffer()
        assertEquals(
            listOf("One.", "Two!", "Three?"),
            buffer.add("One. Two! Three?"),
        )
    }

    @Test
    fun newlinesEndSentences() {
        val buffer = SentenceBuffer()
        assertEquals(listOf("First line"), buffer.add("First line\nsecond"))
        assertEquals("second", buffer.flush())
    }

    @Test
    fun flushReturnsRemainderOnceThenNull() {
        val buffer = SentenceBuffer()
        buffer.add("unterminated text")
        assertEquals("unterminated text", buffer.flush())
        assertNull(buffer.flush())
    }
}
