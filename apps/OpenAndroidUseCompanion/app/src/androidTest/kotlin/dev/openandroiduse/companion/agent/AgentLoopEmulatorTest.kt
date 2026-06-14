package dev.openandroiduse.companion.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.openandroiduse.companion.CompanionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end agent loop smoke on a real emulator (Milestone 4 of the Phase 3
 * plan): the production loop — SDK streaming, accumulation, ToolExecutor,
 * real accessibility snapshot and screenshot — driven against an on-device
 * stub model server. No API key, no network egress.
 *
 * Precondition: the companion accessibility service is enabled (the CI smoke
 * script does this via adb settings before the test runs). Skipped otherwise.
 */
@RunWith(AndroidJUnit4::class)
class AgentLoopEmulatorTest {

    /**
     * `am instrument` force-restarts the app process, which unbinds the
     * enabled accessibility service — and the accessibility manager does not
     * rebind it into the instrumented process on its own. Toggling the secure
     * setting off and back on (via shell, with DONT_SUPPRESS so UiAutomation
     * does not displace other services) forces a rebind into this process.
     */
    private fun ensureServiceRunning(): Boolean {
        if (awaitService(5_000)) return true
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String) {
            automation.executeShellCommand(command).use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).readBytes()
            }
        }
        val component = "dev.openandroiduse.companion/dev.openandroiduse.companion.CompanionService"
        shell("settings delete secure enabled_accessibility_services")
        Thread.sleep(1_000)
        shell("settings put secure enabled_accessibility_services $component")
        shell("settings put secure accessibility_enabled 1")
        return awaitService(30_000)
    }

    private fun awaitService(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!CompanionService.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        return CompanionService.isRunning
    }

    @Test
    fun agentLoopExecutesToolTurnAgainstStubModel() {
        // With -e requireCompanion true (CI), an absent service is a failure —
        // never a vacuous skip.
        val running = ensureServiceRunning()
        val required = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("requireCompanion") == "true"
        if (required) {
            assertTrue("companion accessibility service must be running in CI", running)
        } else {
            assumeTrue(
                "companion accessibility service must be enabled (CI smoke enables it)",
                running,
            )
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stub = StubModelServer()
        stub.start()
        try {
            // Self-check before driving the loop: the stub must be reachable
            // at the exact address the SDK client will dial.
            java.net.Socket().use { probe ->
                try {
                    probe.connect(java.net.InetSocketAddress("127.0.0.1", stub.port), 3_000)
                } catch (e: Exception) {
                    org.junit.Assert.fail("stub server not reachable on 127.0.0.1:${stub.port}: $e")
                }
            }

            val settings = AgentSettings(context)
            settings.storeApiKey("stub-key-for-emulator-smoke")
            settings.baseUrlOverride = "http://127.0.0.1:${stub.port}"
            settings.confirmActions = false
            settings.speakNarration = false

            val finished = CountDownLatch(1)
            AgentController.resetConversation()
            AgentController.listener = object : AgentController.Listener {
                override fun onTaskStateChanged(running: Boolean) {
                    if (!running) finished.countDown()
                }

                override fun onTranscriptChanged() {}
            }

            assertTrue(
                "task should start",
                AgentController.startTask("Look at the current screen.", settings),
            )
            assertTrue(
                "agent loop should finish within 90s",
                finished.await(90, TimeUnit.SECONDS),
            )

            val transcript = AgentController.transcriptSnapshot()
            // Surface the loop's own error notes in the CI log and in every
            // assertion message — the transcript is where failures land.
            val dump = transcript.joinToString("\n") { "[${it.kind}] ${it.text.take(300)}" }
            println("AGENT TRANSCRIPT:\n$dump")
            assertTrue(
                "get_app_state should have executed; transcript:\n$dump",
                transcript.any { it.kind == AgentController.KIND_TOOL && it.text.contains("get_app_state") },
            )
            assertEquals("stub should have served two turns; transcript:\n$dump", 2, stub.requestBodies.size)

            val first = stub.requestBodies[0]
            assertTrue("first request must carry the 9-tool schema", first.contains("\"get_app_state\""))
            assertTrue("first request must carry the frozen system prompt prefix",
                first.contains("Open Android Use companion agent"))
            assertTrue("adaptive thinking must be on", first.contains("\"adaptive\""))

            val second = stub.requestBodies[1]
            assertTrue("second request must return the tool result", second.contains("\"tool_result\""))
            assertTrue("tool result must reference the stub tool_use id", second.contains("toolu_stub_1"))
            assertTrue("tool result should include a screenshot image block",
                second.contains("\"image/png\""))

            assertTrue(
                "transcript should contain the final narration",
                transcript.any { it.kind == AgentController.KIND_ASSISTANT && it.text.contains("Done") },
            )
        } finally {
            AgentController.listener = null
            stub.shutdown()
            // Never leave the loopback override or stub key behind on a real
            // device that later runs the agent for real.
            val settings = AgentSettings(ApplicationProvider.getApplicationContext())
            settings.baseUrlOverride = null
            settings.clearApiKey()
        }
    }
}
