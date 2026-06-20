package dev.openandroiduse.companion.agent.llm

import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.Type
import org.json.JSONObject

/**
 * Translation between the provider-neutral agent model and the Gemini
 * `com.google.genai` wire types (Phase 5.2). Pure functions, no client — so the
 * mapping is unit-testable without a network call or an API key. Mirrors
 * [AnthropicMessageMapping]'s role for the Gemini side.
 *
 * Note on tool-call identity: Gemini matches a function *response* to its call by
 * **name**, but the neutral [AgentContent.ToolResult] only carries a
 * `toolUseId`. So [GeminiBackend] encodes the function name into the id it emits
 * ([encodeToolUseId]) and [toContents] recovers it ([nameFromToolUseId]) — a
 * self-consistent scheme within one backend instance (one provider per task).
 */
object GeminiMessageMapping {

    const val SCREENSHOT_OMITTED = "(screenshot omitted to save context)"
    private const val ID_DELIMITER = '@'

    /** The frozen 9 tools as a single Gemini [Tool] of function declarations. */
    fun toTools(specs: List<ToolSpec>): List<Tool> =
        listOf(Tool.builder().functionDeclarations(specs.map { toFunctionDeclaration(it) }).build())

    fun toFunctionDeclaration(spec: ToolSpec): FunctionDeclaration {
        val parameters = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(spec.properties.mapValues { fragmentToSchema(it.value) })
            .apply { if (spec.required.isNotEmpty()) required(spec.required) }
            .build()
        return FunctionDeclaration.builder()
            .name(spec.name)
            .description(spec.description)
            .parameters(parameters)
            .build()
    }

    /** Convert one neutral JSON-schema property fragment (e.g. {"type":"string"}) to a Gemini [Schema]. */
    private fun fragmentToSchema(fragment: Map<String, Any>): Schema {
        val builder = Schema.builder().type(typeOf(fragment["type"] as? String))
        (fragment["description"] as? String)?.let { builder.description(it) }
        (fragment["enum"] as? List<*>)?.let { builder.enum_(it.filterIsInstance<String>()) }
        @Suppress("UNCHECKED_CAST")
        (fragment["items"] as? Map<String, Any>)?.let { builder.items(fragmentToSchema(it)) }
        return builder.build()
    }

    private fun typeOf(type: String?): Type.Known = when (type?.lowercase()) {
        "string" -> Type.Known.STRING
        "number" -> Type.Known.NUMBER
        "integer" -> Type.Known.INTEGER
        "boolean" -> Type.Known.BOOLEAN
        "array" -> Type.Known.ARRAY
        "object" -> Type.Known.OBJECT
        else -> Type.Known.STRING
    }

    /** Build the Gemini conversation history from neutral messages. */
    fun toContents(messages: List<AgentMessage>): List<Content> = messages.map { message ->
        when (message.role) {
            AgentRole.USER ->
                Content.builder().role("user").parts(message.content.flatMap { userParts(it) }).build()
            AgentRole.ASSISTANT ->
                // Phase 5.2 fix: an assistant turn that issued function calls carries its original
                // model `Content` (with the per-call `thoughtSignature` Gemini 2.5 requires echoed
                // back) in replayPayload — return it verbatim. Only history rebuilt from a persisted,
                // text-only transcript (no function calls → no signature needed) falls through to the
                // neutral rebuild.
                (message.replayPayload as? Content)
                    ?: Content.builder().role("model").parts(message.content.mapNotNull { modelPart(it) }).build()
        }
    }

    /**
     * The assistant model [Content] to stash in `replayPayload` for byte-exact replay: the narration
     * text (if any) plus the model's **raw** functionCall [Part]s — which carry their `thoughtSignature`
     * inside them, so the signature round-trips without this code ever reading or rebuilding it.
     */
    fun replayContent(text: String, functionCallParts: List<Part>): Content {
        val parts = buildList {
            if (text.isNotBlank()) add(Part.fromText(text))
            addAll(functionCallParts)
        }
        return Content.builder().role("model").parts(parts).build()
    }

    private fun userParts(content: AgentContent): List<Part> = when (content) {
        is AgentContent.Text -> listOf(Part.fromText(content.text))
        is AgentContent.ToolResult -> {
            val response = mutableMapOf<String, Any>(
                (if (content.isError) "error" else "output") to content.text,
            )
            if (content.omittedImage) response["note"] = SCREENSHOT_OMITTED
            val parts = mutableListOf(
                Part.fromFunctionResponse(nameFromToolUseId(content.toolUseId), response),
            )
            content.image?.let { parts.add(Part.fromBytes(decodeBase64(it.pngBase64), "image/png")) }
            parts
        }
        // A tool_use never appears inside a user message.
        is AgentContent.ToolUse -> emptyList()
    }

    private fun modelPart(content: AgentContent): Part? = when (content) {
        is AgentContent.Text -> if (content.text.isEmpty()) null else Part.fromText(content.text)
        is AgentContent.ToolUse -> Part.fromFunctionCall(content.name, jsonToMap(content.argsJson))
        // A tool_result never appears inside an assistant message.
        is AgentContent.ToolResult -> null
    }

    /** Map Gemini function calls in a finished turn to neutral tool_use blocks (name encoded in the id). */
    fun toToolUses(calls: List<FunctionCall>): List<AgentContent.ToolUse> =
        calls.mapIndexed { index, call ->
            val name = call.name().orElse("")
            AgentContent.ToolUse(
                id = encodeToolUseId(name, index),
                name = name,
                argsJson = JSONObject(call.args().orElse(emptyMap())).toString(),
            )
        }

    fun encodeToolUseId(name: String, index: Int): String = "$name$ID_DELIMITER$index"

    fun nameFromToolUseId(toolUseId: String): String = toolUseId.substringBeforeLast(ID_DELIMITER)

    private fun jsonToMap(argsJson: String): Map<String, Any> {
        val obj = try {
            JSONObject(argsJson)
        } catch (_: Exception) {
            return emptyMap()
        }
        return obj.keys().asSequence().associateWith { obj.get(it) }
    }

    // java.util.Base64 (not android.util.Base64): available on minSdk 26+ and on
    // the JVM, so the mapping stays unit-testable without an emulator.
    private fun decodeBase64(data: String): ByteArray = java.util.Base64.getDecoder().decode(data)
}
