package dev.openandroiduse.companion

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.openandroiduse.companion.agent.Redaction
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

        // Phase 6.5c-1 (L0 redaction): emit the field VALUE through the redaction
        // policy so a password/payment field's contents never reach the model — not
        // in the on-device tree and not in the host bridge, which reads this same
        // JSON. contentDesc/resourceId are field LABELS, not values, so they stay.
        val password = node.isPassword
        val creditCard = isAutofillCreditCard(node)
        Redaction.emittedText(password, creditCard, node.text?.toString())
            ?.let { json.put("text", it) }
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
        putIfTrue(json, "password", password)
        putIfTrue(json, "creditCard", creditCard)
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

    // Phase 6.5b: payment-field heuristic. AccessibilityNodeInfo does not expose
    // autofill hints (those reach only the autofill ViewStructure), so we match the
    // node's visible labels against a tight, card-specific token set. Scoped to tokens
    // that essentially never appear off a payment form (no bare "card"/"expiry") to
    // keep false positives near-zero; camelCase ids like "cardNumber" are split first.
    private val PAYMENT_TOKENS = Regex(
        "\\b(cvv|cvc|csc|card number|credit card|debit card|security code|card verification)\\b",
    )

    private fun isAutofillCreditCard(node: AccessibilityNodeInfo): Boolean =
        looksLikePaymentLabel(
            listOfNotNull(
                node.hintText?.toString(),
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName,
            ).joinToString(" "),
        )

    /** Pure label heuristic, split out for unit testing (no Android types). */
    internal fun looksLikePaymentLabel(raw: String): Boolean {
        if (raw.isBlank()) return false
        val normalized = raw
            .replace(Regex("([a-z])([A-Z])"), "$1 $2") // split camelCase before lowercasing
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
        return PAYMENT_TOKENS.containsMatchIn(normalized)
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
