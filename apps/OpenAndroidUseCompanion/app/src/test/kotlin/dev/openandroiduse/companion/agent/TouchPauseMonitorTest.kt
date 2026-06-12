package dev.openandroiduse.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TouchPauseMonitorTest {

    private val own = "dev.openandroiduse.companion"

    @Before
    fun reset() {
        TouchPauseMonitor.reset()
    }

    @Test
    fun userTouchOutsideGestureWindowPauses() {
        TouchPauseMonitor.noteAgentAction(nowMs = 1_000)
        assertTrue(
            TouchPauseMonitor.shouldPause(
                agentRunning = true,
                eventPackage = "com.android.settings",
                ownPackage = own,
                nowMs = 1_000 + TouchPauseMonitor.AGENT_ACTION_WINDOW_MS + 1,
            ),
        )
    }

    @Test
    fun agentOwnGesturesDoNotPause() {
        TouchPauseMonitor.noteAgentAction(nowMs = 1_000)
        assertFalse(
            TouchPauseMonitor.shouldPause(
                agentRunning = true,
                eventPackage = "com.android.settings",
                ownPackage = own,
                nowMs = 1_500,
            ),
        )
    }

    @Test
    fun companionUiIsExempt() {
        TouchPauseMonitor.noteAgentAction(nowMs = 0)
        assertFalse(
            TouchPauseMonitor.shouldPause(
                agentRunning = true,
                eventPackage = own,
                ownPackage = own,
                nowMs = 100_000,
            ),
        )
    }

    @Test
    fun idleAgentNeverPauses() {
        assertFalse(
            TouchPauseMonitor.shouldPause(
                agentRunning = false,
                eventPackage = "com.android.settings",
                ownPackage = own,
                nowMs = 100_000,
            ),
        )
    }

    @Test
    fun nullPackageIsIgnored() {
        assertFalse(
            TouchPauseMonitor.shouldPause(
                agentRunning = true,
                eventPackage = null,
                ownPackage = own,
                nowMs = 100_000,
            ),
        )
    }
}
