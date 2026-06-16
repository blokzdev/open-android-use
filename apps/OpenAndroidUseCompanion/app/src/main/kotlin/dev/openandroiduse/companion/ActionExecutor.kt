package dev.openandroiduse.companion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes protocol-v1 actions (docs/design-docs/on-device-companion.md):
 * tap / longPress / swipe via dispatchGesture, setText via ACTION_SET_TEXT on
 * the input-focused editable node (full Unicode — the capability ADB lacks),
 * and global navigation actions.
 */
object ActionExecutor {

    fun execute(service: CompanionService, body: String): JSONObject {
        val request = try {
            JSONObject(body)
        } catch (_: Exception) {
            return HttpServer.failure("action body must be a JSON object")
        }
        return when (val type = request.optString("type")) {
            "tap" -> gesture(service, request, holdMs = 50)
            "longPress" -> gesture(service, request, holdMs = 600)
            "swipe" -> swipe(service, request)
            "setText" -> setText(service, request.optString("text"))
            "global" -> global(service, request.optString("action"))
            else -> HttpServer.failure("unknown action type \"$type\"")
        }
    }

    private fun gesture(service: CompanionService, request: JSONObject, holdMs: Long): JSONObject {
        if (!request.has("x") || !request.has("y")) {
            return HttpServer.failure("tap/longPress requires x and y")
        }
        val x = request.optDouble("x").toFloat()
        val y = request.optDouble("y").toFloat()
        val path = Path().apply { moveTo(x, y) }
        return dispatch(service, path, holdMs)
    }

    private fun swipe(service: CompanionService, request: JSONObject): JSONObject {
        for (key in listOf("fromX", "fromY", "toX", "toY")) {
            if (!request.has(key)) {
                return HttpServer.failure("swipe requires fromX, fromY, toX, and toY")
            }
        }
        val duration = request.optLong("durationMs", 300).coerceIn(1, 10_000)
        val path = Path().apply {
            moveTo(request.optDouble("fromX").toFloat(), request.optDouble("fromY").toFloat())
            lineTo(request.optDouble("toX").toFloat(), request.optDouble("toY").toFloat())
        }
        return dispatch(service, path, duration)
    }

    private fun dispatch(service: CompanionService, path: Path, durationMs: Long): JSONObject {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val latch = CountDownLatch(1)
        var completed = false
        val accepted = service.onMainThread {
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        latch.countDown()
                    }
                },
                null
            )
        }
        if (accepted != true) {
            return HttpServer.failure("gesture was not accepted by the accessibility service")
        }
        if (!latch.await(durationMs + 3_000, TimeUnit.MILLISECONDS)) {
            return HttpServer.failure("gesture timed out")
        }
        if (!completed) {
            return HttpServer.failure("gesture was cancelled (is the screen interactive?)")
        }
        return ok()
    }

    private fun setText(service: CompanionService, text: String): JSONObject {
        val result = service.onMainThread {
            val root = service.rootInActiveWindow
                ?: return@onMainThread HttpServer.failure("no active window is available")
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return@onMainThread HttpServer.failure("no input-focused element; tap a text field first")
            if (!focused.isEditable) {
                return@onMainThread HttpServer.failure("the focused element is not editable")
            }
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                ok()
            } else {
                HttpServer.failure("ACTION_SET_TEXT was rejected by the focused element")
            }
        }
        return result ?: HttpServer.failure("setText timed out")
    }

    /**
     * Phase 6.5c-2b login tap-to-fill: request input focus on the first password
     * field so the platform surfaces the user's own autofill / password-manager
     * suggestion chip — the human taps it and the secret flows manager→field,
     * brokered by the OS. We only focus; we **never** read the field's text.
     * Best-effort: returns false if there is no password field or focus is refused
     * (the handoff still works — the human can type it in manually).
     */
    fun focusFirstSecureField(service: CompanionService): Boolean {
        val result = service.onMainThread {
            val root = service.rootInActiveWindow ?: return@onMainThread false
            val node = findPasswordNode(root) ?: return@onMainThread false
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        return result == true
    }

    private fun findPasswordNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isPassword && node.isEditable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findPasswordNode(child)?.let { return it }
        }
        return null
    }

    private fun global(service: CompanionService, action: String): JSONObject {
        val globalAction = when (action) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return HttpServer.failure("unknown global action \"$action\"")
        }
        val performed = service.onMainThread { service.performGlobalAction(globalAction) }
        return if (performed == true) ok() else HttpServer.failure("global action \"$action\" failed")
    }

    private fun ok(): JSONObject = JSONObject().put("ok", true)
}
