package dev.openandroiduse.companion

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the live accessibility tree of the active window into the
 * protocol-v1 snapshot JSON (docs/design-docs/on-device-companion.md).
 * Bounds are device pixels in screen coordinates. Boolean flags are emitted
 * only when true, except `enabled`, which is emitted only when false.
 */
object SnapshotBuilder {

    private const val MAX_DEPTH = 60

    fun build(service: CompanionService): JSONObject {
        val snapshot = service.onMainThread {
            val root = service.rootInActiveWindow
                ?: return@onMainThread HttpServer.failure("no active window is available")
            JSONObject()
                .put("ok", true)
                .put("protocol", Protocol.VERSION)
                .put("package", root.packageName?.toString() ?: "")
                .put("tree", nodeJson(root, 0))
        }
        return snapshot ?: HttpServer.failure("snapshot timed out")
    }

    private fun nodeJson(node: AccessibilityNodeInfo, depth: Int): JSONObject {
        val json = JSONObject()
        json.put("className", node.className?.toString() ?: "")
        putIfNotEmpty(json, "text", node.text?.toString())
        putIfNotEmpty(json, "contentDesc", node.contentDescription?.toString())
        putIfNotEmpty(json, "resourceId", node.viewIdResourceName)

        val rect = Rect()
        node.getBoundsInScreen(rect)
        json.put("bounds", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))

        putIfTrue(json, "clickable", node.isClickable)
        putIfTrue(json, "longClickable", node.isLongClickable)
        putIfTrue(json, "scrollable", node.isScrollable)
        putIfTrue(json, "editable", node.isEditable)
        putIfTrue(json, "focusable", node.isFocusable)
        putIfTrue(json, "focused", node.isFocused)
        putIfTrue(json, "checkable", node.isCheckable)
        putIfTrue(json, "checked", node.isChecked)
        putIfTrue(json, "selected", node.isSelected)
        putIfTrue(json, "password", node.isPassword)
        if (!node.isEnabled) {
            json.put("enabled", false)
        }

        if (depth < MAX_DEPTH && node.childCount > 0) {
            val children = JSONArray()
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                children.put(nodeJson(child, depth + 1))
            }
            if (children.length() > 0) {
                json.put("children", children)
            }
        }
        return json
    }

    private fun putIfNotEmpty(json: JSONObject, key: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            json.put(key, value)
        }
    }

    private fun putIfTrue(json: JSONObject, key: String, value: Boolean) {
        if (value) {
            json.put(key, true)
        }
    }
}
