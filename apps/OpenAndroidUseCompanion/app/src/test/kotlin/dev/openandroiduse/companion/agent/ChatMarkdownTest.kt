package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {

    @Test fun plainParagraph() {
        val blocks = ChatMarkdown.parse("hello world")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals(listOf(MdSpan("hello world")), p.spans)
    }

    @Test fun boldAndItalicAndCode() {
        val spans = ChatMarkdown.parseInline("a **b** c *d* `e`")
        assertTrue(spans.contains(MdSpan("b", bold = true)))
        assertTrue(spans.contains(MdSpan("d", italic = true)))
        assertTrue(spans.contains(MdSpan("e", code = true)))
    }

    @Test fun bulletList() {
        val blocks = ChatMarkdown.parse("- one\n- two")
        val list = blocks.single() as MdBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals("one", list.items[0].joinToString("") { it.text })
        assertEquals("two", list.items[1].joinToString("") { it.text })
    }

    @Test fun numberedList() {
        val blocks = ChatMarkdown.parse("1. first\n2. second")
        val list = blocks.single() as MdBlock.NumberedList
        assertEquals(listOf("first", "second"), list.items.map { spans -> spans.joinToString("") { it.text } })
    }

    @Test fun italicStarIsNotMistakenForBullet() {
        // "*word*" has no space after the marker, so it's a paragraph with italics.
        val blocks = ChatMarkdown.parse("*word*")
        val p = blocks.single() as MdBlock.Paragraph
        assertTrue(p.spans.contains(MdSpan("word", italic = true)))
    }

    @Test fun blankLineSeparatesParagraphs() {
        val blocks = ChatMarkdown.parse("one\n\ntwo")
        assertEquals(2, blocks.size)
    }
}
