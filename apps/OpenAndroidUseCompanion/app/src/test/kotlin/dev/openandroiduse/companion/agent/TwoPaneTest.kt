package dev.openandroiduse.companion.agent

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPaneTest {

    @Test
    fun expandedWidthIsTwoPane() {
        assertTrue(TwoPane.isTwoPane(WindowWidthSizeClass.Expanded))
    }

    @Test
    fun compactAndMediumAreSinglePane() {
        assertFalse(TwoPane.isTwoPane(WindowWidthSizeClass.Compact))
        assertFalse(TwoPane.isTwoPane(WindowWidthSizeClass.Medium))
    }
}
