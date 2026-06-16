package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDiffTest {

    private fun snap(
        pkg: String = "com.android.settings",
        title: String = "Settings",
        tree: List<String> = listOf("a", "b"),
        elements: Int = 2,
        focus: String = "",
    ) = AppSnapshot(
        appName = "Settings",
        packageName = pkg,
        windowTitle = title,
        treeLines = tree,
        elements = (0 until elements).map { ElementRecord(index = it) },
        focusedSummary = focus,
    )

    @Test
    fun nullPreviousIsChangedWithNoSummary() {
        val r = SnapshotDiff.summarize(null, snap())
        assertTrue(r.changed)
        assertEquals("", r.summary)
    }

    @Test
    fun identicalScreenIsUnchanged() {
        val r = SnapshotDiff.summarize(snap(), snap())
        assertFalse(r.changed)
        assertEquals("Screen unchanged.", r.summary)
    }

    @Test
    fun addedElementsReported() {
        val r = SnapshotDiff.summarize(snap(elements = 2), snap(tree = listOf("a", "b", "c"), elements = 3))
        assertTrue(r.changed)
        assertTrue(r.summary.contains("+1 elements"))
    }

    @Test
    fun removedElementsReported() {
        val r = SnapshotDiff.summarize(snap(elements = 3, tree = listOf("a", "b", "c")), snap(elements = 1, tree = listOf("a")))
        assertTrue(r.changed)
        assertTrue(r.summary.contains("-2 elements"))
    }

    @Test
    fun focusChangeReportedEvenWhenTreeEqual() {
        val r = SnapshotDiff.summarize(snap(focus = ""), snap(focus = "the Email field"))
        assertTrue(r.changed)
        assertTrue(r.summary.contains("focus → the Email field"))
    }

    @Test
    fun windowChangeReported() {
        val r = SnapshotDiff.summarize(snap(title = "Settings"), snap(pkg = "com.android.chrome", title = "Chrome"))
        assertTrue(r.changed)
        assertTrue(r.summary.contains("window → 'Chrome'"))
    }

    @Test
    fun treeContentChangeWithoutCountChangeIsChanged() {
        // Same element count / focus / window, but a label changed: still "changed", no detail.
        val r = SnapshotDiff.summarize(snap(tree = listOf("a", "b")), snap(tree = listOf("a", "B!")))
        assertTrue(r.changed)
        assertEquals("Screen changed.", r.summary)
    }
}
