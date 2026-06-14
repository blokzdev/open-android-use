package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteClassifierTest {

    @Test fun paused() = assertEquals(
        NoteStyle.PAUSED,
        NoteClassifier.classify("Paused — you touched the screen. Send a message to continue."),
    )

    @Test fun needsKey() = assertEquals(
        NoteStyle.NEEDS_KEY,
        NoteClassifier.classify("Add your Anthropic API key to start — opening settings."),
    )

    @Test fun needsAccessibility() = assertEquals(
        NoteStyle.NEEDS_ACCESSIBILITY,
        NoteClassifier.classify("Enable the companion accessibility service so I can see and tap the screen."),
    )

    @Test fun authError() = assertEquals(
        NoteStyle.ERROR,
        NoteClassifier.classify("Authentication failed (401)."),
    )

    @Test fun genericInfo() = assertEquals(
        NoteStyle.INFO,
        NoteClassifier.classify("The agent sees the screen and acts through the accessibility service."),
    )
}
