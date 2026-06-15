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

/**
 * Drives the real [GeminiBackend] streaming + decode path against a loopback stub
 * emitting Gemini `streamGenerateContent` SSE (Phase 5.2). De-risks the wire
 * mapping (delta text, function-call parsing, stop-reason mapping) without an
 * emulator — the genai client is plain JVM.
 */
class GeminiBackendStreamingTest {

    private lateinit var server: GeminiStubServer
    private lateinit var backend: GeminiBackend

    @Before
    fun setUp() {
        server = GeminiStubServer().also { it.start() }
        backend = GeminiBackend(apiKey = "stub-key", baseUrl = "http://127.0.0.1:${server.port}")
    }

    @After
    fun tearDown() {
        backend.close()
        server.shutdown()
    }

    private fun request() = BackendRequest(
        model = "gemini-2.5-pro",
        systemPrompt = "You operate an Android device.",
        tools = AgentTools.specs(),
        messages = listOf(AgentMessage(AgentRole.USER, listOf(AgentContent.Text("Look at the screen.")))),
    )

    private class CollectingSink : BackendSink {
        val text = StringBuilder()
        val thinking = StringBuilder()
        override fun onTextDelta(text: String) { this.text.append(text) }
        override fun onThinkingDelta(text: String) { this.thinking.append(text) }
    }

    @Test
    fun functionCallTurnParsesToolUseAndMapsToolUseStop() {
        val sink = CollectingSink()
        val turn = backend.streamTurn(request(), sink)

        assertEquals(AgentStopReason.TOOL_USE, turn.stopReason)
        val toolUses = turn.assistant.content.filterIsInstance<AgentContent.ToolUse>()
        assertEquals(1, toolUses.size)
        assertEquals("get_app_state", toolUses[0].name)
        assertTrue(toolUses[0].argsJson.contains("foreground"))
        assertTrue("no plain text expected on a tool-call turn", sink.text.isEmpty())
    }

    @Test
    fun textTurnStreamsDeltasAndMapsEndTurn() {
        server.scenario = GeminiStubServer.Scenario.TEXT
        val sink = CollectingSink()
        val turn = backend.streamTurn(request(), sink)

        assertEquals(AgentStopReason.END_TURN, turn.stopReason)
        assertEquals("Done — I can see the screen.", sink.text.toString())
        val texts = turn.assistant.content.filterIsInstance<AgentContent.Text>()
        assertEquals("Done — I can see the screen.", texts.single().text)
    }
}

/**
 * Minimal loopback HTTP server that answers any POST with a Gemini SSE stream.
 * Mirrors the androidTest StubModelServer skeleton but for the Gemini wire.
 */
private class GeminiStubServer {

    enum class Scenario { FUNCTION_CALL, TEXT }

    @Volatile
    var scenario: Scenario = Scenario.FUNCTION_CALL

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
        }, "gemini-stub-server").start()
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
        for (data in dataEvents()) {
            output.write("data: $data\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    private fun dataEvents(): List<String> = when (scenario) {
        Scenario.FUNCTION_CALL -> listOf(
            """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"get_app_state","args":{"app":"foreground"}}}]},"finishReason":"STOP","index":0}]}""",
        )
        Scenario.TEXT -> listOf(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"Done — "}]},"index":0}]}""",
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"I can see the screen."}]},"finishReason":"STOP","index":0}]}""",
        )
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
}
