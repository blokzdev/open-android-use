package dev.openandroiduse.companion.agent

import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam

/**
 * Rebuilds a valid Anthropic message history from a persisted transcript so a
 * resumed session continues with its conversational context. Only the user and
 * assistant *text* turns become model messages; thinking, tool, and note lines
 * are display-only and dropped. Consecutive same-role lines are merged and a
 * trailing unanswered user line is dropped, guaranteeing a strictly alternating
 * history that starts with the user and ends with the assistant — so the next
 * task's user message keeps the conversation well-formed.
 *
 * Screenshots and raw tool_use/tool_result blocks are intentionally not
 * replayed (screenshots never persist); the agent re-observes the device live.
 */
object SessionHistory {

    fun rebuild(transcript: List<Pair<String, String>>): List<MessageParam> {
        val merged = mutableListOf<Pair<MessageParam.Role, StringBuilder>>()
        for ((kind, text) in transcript) {
            val role = when (kind) {
                AgentController.KIND_USER -> MessageParam.Role.USER
                AgentController.KIND_ASSISTANT -> MessageParam.Role.ASSISTANT
                else -> continue
            }
            val clean = text.trim()
            if (clean.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                last.second.append("\n\n").append(clean)
            } else {
                merged.add(role to StringBuilder(clean))
            }
        }
        // Drop a trailing user turn with no assistant reply: the resume prompt is
        // its continuation, and two user messages in a row would be malformed.
        if (merged.lastOrNull()?.first == MessageParam.Role.USER) {
            merged.removeAt(merged.size - 1)
        }
        return merged.map { (role, body) ->
            MessageParam.builder()
                .role(role)
                .contentOfBlockParams(
                    listOf(ContentBlockParam.ofText(TextBlockParam.builder().text(body.toString()).build())),
                )
                .build()
        }
    }
}
