package dev.openandroiduse.companion.agent

/**
 * Accumulates streamed text deltas and releases complete sentences — the unit
 * the narrator speaks. Pure logic, unit-testable.
 */
class SentenceBuffer {

    private val buffer = StringBuilder()

    /** Appends a delta and returns any complete sentences now available. */
    fun add(delta: String): List<String> {
        buffer.append(delta)
        val sentences = mutableListOf<String>()
        var start = 0
        for (index in buffer.indices) {
            val ch = buffer[index]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                val sentence = buffer.substring(start, index + 1).trim()
                if (sentence.isNotEmpty()) {
                    sentences.add(sentence)
                }
                start = index + 1
            }
        }
        if (start > 0) {
            buffer.delete(0, start)
        }
        return sentences
    }

    /** Releases whatever is left (end of message). */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.setLength(0)
        return rest.ifEmpty { null }
    }
}
