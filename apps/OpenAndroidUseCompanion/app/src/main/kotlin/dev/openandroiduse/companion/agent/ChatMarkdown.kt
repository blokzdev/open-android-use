package dev.openandroiduse.companion.agent

/**
 * A conservative, dependency-free Markdown subset for rendering assistant
 * answers: bold (**), italic (* or _), inline code (`), and bullet / numbered
 * lists. Returns a framework-agnostic model so the parser is pure-JVM testable;
 * the Compose layer converts spans to an AnnotatedString.
 */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class BulletList(val items: List<List<MdSpan>>) : MdBlock
    data class NumberedList(val items: List<List<MdSpan>>) : MdBlock
}

object ChatMarkdown {

    private val BULLET = Regex("^[-*]\\s+(.*)")
    private val NUMBERED = Regex("^\\d+[.)]\\s+(.*)")

    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var para = mutableListOf<String>()
        var bullets = mutableListOf<String>()
        var numbered = mutableListOf<String>()

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
        fun flushAll() { flushPara(); flushBullets(); flushNumbered() }

        for (line in src.split("\n")) {
            val l = line.trim()
            val bullet = BULLET.find(l)
            val number = NUMBERED.find(l)
            when {
                l.isEmpty() -> flushAll()
                bullet != null -> { flushPara(); flushNumbered(); bullets.add(bullet.groupValues[1]) }
                number != null -> { flushPara(); flushBullets(); numbered.add(number.groupValues[1]) }
                else -> { flushBullets(); flushNumbered(); para.add(l) }
            }
        }
        flushAll()
        return blocks
    }

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
                c == '*' && i + 1 < text.length && text[i + 1] == '*' -> { flush(); bold = !bold; i += 2 }
                c == '*' || c == '_' -> { flush(); italic = !italic; i++ }
                else -> { sb.append(c); i++ }
            }
        }
        flush()
        return spans.ifEmpty { listOf(MdSpan(text)) }
    }
}
