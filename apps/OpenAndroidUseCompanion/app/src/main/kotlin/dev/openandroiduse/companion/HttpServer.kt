package dev.openandroiduse.companion

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP/1.1 server, deliberately dependency-free. Binds the loopback
 * interface only: reachable from a host machine exclusively via
 * `adb forward tcp:8355 tcp:8355`, i.e. behind the USB-debugging trust gate.
 */
class HttpServer(private val service: CompanionService, private val port: Int) {

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    fun start() {
        running = true
        Thread({
            try {
                val socket = ServerSocket(port, BACKLOG, InetAddress.getLoopbackAddress())
                serverSocket = socket
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    Thread({ handle(client) }, "oau-http-worker").start()
                }
            } catch (error: Exception) {
                Log.e(CompanionService.TAG, "http server failed to start on $port", error)
            }
        }, "oau-http-accept").start()
    }

    fun shutdown() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun handle(client: Socket) {
        client.use {
            try {
                val input = client.getInputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.trim().split(" ")
                if (parts.size < 2) {
                    return
                }
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                var contentLength = 0
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isBlank()) {
                        break
                    }
                    val separator = header.indexOf(':')
                    if (separator > 0 && header.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                        contentLength = header.substring(separator + 1).trim().toIntOrNull() ?: 0
                    }
                }
                val body = readBody(input, contentLength)
                route(client.getOutputStream(), method, path, body)
            } catch (error: Exception) {
                Log.w(CompanionService.TAG, "request handling failed", error)
            }
        }
    }

    private fun route(output: OutputStream, method: String, path: String, body: String) {
        when {
            method == "GET" && path == "/health" -> respondJson(output, 200, health())
            method == "GET" && path == "/snapshot" -> respondJson(output, 200, SnapshotBuilder.build(service))
            method == "GET" && path == "/screenshot" -> respondScreenshot(output)
            method == "POST" && path == "/action" -> respondJson(output, 200, ActionExecutor.execute(service, body))
            else -> respondJson(output, 404, failure("not found: $method $path"))
        }
    }

    private fun health(): JSONObject = JSONObject()
        .put("ok", true)
        .put("service", "open-android-use-companion")
        .put("version", BuildConfig.VERSION_NAME)
        .put("protocol", Protocol.VERSION)
        .put("screenshot", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)

    private fun respondScreenshot(output: OutputStream) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            respondJson(output, 501, failure("screenshot requires Android 11+"))
            return
        }
        val latch = CountDownLatch(1)
        var png: ByteArray? = null
        var errorText: String? = null
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { it.run() },
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        if (software == null) {
                            errorText = "could not convert screenshot buffer"
                        } else {
                            val buffer = ByteArrayOutputStream()
                            software.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                            png = buffer.toByteArray()
                        }
                        result.hardwareBuffer.close()
                    } catch (error: Exception) {
                        errorText = "screenshot conversion failed: ${error.message}"
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    errorText = "takeScreenshot failed with code $errorCode"
                    latch.countDown()
                }
            }
        )
        if (!latch.await(5, TimeUnit.SECONDS)) {
            respondJson(output, 500, failure("screenshot timed out"))
            return
        }
        val bytes = png
        if (bytes == null) {
            respondJson(output, 500, failure(errorText ?: "screenshot failed"))
            return
        }
        respond(output, 200, "image/png", bytes)
    }

    private fun respondJson(output: OutputStream, status: Int, json: JSONObject) {
        respond(output, status, "application/json; charset=utf-8", json.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun respond(output: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val reason = if (status == 200) "OK" else "Error"
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                return if (buffer.isEmpty()) null else buffer.toString()
            }
            if (byte == '\n'.code) {
                return buffer.toString().trimEnd('\r')
            }
            buffer.append(byte.toChar())
            if (buffer.length > MAX_LINE) {
                return null
            }
        }
    }

    private fun readBody(input: InputStream, contentLength: Int): String {
        if (contentLength <= 0 || contentLength > MAX_BODY) {
            return ""
        }
        val bytes = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(bytes, read, contentLength - read)
            if (count < 0) {
                break
            }
            read += count
        }
        return String(bytes, 0, read, StandardCharsets.UTF_8)
    }

    companion object {
        private const val BACKLOG = 8
        private const val MAX_LINE = 8_192
        private const val MAX_BODY = 1_000_000

        fun failure(message: String): JSONObject = JSONObject().put("ok", false).put("error", message)
    }
}
