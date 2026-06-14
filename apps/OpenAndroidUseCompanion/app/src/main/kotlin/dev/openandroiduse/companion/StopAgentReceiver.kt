package dev.openandroiduse.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.openandroiduse.companion.agent.AgentController

/** Backs the "Stop" action on the agent-activity notification. */
class StopAgentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AgentController.requestStop()
    }
}
