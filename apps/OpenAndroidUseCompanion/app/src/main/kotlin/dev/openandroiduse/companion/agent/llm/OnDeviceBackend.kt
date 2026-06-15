package dev.openandroiduse.companion.agent.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.util.Base64

/**
 * On-device (Gemma 4 E2B via LiteRT-LM) [AgentBackend] (Phase 5.5b). Runs the
 * model fully locally — no API key, no network. The only file (with
 * [GemmaToolPrompt]) under `agent` importing `com.google.ai.edge.litertlm`.
 *
 * Runtime behaviour can only be validated on real hardware (no device / 2.6 GB
 * model / on-device inference in CI), so this is wired against the confirmed
 * LiteRT-LM API and the function-calling format is pinned by [GemmaToolPrompt]'s
 * unit tests; the live loop is verified on-device (VERIFICATION V131+). The
 * `Engine` is created once per task (init takes several seconds) and reused
 * across the loop's turns; [close] releases it.
 *
 * [modelPath] is the verified local `.litertlm` file (from `OnDeviceModelManager`).
 */
class OnDeviceBackend(private val modelPath: String) : AgentBackend {

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var cancelled = false

    private fun engine(): Engine {
        engine?.let { return it }
        // Vision-capable engine: LiteRT-LM loads the image encoder only when an
        // image is actually sent, so text-only turns stay lightweight. The
        // perception toggle (AgentSettings.sendScreenshots) decides whether the
        // request carries a screenshot; we just send what's there.
        val created = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                maxNumImages = 1,
            ),
        )
        created.initialize() // several seconds on first call
        engine = created
        return created
    }

    override fun streamTurn(request: BackendRequest, sink: BackendSink): CompletedTurn {
        cancelled = false
        val prompt = GemmaToolPrompt.render(request.systemPrompt, request.tools, request.messages)
        val full = StringBuilder()

        val engine = try {
            engine()
        } catch (error: Exception) {
            throw BackendStreamException(error.message ?: "on-device engine failed to start", error)
        }
        // Vision (Phase 5.6): when the perception toggle is on, the latest tool
        // result carries a screenshot — send it to Gemma as an image alongside
        // the text prompt. Text-only mode strips images upstream, so absence ⇒
        // text-only path (no encoder load).
        val latestImage = request.messages.asReversed()
            .firstNotNullOfOrNull { message ->
                message.content.filterIsInstance<AgentContent.ToolResult>()
                    .lastOrNull { it.image != null }?.image
            }
        val visionContents = latestImage?.let {
            Contents.of(Content.Text(prompt), Content.ImageBytes(Base64.getDecoder().decode(it.pngBase64)))
        }

        val conversation = engine.createConversation()
        try {
            runBlocking {
                val flow = if (visionContents != null) {
                    conversation.sendMessageAsync(visionContents)
                } else {
                    conversation.sendMessageAsync(prompt)
                }
                flow.collect { message ->
                    if (cancelled) throw CancellationException()
                    // Each emission's text; tolerate either cumulative or delta chunks.
                    val soFar = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    val delta = if (soFar.startsWith(full)) soFar.substring(full.length) else soFar
                    if (delta.isNotEmpty()) {
                        sink.onTextDelta(delta)
                        full.append(delta)
                    }
                }
            }
        } catch (_: CancellationException) {
            // Stop button: fall through and finish with whatever streamed so far.
        } finally {
            try {
                conversation.close()
            } catch (_: Exception) {
            }
        }

        val parsed = GemmaToolPrompt.parse(full.toString())
        val content = buildList {
            if (parsed.text.isNotEmpty()) add(AgentContent.Text(parsed.text))
            addAll(parsed.toolUses)
        }
        return CompletedTurn(
            assistant = AgentMessage(AgentRole.ASSISTANT, content),
            stopReason = if (parsed.toolUses.isNotEmpty()) AgentStopReason.TOOL_USE else AgentStopReason.END_TURN,
        )
    }

    override fun cancel() {
        cancelled = true
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    override fun close() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }
}
