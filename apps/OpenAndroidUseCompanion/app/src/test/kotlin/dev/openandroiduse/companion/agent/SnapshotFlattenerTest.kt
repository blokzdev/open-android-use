package dev.openandroiduse.companion.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun sensitiveFlagsPassThroughFlatten() {
        val tree = JSONObject(
            """
            {
              "className": "android.widget.FrameLayout",
              "bounds": [0, 0, 1080, 2400],
              "children": [
                {
                  "className": "android.widget.EditText",
                  "bounds": [0, 0, 980, 80],
                  "editable": true,
                  "password": true
                },
                {
                  "className": "android.widget.EditText",
                  "bounds": [0, 100, 980, 180],
                  "editable": true,
                  "creditCard": true
                }
              ]
            }
            """,
        )
        val flattened = SnapshotFlattener.flatten(tree, 1.0)
        assertEquals(2, flattened.elements.size)
        assertTrue(flattened.elements[0].password)
        assertTrue(flattened.elements[1].creditCard)
    }

    @Test
    fun redactedValueSurvivesAsTreeTextWhileStructureRemains() {
        // The JSON as SnapshotBuilder emits it for a payment screen: the card field's
        // value is already "[redacted]" at the wire; a non-secret sibling is untouched.
        val tree = JSONObject(
            """
            {
              "className": "android.widget.FrameLayout",
              "bounds": [0, 0, 1080, 2400],
              "children": [
                {
                  "className": "android.widget.EditText",
                  "text": "[redacted]",
                  "resourceId": "com.shop:id/card_number",
                  "bounds": [0, 0, 980, 80],
                  "editable": true,
                  "creditCard": true
                },
                {
                  "className": "android.widget.EditText",
                  "text": "Jane Buyer",
                  "resourceId": "com.shop:id/cardholder",
                  "bounds": [0, 100, 980, 180],
                  "editable": true
                }
              ]
            }
            """,
        )
        val flattened = SnapshotFlattener.flatten(tree, 1.0)
        val rendered = flattened.treeLines.joinToString("\n")

        // No raw card digits anywhere; the redaction marker and the structure remain.
        assertFalse(rendered.contains("4111"))
        assertTrue(rendered.contains("[redacted]"))
        assertTrue(rendered.contains("com.shop:id/card_number"))
        assertTrue(rendered.contains("set_value"))
        // The non-secret sibling value still renders as its label.
        assertTrue(rendered.contains("Jane Buyer"))
        assertEquals("[redacted]", flattened.elements[0].value)
    }

    @Test
    fun flattenValueBackstopRedactsEvenRawSecretText() {
        // Defense in depth: even if a record were built from un-redacted JSON, the
        // ElementRecord value must never carry the raw secret.
        val tree = JSONObject(
            """
            {
              "className": "android.widget.EditText",
              "text": "4111111111111111",
              "bounds": [0, 0, 980, 80],
              "editable": true,
              "creditCard": true
            }
            """,
        )
        val flattened = SnapshotFlattener.flatten(tree, 1.0)
        assertEquals(1, flattened.elements.size)
        assertEquals("[redacted]", flattened.elements[0].value)
    }

    @Test
    fun withheldScreenshotAddsModelFacingNote() {
        val withheld = AppSnapshot(
            appName = "Shop",
            packageName = "com.shop",
            treeLines = listOf("[1] EditText \"[redacted]\" (id: com.shop:id/card_number) {{x: 0, y: 0, width: 980, height: 80}} [set_value]"),
            screenshotWithheld = true,
        )
        assertTrue(withheld.renderedText().contains(Redaction.SCREENSHOT_WITHHELD_NOTE))

        val normal = withheld.copy(screenshotWithheld = false)
        assertFalse(normal.renderedText().contains(Redaction.SCREENSHOT_WITHHELD_NOTE))
    }
}
