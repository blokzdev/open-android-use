package dev.openandroiduse.companion.agent.llm

import dev.openandroiduse.companion.agent.AgentTools
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cross-backend parity (Phase 5.8): drives each real cloud [AgentBackend] against
 * a loopback stub emitting that provider's wire, and asserts the **same neutral
 * semantics** come out of the shared seam — a tool-call turn maps to `TOOL_USE`
 * with one parsed `ToolUse`, a text turn maps to `END_TURN` with reconstructed
 * deltas. Parameterizing over {Anthropic, Gemini} fills the Anthropic
 * backend-streaming gap and pins behavioral equivalence in one structure. The
 * SSE payloads mirror the emulator `StubModelServer`. On-device (Gemma) needs the
 * native engine, so its parity lives in the emulator smoke + the hardware ledger.
 */
@RunWith(Parameterized::class)
class CloudBackendStreamingMatrixTest(private val case: Case) {

    enum class Wire { ANTHROPIC, GEMINI }

    class Case(
        private val label: String,
        val wire: Wire,
        val backend: (baseUrl: String) -> AgentBackend,
    ) {
        override fun toString(): String = label
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Case> = listOf(
            Case("anthropic", Wire.ANTHROPIC) { url -> AnthropicBackend(apiKey = "stub-key", baseUrl = url) },
            Case("gemini", Wire.GEMINI) { url -> GeminiBackend(apiKey = "stub-key", baseUrl = url) },
        )
    }

    private lateinit var server: MatrixStubServer
    private lateinit var backend: AgentBackend

    @Before
    fun setUp() {
        server = MatrixStubServer(case.wire).also { it.start() }
        backend = case.backend("http://127.0.0.1:${server.port}")
    }

    @After
    fun tearDown() {
        backend.close()
        server.shutdown()
    }

    private fun request() = BackendRequest(
        model = if (case.wire == Wire.ANTHROPIC) "claude-opus-4-8" else "gemini-2.5-pro",
        systemPrompt = "You operate an Android device.",
        tools = AgentTools.specs(),
        messages = listOf(AgentMessage(AgentRole.USER, listOf(AgentContent.Text("Look at the screen.")))),
    )

    private class CollectingSink : BackendSink {
        val text = StringBuilder()
        override fun onTextDelta(text: String) { this.text.append(text) }
        override fun onThinkingDelta(text: String) {}
    }

    @Test
    fun toolCallTurnMapsToToolUse() {
        server.scenario = MatrixStubServer.Scenario.TOOL_CALL
        val sink = CollectingSink()
        val turn = backend.streamTurn(request(), sink)

        assertEquals(AgentStopReason.TOOL_USE, turn.stopReason)
        val toolUses = turn.assistant.content.filterIsInstance<AgentContent.ToolUse>()
        assertEquals(1, toolUses.size)
        assertEquals("get_app_state", toolUses[0].name)
        assertTrue("args should target the foreground app", toolUses[0].argsJson.contains("foreground"))
    }

    @Test
    fun textTurnMapsToEndTurn() {
        server.scenario = MatrixStubServer.Scenario.TEXT
        val sink = CollectingSink()
        val turn = backend.streamTurn(request(), sink)

        assertEquals(AgentStopReason.END_TURN, turn.stopReason)
        assertEquals("Done — I can see the screen.", sink.text.toString())
        val texts = turn.assistant.content.filterIsInstance<AgentContent.Text>()
        assertEquals("Done — I can see the screen.", texts.single().text)
    }
}

/**
 * Minimal loopback HTTP server that answers any POST with a scripted SSE stream in
 * either provider's wire. Mirrors the emulator `StubModelServer` payloads so the
 * JVM matrix exercises the same accumulator/decoder paths without an emulator.
 */
private class MatrixStubServer(private val wire: CloudBackendStreamingMatrixTest.Wire) {

    enum class Scenario { TOOL_CALL, TEXT }

    @Volatile
    var scenario: Scenario = Scenario.TOOL_CALL

    private lateinit var serverSocket: ServerSocket

    @Volatile
    private var running = false

    val port: Int get() = serverSocket.localPort

    fun start() {
        serverSocket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        running = true
        Thread({
            while (running) {
                val client = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    break
                }
                try {
                    client.use { handle(it.getInputStream(), it.getOutputStream()) }
                } catch (_: Exception) {
                }
            }
        }, "matrix-stub-server").start()
    }

    fun shutdown() {
        running = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }

    private fun handle(input: InputStream, output: OutputStream) {
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isBlank()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toIntOrNull() ?: 0
            }
        }
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(body, read, contentLength - read)
            if (count < 0) break
            read += count
        }

        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        when (wire) {
            CloudBackendStreamingMatrixTest.Wire.ANTHROPIC ->
                for ((event, data) in if (scenario == Scenario.TOOL_CALL) anthropicToolUse() else anthropicText()) {
                    output.write("event: $event\ndata: $data\n\n".toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                }
            CloudBackendStreamingMatrixTest.Wire.GEMINI ->
                for (data in if (scenario == Scenario.TOOL_CALL) geminiToolUse() else geminiText()) {
                    output.write("data: $data\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (byte == '\n'.code) return buffer.toString().trimEnd('\r')
            buffer.append(byte.toChar())
        }
    }

    private fun messageStart(): Pair<String, String> = "message_start" to
        """{"type":"message_start","message":{"id":"msg_stub","type":"message","role":"assistant","model":"claude-opus-4-8","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":25,"output_tokens":1}}}"""

    private fun anthropicToolUse(): List<Pair<String, String>> = listOf(
        messageStart(),
        "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_stub_1","name":"get_app_state","input":{}}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"app\": \"foreground\"}"}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":20}}""",
        "message_stop" to """{"type":"message_stop"}""",
    )

    private fun anthropicText(): List<Pair<String, String>> = listOf(
        messageStart(),
        "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Done — "}}""",
        "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"I can see the screen."}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":12}}""",
        "message_stop" to """{"type":"message_stop"}""",
    )

    private fun geminiToolUse(): List<String> = listOf(
        """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"get_app_state","args":{"app":"foreground"}}}]},"finishReason":"STOP","index":0}]}""",
    )

    private fun geminiText(): List<String> = listOf(
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"Done — "}]},"index":0}]}""",
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"I can see the screen."}]},"finishReason":"STOP","index":0}]}""",
    )
}
