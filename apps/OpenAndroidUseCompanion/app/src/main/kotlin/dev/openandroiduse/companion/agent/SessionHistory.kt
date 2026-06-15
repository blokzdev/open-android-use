package dev.openandroiduse.companion.agent

import dev.openandroiduse.companion.agent.llm.AgentContent
import dev.openandroiduse.companion.agent.llm.AgentMessage
import dev.openandroiduse.companion.agent.llm.AgentRole

/**
 * Rebuilds a valid message history from a persisted transcript so a resumed
 * session continues with its conversational context. Only the user and
 * assistant *text* turns become model messages; thinking, tool, and note lines
 * are display-only and dropped. Consecutive same-role lines are merged and a
 * trailing unanswered user line is dropped, guaranteeing a strictly alternating
 * history that starts with the user and ends with the assistant — so the next
 * task's user message keeps the conversation well-formed.
 *
 * The rebuilt assistant turns carry no replay payload (text-only resume
 * context); screenshots and raw tool_use/tool_result blocks are intentionally
 * not replayed (screenshots never persist), and the agent re-observes the
 * device live.
 */
object SessionHistory {

    fun rebuild(transcript: List<Pair<String, String>>): List<AgentMessage> {
        val merged = mutableListOf<Pair<AgentRole, StringBuilder>>()
        for ((kind, text) in transcript) {
            val role = when (kind) {
                AgentController.KIND_USER -> AgentRole.USER
                AgentController.KIND_ASSISTANT -> AgentRole.ASSISTANT
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
        if (merged.lastOrNull()?.first == AgentRole.USER) {
            merged.removeAt(merged.size - 1)
        }
        return merged.map { (role, body) ->
            AgentMessage(role, listOf(AgentContent.Text(body.toString())))
        }
    }
}
