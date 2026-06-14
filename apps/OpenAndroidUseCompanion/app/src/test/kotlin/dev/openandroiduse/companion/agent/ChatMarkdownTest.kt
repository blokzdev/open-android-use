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

    @Test fun parsesLink() {
        val spans = ChatMarkdown.parseInline("see [docs](https://example.com/x) now")
        val link = spans.single { it.url != null }
        assertEquals("docs", link.text)
        assertEquals("https://example.com/x", link.url)
        // Surrounding text is preserved.
        assertEquals("see docs now", spans.joinToString("") { it.text })
    }

    @Test fun unmatchedBracketIsLiteral() {
        val spans = ChatMarkdown.parseInline("an [open bracket")
        assertEquals("an [open bracket", spans.joinToString("") { it.text })
        assertTrue(spans.none { it.url != null })
    }

    @Test fun parsesPipeTable() {
        val blocks = ChatMarkdown.parse("| Name | Qty |\n| --- | --- |\n| Apples | 3 |\n| Pears | 5 |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Name", "Qty"), table.header.map { spans -> spans.joinToString("") { it.text } })
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Apples", "3"), table.rows[0].map { spans -> spans.joinToString("") { it.text } })
    }

    @Test fun pipeLinesWithoutSeparatorAreNotATable() {
        val blocks = ChatMarkdown.parse("| just some piped text |")
        assertTrue(blocks.all { it is MdBlock.Paragraph })
    }

    @Test fun linkInsideTableCell() {
        val blocks = ChatMarkdown.parse("| Site |\n| --- |\n| [home](https://h.co) |")
        val table = blocks.single() as MdBlock.Table
        val cell = table.rows[0][0]
        assertEquals("https://h.co", cell.single { it.url != null }.url)
    }
}
