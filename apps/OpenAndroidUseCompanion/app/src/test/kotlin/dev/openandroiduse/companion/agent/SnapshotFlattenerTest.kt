package dev.openandroiduse.companion.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the Go bridge's flattenCompanionTree tests: the same protocol-v1
 * snapshot JSON must produce the same indexed tree lines on-device.
 */
class SnapshotFlattenerTest {

    private fun fixture(): JSONObject = JSONObject(
        """
        {
          "className": "android.widget.FrameLayout",
          "bounds": [0, 0, 1080, 2400],
          "children": [
            {
              "className": "android.widget.Button",
              "text": "OK",
              "resourceId": "com.example:id/ok",
              "bounds": [100, 200, 300, 280],
              "clickable": true
            },
            {
              "className": "android.widget.EditText",
              "text": "hello",
              "bounds": [100, 400, 980, 480],
              "editable": true,
              "focused": true
            },
            {
              "className": "android.view.View",
              "bounds": [0, 500, 1080, 600]
            }
          ]
        }
        """,
    )

    @Test
    fun indexesActionableElementsAndSkipsBoringOnes() {
        val flattened = SnapshotFlattener.flatten(fixture(), 1.0)
        assertEquals(2, flattened.elements.size)
        assertEquals(1, flattened.elements[0].index)
        assertEquals("Button", flattened.elements[0].controlType)
        assertEquals(listOf("click"), flattened.elements[0].actions)
        assertEquals(2, flattened.elements[1].index)
        assertEquals(listOf("set_value"), flattened.elements[1].actions)
        // Root has no label/actions → not included; bare View skipped.
        assertEquals(2, flattened.treeLines.size)
    }

    @Test
    fun rendersTreeLinesInBridgeFormat() {
        val flattened = SnapshotFlattener.flatten(fixture(), 1.0)
        assertEquals(
            "[1] Button \"OK\" (id: com.example:id/ok) {{x: 100, y: 200, width: 200, height: 80}} [click]",
            flattened.treeLines[0],
        )
    }

    @Test
    fun reportsFocusedElement() {
        val flattened = SnapshotFlattener.flatten(fixture(), 1.0)
        assertEquals("EditText \"hello\" (element 2)", flattened.focusedSummary)
    }

    @Test
    fun scalesFramesIntoScreenshotPixelSpace() {
        val flattened = SnapshotFlattener.flatten(fixture(), 0.5)
        val frame = flattened.elements[0].frame!!
        assertEquals(50.0, frame.x, 0.001)
        assertEquals(100.0, frame.y, 0.001)
        assertEquals(100.0, frame.width, 0.001)
        assertEquals(40.0, frame.height, 0.001)
    }

    @Test
    fun disabledNodesAreExcluded() {
        val tree = JSONObject(
            """{"className": "android.widget.Button", "text": "Off", "bounds": [0,0,10,10],
                "clickable": true, "enabled": false}""",
        )
        val flattened = SnapshotFlattener.flatten(tree, 1.0)
        assertTrue(flattened.elements.isEmpty())
    }

    @Test
    fun renderedTextMatchesBridgeShape() {
        val flattened = SnapshotFlattener.flatten(fixture(), 1.0)
        val snapshot = AppSnapshot(
            appName = "Example",
            packageName = "com.example",
            windowTitle = "Example",
            treeLines = flattened.treeLines,
            elements = flattened.elements,
            focusedSummary = flattened.focusedSummary,
        )
        val text = snapshot.renderedText()
        assertTrue(text.startsWith("App=com.example\nWindow: \"Example\", App: Example."))
        assertTrue(text.endsWith("The focused UI element is EditText \"hello\" (element 2)."))
    }

    @Test
    fun lookupElementResolvesByIndex() {
        val flattened = SnapshotFlattener.flatten(fixture(), 1.0)
        val snapshot = AppSnapshot(
            appName = "Example",
            packageName = "com.example",
            elements = flattened.elements,
        )
        assertEquals("Button", snapshot.lookupElement("1")?.controlType)
        assertNull(snapshot.lookupElement("99"))
        assertNull(snapshot.lookupElement("not-a-number"))
    }
}
