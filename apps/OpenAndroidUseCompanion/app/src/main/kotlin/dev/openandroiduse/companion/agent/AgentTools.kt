package dev.openandroiduse.companion.agent

import dev.openandroiduse.companion.agent.llm.ToolSpec

/**
 * The 9-tool Computer Use surface, ported verbatim from the Go bridge's
 * toolDefinitions() (apps/OpenAndroidUse/main.go). Order, names, descriptions,
 * and schemas are frozen and deterministic: together with [SYSTEM_PROMPT] they
 * form the byte-identical prompt-cache prefix across turns — do not interleave
 * anything volatile (timestamps, ids) before them.
 *
 * [specs] is the provider-neutral source of truth (Phase 5.1); each backend maps
 * it to its own wire format (the Anthropic mapping lives in
 * AnthropicMessageMapping.toAnthropicTool), so this file stays SDK-free.
 */
object AgentTools {

    /**
     * On-device adaptation of the bridge's server instructions plus the
     * Second Pair of Hands interaction contract (narrate-then-act, consent
     * ladder — docs/design-docs/phase3-agent-loop.md). Frozen: no dynamic
     * interpolation, ever.
     */
    const val SYSTEM_PROMPT: String =
        "You are the Open Android Use companion agent, operating this Android phone " +
            "directly on behalf of its owner, who is watching the screen. Computer Use " +
            "tools let you interact with Android apps by performing UI actions.\n\n" +
            "Begin by calling `get_app_state` every turn you want to use Computer Use to " +
            "get the latest state before acting. The available tools are list_apps, " +
            "get_app_state, click, perform_secondary_action, scroll, drag, type_text, " +
            "press_key, and set_value.\n\n" +
            "Prefer element-targeted interactions over coordinate taps when an index for " +
            "the targeted element is available. Android has a single foreground app: " +
            "get_app_state brings the target app to the foreground if needed, and `app` " +
            "may be set to \"foreground\" to target whatever is currently on screen. " +
            "`mouse_button: \"right\"` maps to a long-press. `press_key` accepts a " +
            "reduced Android key set: \"Return\"/\"Enter\" (IME action), \"Back\", " +
            "\"Home\", \"Recents\", and \"BackSpace\"/\"Delete\". `type_text` and " +
            "`set_value` both replace the focused field's full value and carry full " +
            "Unicode.\n\n" +
            "Interaction contract with the user:\n" +
            "- Narrate before acting: one short sentence of intent before each action " +
            "batch. No silent multi-step runs longer than one screen transition.\n" +
            "- Consent ladder: read and navigate freely; stop and ask before externally " +
            "visible actions (send, post, purchase, delete, call); never touch " +
            "credential or secure surfaces uninvited — logins are the user's job, hand " +
            "off and wait.\n" +
            "- If the screen state contradicts your expectation, re-snapshot and adapt " +
            "rather than repeating the same action."

    /**
     * Provider-neutral source of truth for the frozen 9-tool schema (Phase 5.1).
     * Same order, names, descriptions, and schemas as the Go bridge; each
     * backend maps these to its own wire format.
     */
    fun specs(): List<ToolSpec> = listOf(
        spec(
            name = "click",
            description = "Click an element by index or pixel coordinates from screenshot. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "element_index" to stringProperty("Element index to click"),
                "x" to numberProperty("X coordinate in screenshot pixel coordinates"),
                "y" to numberProperty("Y coordinate in screenshot pixel coordinates"),
                "click_count" to integerProperty("Number of clicks. Defaults to 1"),
                "mouse_button" to enumStringProperty(
                    "Mouse button to click. Defaults to left.",
                    listOf("left", "right", "middle"),
                ),
            ),
            required = listOf("app"),
        ),
        spec(
            name = "drag",
            description = "Drag from one point to another using pixel coordinates. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "from_x" to numberProperty("Start X coordinate"),
                "from_y" to numberProperty("Start Y coordinate"),
                "to_x" to numberProperty("End X coordinate"),
                "to_y" to numberProperty("End Y coordinate"),
            ),
            required = listOf("app", "from_x", "from_y", "to_x", "to_y"),
        ),
        spec(
            name = "get_app_state",
            description = "Get the state of an already running app's key window and return a " +
                "screenshot and accessibility tree. This must be called once per assistant " +
                "turn before interacting with the app. This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
            ),
            required = listOf("app"),
        ),
        spec(
            name = "list_apps",
            description = "List the apps on this computer. Returns the set of apps that are " +
                "currently running, as well as any that have been used in the last 14 days, " +
                "including details on usage frequency. This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(),
            required = emptyList(),
        ),
        spec(
            name = "perform_secondary_action",
            description = "Invoke a secondary accessibility action exposed by an element. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "element_index" to stringProperty("Element identifier"),
                "action" to stringProperty("Secondary accessibility action name"),
            ),
            required = listOf("app", "element_index", "action"),
        ),
        spec(
            name = "press_key",
            description = "Press a key or key-combination on the keyboard, including modifier " +
                "and navigation keys.\n  - This supports xdotool's `key` syntax.\n  - Examples: " +
                "\"a\", \"Return\", \"Tab\", \"super+c\", \"Up\", \"KP_0\" (for the numpad 0). " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "key" to stringProperty("Key or key-combination to press"),
            ),
            required = listOf("app", "key"),
        ),
        spec(
            name = "scroll",
            description = "Scroll an element in a direction by a number of pages. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "direction" to stringProperty("Scroll direction: up, down, left, or right"),
                "element_index" to stringProperty("Element identifier"),
                "pages" to numberProperty(
                    "Number of pages to scroll. Fractional values are supported. Defaults to 1",
                ),
            ),
            required = listOf("app", "element_index", "direction"),
        ),
        spec(
            name = "set_value",
            description = "Set the value of a settable accessibility element. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "element_index" to stringProperty("Element identifier"),
                "value" to stringProperty("Value to assign"),
            ),
            required = listOf("app", "element_index", "value"),
        ),
        spec(
            name = "type_text",
            description = "Type literal text using keyboard input. " +
                "This tool is part of plugin `Computer Use`.",
            properties = linkedMapOf(
                "app" to stringProperty("App name or bundle identifier"),
                "text" to stringProperty("Literal text to type"),
            ),
            required = listOf("app", "text"),
        ),
    )

    private fun spec(
        name: String,
        description: String,
        properties: LinkedHashMap<String, Map<String, Any>>,
        required: List<String>,
    ): ToolSpec = ToolSpec(name, description, properties, required)

    private fun stringProperty(description: String): Map<String, Any> =
        linkedMapOf("type" to "string", "description" to description)

    private fun enumStringProperty(description: String, values: List<String>): Map<String, Any> =
        linkedMapOf("type" to "string", "description" to description, "enum" to values)

    private fun numberProperty(description: String): Map<String, Any> =
        linkedMapOf("type" to "number", "description" to description)

    private fun integerProperty(description: String): Map<String, Any> =
        linkedMapOf("type" to "integer", "description" to description)
}
