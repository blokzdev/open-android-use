package dev.openandroiduse.companion.agent

import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * A loopback stub for the Messages API streaming endpoint: serves scripted
 * SSE responses and records every request body, so the emulator smoke can run
 * the real agent loop (SDK, accumulator, tool executor, screenshots) without
 * an API key or network egress.
 */
class StubModelServer {

    val requestBodies: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private lateinit var serverSocket: ServerSocket

    @Volatile
    private var running = false
    private var requestIndex = 0

    val port: Int get() = serverSocket.localPort

    fun start() {
        // Explicitly IPv4: Android's getLoopbackAddress() prefers ::1, but the
        // SDK client dials the 127.0.0.1 base URL — a family mismatch is an
        // instant ECONNREFUSED even within one process.
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
        }, "stub-model-server").start()
    }

    fun shutdown() {
        running = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }

    private fun handle(input: InputStream, output: java.io.OutputStream) {
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
        requestBodies.add(String(body, 0, read, StandardCharsets.UTF_8))

        val events = synchronized(this) {
            val turn = requestIndex
            requestIndex++
            if (turn == 0) toolUseTurn() else endTurn()
        }
        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        for ((event, data) in events) {
            output.write("event: $event\ndata: $data\n\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
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

    /** Turn 1: narrate, then call get_app_state on the foreground app. */
    private fun toolUseTurn(): List<Pair<String, String>> = listOf(
        messageStart(),
        "content_block_start" to
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
        "content_block_delta" to
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Taking a look at the screen."}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        "content_block_start" to
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_stub_1","name":"get_app_state","input":{}}}""",
        "content_block_delta" to
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"app\": \"foreground\"}"}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":1}""",
        "message_delta" to
            """{"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":20}}""",
        "message_stop" to """{"type":"message_stop"}""",
    )

    /** Turn 2: read the tool result and finish. */
    private fun endTurn(): List<Pair<String, String>> = listOf(
        messageStart(),
        "content_block_start" to
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
        "content_block_delta" to
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Done — I can see the screen."}}""",
        "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        "message_delta" to
            """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":12}}""",
        "message_stop" to """{"type":"message_stop"}""",
    )
}
