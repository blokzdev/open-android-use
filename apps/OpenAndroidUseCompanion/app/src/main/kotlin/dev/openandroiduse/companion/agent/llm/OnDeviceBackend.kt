package dev.openandroiduse.companion.agent.llm

/**
 * On-device (Gemma 4 E2B via LiteRT-LM) [AgentBackend].
 *
 * Phase 5.5a ships the provider, the model download/management, and the gating —
 * but **not** the inference, which requires the native LiteRT-LM runtime and can
 * only be validated on real hardware (no device / 2.6 GB model in CI). So this is
 * a placeholder: the provider is selectable and the whole download + tier-gating +
 * settings flow is exercisable, and a task started on-device returns a clear note.
 * Phase 5.5b replaces this with real streaming inference + function calling.
 *
 * [modelPath] is the verified local `.litertlm` file path (resolved by
 * `OnDeviceModelManager`); it is unused until 5.5b.
 */
class OnDeviceBackend(@Suppress("unused") private val modelPath: String) : AgentBackend {

    override fun streamTurn(request: BackendRequest, sink: BackendSink): CompletedTurn {
        val note = "On-device inference is not wired up yet (it lands in Phase 5.5b). " +
            "The model is downloaded and ready — switch to Claude or Gemini in Settings to run tasks for now."
        sink.onTextDelta(note)
        return CompletedTurn(
            assistant = AgentMessage(AgentRole.ASSISTANT, listOf(AgentContent.Text(note))),
            stopReason = AgentStopReason.END_TURN,
        )
    }

    override fun cancel() {}

    override fun close() {}
}
