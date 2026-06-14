package dev.openandroiduse.companion.agent

/**
 * UI-side prettifier for the agent's KIND_TOOL transcript strings (e.g.
 * "▸ click [42]", "✗ click failed — …"). The transcript text itself is left
 * unchanged (the emulator smoke asserts on it); this only affects how the chat
 * renders tool chips. Pure logic, unit-tested.
 */
object ToolChipLabel {

    fun isError(raw: String): Boolean = raw.trimStart().startsWith("✗")

    fun describe(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("✗")) {
            return t.removePrefix("✗").trim().ifEmpty { "Action failed" }
        }
        val body = t.removePrefix("▸").trim()
        val name = body.substringBefore(' ', body).trim()
        val arg = if (' ' in body) body.substringAfter(' ').trim() else ""
        return when (name) {
            "click" -> if (arg.isEmpty()) "Tap" else "Tap $arg"
            "type_text" -> if (arg.isEmpty()) "Type text" else "Type $arg"
            "set_value" -> if (arg.isEmpty()) "Set value" else "Set $arg"
            "press_key" -> if (arg.isEmpty()) "Press key" else "Press $arg"
            "scroll" -> if (arg.isEmpty()) "Scroll" else "Scroll $arg"
            "drag" -> "Drag"
            "perform_secondary_action" -> if (arg.isEmpty()) "Long-press" else "Long-press $arg"
            "get_app_state" -> if (arg.isEmpty()) "Read the screen" else "Open $arg"
            "list_apps" -> "List apps"
            else -> body.ifEmpty { "Action" }
        }
    }
}
