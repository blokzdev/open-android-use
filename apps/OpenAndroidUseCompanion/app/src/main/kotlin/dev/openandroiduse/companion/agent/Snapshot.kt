package dev.openandroiduse.companion.agent

import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * On-device port of the Go bridge's snapshot model (apps/OpenAndroidUse,
 * main.go appSnapshot / device.go snapshotTreeBuilder). The rendered text and
 * element indexing match what the host runtimes show the model (minus process
 * ids, which Android does not expose to apps), so the 9-tool surface behaves
 * identically in both topologies.
 *
 * Coordinate invariant: everything the model sees is screenshot pixel space;
 * [AppSnapshot.coordinateScale] converts back to device pixels
 * (device_px = screenshot_px / scale).
 */
data class ElementFrame(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun renderedLocalFrame(): String =
        "{{x: ${x.roundToLong()}, y: ${y.roundToLong()}, width: ${width.roundToLong()}, height: ${height.roundToLong()}}}"

    fun centerX(): Double = x + width / 2
    fun centerY(): Double = y + height / 2

    fun scaled(scale: Double): ElementFrame =
        if (scale == 1.0) this else ElementFrame(
            (x * scale).roundToLong().toDouble(),
            (y * scale).roundToLong().toDouble(),
            (width * scale).roundToLong().toDouble(),
            (height * scale).roundToLong().toDouble(),
        )
}

data class ElementRecord(
    val index: Int = 0,
    val resourceId: String = "",
    val name: String = "",
    val controlType: String = "",
    val value: String = "",
    val frame: ElementFrame? = null,
    val actions: List<String> = emptyList(),
    val focused: Boolean = false,
    // Phase 6.5a: the framework's AccessibilityNodeInfo.isPassword flag, captured by
    // SnapshotBuilder and carried through here so the action gate can auto-decline
    // interaction with credential fields. Not rendered into the tree-line text.
    val password: Boolean = false,
    // Phase 6.5b: a payment/credit-card field, inferred by SnapshotBuilder from the
    // node's labels (autofill hints aren't exposed to accessibility). Same gate as
    // password; not rendered into the tree-line text.
    val creditCard: Boolean = false,
)

data class AppSnapshot(
    val appName: String,
    val packageName: String,
    val windowTitle: String = "",
    val screenshotPngBase64: String = "",
    val treeLines: List<String> = emptyList(),
    val elements: List<ElementRecord> = emptyList(),
    val focusedSummary: String = "",
    val coordinateScale: Double = 1.0,
    // Phase 6.5c-1: true when the screenshot was deliberately withheld because this
    // is a sensitive screen (vision mode). The redacted tree still flows; renderedText
    // tells the model why vision went dark so it doesn't loop "I can't see".
    val screenshotWithheld: Boolean = false,
) {
    /** Port of appSnapshot.renderedText() — the text the model reads. */
    fun renderedText(): String {
        val appRef = packageName.ifEmpty { appName }
        val title = windowTitle.trim().ifEmpty { appName }
        val lines = mutableListOf(
            "App=$appRef",
            "Window: \"$title\", App: $appName.",
        )
        lines.addAll(treeLines)
        if (focusedSummary.isNotBlank()) {
            lines.add("")
            lines.add("The focused UI element is $focusedSummary.")
        }
        if (screenshotWithheld) {
            lines.add("")
            lines.add(Redaction.SCREENSHOT_WITHHELD_NOTE)
        }
        return lines.joinToString("\n")
    }

    fun lookupElement(elementIndex: String): ElementRecord? {
        val index = elementIndex.trim().toIntOrNull() ?: return null
        return elements.firstOrNull { it.index == index }
    }
}

/**
 * Port of device.go snapshotTreeBuilder + flattenCompanionTree: renders the
 * protocol-v1 snapshot JSON (the exact format SnapshotBuilder emits) into the
 * indexed tree-line format shared by all runtimes.
 */
object SnapshotFlattener {

    private const val MAX_TREE_LINES = 600
    private const val MAX_ELEMENTS = 250

    data class Flattened(
        val treeLines: List<String>,
        val elements: List<ElementRecord>,
        val focusedSummary: String,
    )

    fun flatten(root: JSONObject?, scale: Double): Flattened {
        val effectiveScale = if (scale <= 0) 1.0 else scale
        val builder = TreeBuilder()
        if (root != null) {
            walk(root, 0, effectiveScale, builder)
        }
        return Flattened(builder.treeLines, builder.elements, builder.focused)
    }

