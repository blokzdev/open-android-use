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

    @Test
    fun agentLoopExecutesToolTurnAgainstStubModel() {
        // The test APK install rebinds the already-enabled service; give it a
        // moment. With -e requireCompanion true (CI), absence is a failure —
        // never a vacuous skip.
        val deadline = System.currentTimeMillis() + 30_000
        while (!CompanionService.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        val required = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("requireCompanion") == "true"
        if (required) {
            assertTrue("companion accessibility service must be running in CI", CompanionService.isRunning)
        } else {
            assumeTrue(
                "companion accessibility service must be enabled (CI smoke enables it)",
                CompanionService.isRunning,
            )
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stub = StubModelServer()
        stub.start()
        try {
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
            assertTrue(
                "get_app_state should have executed",
                transcript.any { it.first == AgentController.KIND_TOOL && it.second.contains("get_app_state") },
            )
            assertEquals("stub should have served two turns", 2, stub.requestBodies.size)

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
                transcript.any { it.first == AgentController.KIND_ASSISTANT && it.second.contains("Done") },
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
