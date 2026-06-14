package dev.openandroiduse.companion.agent

/**
 * A conservative, dependency-free Markdown subset for rendering assistant
 * answers: bold (**), italic (* or _), inline code (`), links [text](url),
 * bullet / numbered lists, and pipe tables. Returns a framework-agnostic model
 * so the parser is pure-JVM testable; the Compose layer converts spans to an
 * AnnotatedString (links via LinkAnnotation) and renders tables.
 */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class BulletList(val items: List<List<MdSpan>>) : MdBlock
    data class NumberedList(val items: List<List<MdSpan>>) : MdBlock
    /** A pipe table: a header row plus zero or more body rows, each a list of cells. */
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
}

object ChatMarkdown {

    private val BULLET = Regex("^[-*]\\s+(.*)")
    private val NUMBERED = Regex("^\\d+[.)]\\s+(.*)")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
    private val SEPARATOR_CELL = Regex("^:?-{1,}:?$")

    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var para = mutableListOf<String>()
        var bullets = mutableListOf<String>()
        var numbered = mutableListOf<String>()
        var table = mutableListOf<String>()

        fun flushPara() {
            if (para.isNotEmpty()) {
                blocks.add(MdBlock.Paragraph(parseInline(para.joinToString(" ").trim())))
                para = mutableListOf()
            }
        }
        fun flushBullets() {
            if (bullets.isNotEmpty()) {
                blocks.add(MdBlock.BulletList(bullets.map { parseInline(it) }))
                bullets = mutableListOf()
            }
        }
        fun flushNumbered() {
            if (numbered.isNotEmpty()) {
                blocks.add(MdBlock.NumberedList(numbered.map { parseInline(it) }))
                numbered = mutableListOf()
            }
        }
        fun flushTable() {
            if (table.isEmpty()) return
            // A table needs a header, a `| --- | --- |` separator, then rows.
            if (table.size >= 2 && isSeparatorRow(table[1])) {
                val header = parseRow(table[0])
                val rows = table.drop(2).map { parseRow(it) }
                blocks.add(MdBlock.Table(header, rows))
            } else {
                // Not a real table — fall back to plain paragraphs.
                table.forEach { blocks.add(MdBlock.Paragraph(parseInline(it))) }
            }
            table = mutableListOf()
        }
        fun flushAll() { flushPara(); flushBullets(); flushNumbered(); flushTable() }

        for (line in src.split("\n")) {
            val l = line.trim()
            val bullet = BULLET.find(l)
            val number = NUMBERED.find(l)
            when {
                l.isEmpty() -> flushAll()
                l.startsWith("|") -> { flushPara(); flushBullets(); flushNumbered(); table.add(l) }
                bullet != null -> { flushPara(); flushNumbered(); flushTable(); bullets.add(bullet.groupValues[1]) }
                number != null -> { flushPara(); flushBullets(); flushTable(); numbered.add(number.groupValues[1]) }
                else -> { flushBullets(); flushNumbered(); flushTable(); para.add(l) }
            }
        }
        flushAll()
        return blocks
    }

    private fun isSeparatorRow(line: String): Boolean {
        val cells = splitCells(line)
        return cells.isNotEmpty() && cells.all { SEPARATOR_CELL.matches(it) }
    }

    private fun splitCells(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    private fun parseRow(line: String): List<List<MdSpan>> = splitCells(line).map { parseInline(it) }

    fun parseInline(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val sb = StringBuilder()
        var bold = false
        var italic = false
        var i = 0
        fun flush() {
            if (sb.isNotEmpty()) {
                spans.add(MdSpan(sb.toString(), bold, italic, false))
                sb.clear()
            }
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        spans.add(MdSpan(text.substring(i + 1, end), bold, italic, true))
                        i = end + 1
                    } else {
                        sb.append(c); i++
                    }
                }
                c == '[' -> {
                    val m = LINK.matchAt(text, i)
                    if (m != null) {
                        flush()
                        spans.add(MdSpan(m.groupValues[1], bold, italic, url = m.groupValues[2]))
                        i = m.range.last + 1
                    } else {
                        sb.append(c); i++
                    }
                }
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> { flush(); bold = !bold; i += 2 }
                c == '*' || c == '_' -> { flush(); italic = !italic; i++ }
                else -> { sb.append(c); i++ }
            }
        }
        flush()
        return spans.ifEmpty { listOf(MdSpan(text)) }
    }
}