    private fun walk(node: JSONObject, depth: Int, scale: Double, builder: TreeBuilder) {
        val frame = nodeFrame(node, scale)
        val include = interesting(node) && frame != null
        if (include) {
            val password = node.optBoolean("password")
            val creditCard = node.optBoolean("creditCard")
            builder.add(
                ElementRecord(
                    resourceId = node.optString("resourceId"),
                    name = label(node),
                    controlType = shortClass(node.optString("className")),
                    // Phase 6.5c-1: never store a raw secret as the value. The emitted
                    // `text` is already redacted upstream (SnapshotBuilder) and the
                    // rendered tree-line name derives from it; this is the data-model
                    // boundary backstop and does not change the line text.
                    value = Redaction.redactedValue(password, creditCard, node.optString("text")),
                    frame = frame,
                    actions = actions(node),
                    focused = node.optBoolean("focused"),
                    password = password,
                    creditCard = creditCard,
                ),
                depth,
                selected = node.optBoolean("checked") || node.optBoolean("selected"),
            )
        }
        val childDepth = if (include) depth + 1 else depth
        val children = node.optJSONArray("children") ?: return
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            walk(child, childDepth, scale, builder)
        }
    }

    private fun nodeFrame(node: JSONObject, scale: Double): ElementFrame? {
        val bounds = node.optJSONArray("bounds") ?: return null
        if (bounds.length() != 4) return null
        val left = bounds.optInt(0)
        val top = bounds.optInt(1)
        val right = bounds.optInt(2)
        val bottom = bounds.optInt(3)
        if (right <= left || bottom <= top) return null
        return ElementFrame(
            left.toDouble(),
            top.toDouble(),
            (right - left).toDouble(),
            (bottom - top).toDouble(),
        ).scaled(scale)
    }

    private fun label(node: JSONObject): String {
        val text = node.optString("text")
        if (text.isNotBlank()) return text
        return node.optString("contentDesc")
    }

    private fun shortClass(className: String): String = className.substringAfterLast('.')

    private fun actions(node: JSONObject): List<String> {
        val actions = mutableListOf<String>()
        if (node.optBoolean("clickable")) actions.add("click")
        if (node.optBoolean("longClickable")) actions.add("long-click")
        if (node.optBoolean("scrollable")) actions.add("scroll")
        if (node.optBoolean("checkable")) actions.add("check")
        if (node.optBoolean("editable")) actions.add("set_value")
        return actions
    }

    private fun interesting(node: JSONObject): Boolean {
        // SnapshotBuilder emits `enabled` only when false.
        if (node.has("enabled") && !node.optBoolean("enabled", true)) return false
        if (node.optBoolean("clickable") || node.optBoolean("longClickable") ||
            node.optBoolean("scrollable") || node.optBoolean("checkable") ||
            node.optBoolean("focused") || node.optBoolean("editable")
        ) {
            return true
        }
        return label(node).isNotBlank()
    }

    private class TreeBuilder {
        val treeLines = mutableListOf<String>()
        val elements = mutableListOf<ElementRecord>()
        var focused = ""
        private var nextIndex = 1

        fun add(record: ElementRecord, depth: Int, selected: Boolean) {
            if (treeLines.size >= MAX_TREE_LINES || record.frame == null) return
            var indexed = record
            var line = "  ".repeat(depth)
            if (record.actions.isNotEmpty() && elements.size < MAX_ELEMENTS) {
                indexed = record.copy(index = nextIndex)
                nextIndex++
                elements.add(indexed)
                line += "[${indexed.index}] "
            } else {
                line += "- "
            }
            line += indexed.controlType
            if (indexed.name.isNotEmpty()) {
                line += " \"${indexed.name}\""
            }
            if (indexed.resourceId.isNotEmpty()) {
                line += " (id: ${indexed.resourceId})"
            }
            line += " " + indexed.frame!!.renderedLocalFrame()
            if (indexed.actions.isNotEmpty()) {
                line += " [" + indexed.actions.joinToString(" ") + "]"
            }
            if (selected) {
                line += " (selected)"
            }
            treeLines.add(line)
            if (indexed.focused) {
                var summary = indexed.controlType
                if (indexed.name.isNotEmpty()) {
                    summary += " \"${indexed.name}\""
                }
                if (indexed.index > 0) {
                    summary += " (element ${indexed.index})"
                }
                focused = summary
            }
        }
    }
}
