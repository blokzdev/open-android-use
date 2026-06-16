package dev.openandroiduse.companion.agent

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import dev.openandroiduse.companion.ActionExecutor
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.SnapshotBuilder
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Executes the 9-tool Computer Use surface in-process: snapshots come from
 * SnapshotBuilder + ScreenCapture, gestures go through ActionExecutor — the
 * same CI-verified paths the loopback endpoint serves to host runtimes.
 *
 * Semantics mirror the Go bridge's service/adbBridge pair (apps/OpenAndroidUse
 * main.go + device.go): snapshot-before-action enforcement, element_index
 * resolution against the last snapshot, screenshot-pixel coordinates divided
 * by CoordinateScale, an 800ms settle delay, and a fresh snapshot returned
 * after every action.
 */
class ToolExecutor(
    private val service: CompanionService,
    // Phase 5.6 adaptive perception: when false, snapshots are tree-text only —
    // no screenshot is captured/encoded/sent (faster, cheaper, more private).
    private val captureScreenshots: Boolean = true,
    // Phase 6.5: when true (default), action tools auto-decline on a password/payment
    // screen. The user can disable it in Privacy settings; defaults true so a missed
    // injection fails safe (guard on).
    private val sensitiveScreenGuard: Boolean = true,
) {

    data class Outcome(
        val text: String,
        val screenshotPngBase64: String? = null,
        val isError: Boolean = false,
        // Phase 6.3b: whether an action visibly changed the screen (null for reads/errors),
        // read by AgentController for no-progress / stuck detection.
        val changed: Boolean? = null,
    )

    private val snapshots = mutableMapOf<String, AppSnapshot>()
    private var cachedLaunchableApps: List<Pair<String, String>>? = null

    // Latest captured screenshot's scale + pixel dimensions, used to map a
    // dispatched gesture point into a normalized fraction for the chat's marker.
    private var lastScale = 1.0
    private var lastShotWidth = 0
    private var lastShotHeight = 0

    /** Where the last gesture landed, normalized 0..1 over the screenshot; read by AgentController. */
    @Volatile
    var lastTapNormalized: Pair<Float, Float>? = null
        private set

    private val gestureMarks = mutableListOf<GestureMark>()

    /** The current action's gestures in normalized screenshot space; read by AgentController. */
    val lastGesturesNormalized: List<GestureMark> get() = gestureMarks.toList()

    @Volatile
    private var lastActionChanged: Boolean? = null

    /** Whether the most recent tool call was an action that changed the screen (null = no action). */
    val actionChanged: Boolean? get() = lastActionChanged

    fun callTool(name: String, args: JSONObject): Outcome {
        gestureMarks.clear()
        lastActionChanged = null
        // Phase 6.5: never act on a password/payment screen (unless the user turned the
        // guard off). Action tools gate on the last snapshot for this app; reads stay
        // open so the agent can still perceive, narrate, and ask the human to enter the
        // secret themselves.
        if (sensitiveScreenGuard && name in ACTION_TOOLS) {
            val snapshot = current(args.optString("app"))
            if (snapshot != null && SensitiveScreenDetector.isSensitive(snapshot)) {
                return error(SensitiveScreenDetector.REASON_SENSITIVE)
            }
        }
        return try {
            when (name) {
                "list_apps" -> listApps()
                "get_app_state" -> getAppState(args.optString("app"))
                "click" -> click(args)
                "perform_secondary_action" -> performSecondaryAction(args)
                "scroll" -> scroll(args)
                "drag" -> drag(args)
                "type_text" -> typeText(args)
                "press_key" -> pressKey(args)
                "set_value" -> setValue(args)
                else -> error("unsupportedTool(\"$name\")")
            }
        } catch (failure: Exception) {
            error("Tool $name failed: ${failure.message}")
        }
    }

    // --- read tools ---

    private fun listApps(): Outcome {
        val apps = launchableApps()
        if (apps.isEmpty()) {
            return error("No launchable apps are visible to this Android runtime.")
        }
        val lines = apps
            .sortedBy { it.second.lowercase() }
            .map { (pkg, label) -> "$label — $pkg" }
        return Outcome(lines.joinToString("\n"))
    }

    private fun getAppState(app: String): Outcome {
        if (app.isBlank()) return error("Missing required argument: app")
        if (!ScreenCapture.isSupported()) {
            return error("The on-device agent requires Android 11 or newer for screenshots.")
        }
        val pkg = resolveAndForeground(app) ?: return error(
            "Could not find or open \"$app\". Use list_apps to see what is installed, " +
                "or \"foreground\" for the current screen.",
        )
        val snapshot = capture(pkg) ?: return error("Could not capture the screen state.")
        remember(app, snapshot)
        return snapshot.outcome()
    }

    // --- action tools ---

    private fun click(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        val elementIndex = args.optString("element_index")
        if (elementIndex.isNotBlank() && snapshot.lookupElement(elementIndex) == null) {
            return error("unknown element_index \"$elementIndex\"")
        }
        val point = targetPoint(args, snapshot) ?: return error("click requires either element_index or x/y")
        val (x, y) = point
        val button = args.optString("mouse_button").ifBlank { "left" }.lowercase()
        when (button) {
            "left" -> {
                val count = args.optInt("click_count", 1).coerceAtLeast(1)
                for (tap in 0 until count) {
                    if (tap > 0) sleep(120)
                    perform(JSONObject().put("type", "tap").put("x", x).put("y", y))
                        ?.let { return it }
                }
            }
            "right" -> perform(JSONObject().put("type", "longPress").put("x", x).put("y", y))
                ?.let { return it }
            else -> return error("mouse_button \"$button\" is not supported on Android. Use left, or right for a long-press.")
        }
        return settleAndSnapshot(app, snapshot)
    }

    private fun performSecondaryAction(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        val element = snapshot.lookupElement(args.optString("element_index"))
            ?: return error("unknown element_index \"${args.optString("element_index")}\"")
        val action = args.optString("action").trim().lowercase()
        if (action !in listOf("long-click", "long_click", "longclick")) {
            return error("\"${args.optString("action")}\" is not a valid secondary action for element ${element.index}. Available: long-click.")
        }
        if ("long-click" !in element.actions) {
            return error("Element ${element.index} does not expose a long-click action.")
        }
        val (x, y) = elementPoint(element.frame!!, snapshot.coordinateScale)
        perform(JSONObject().put("type", "longPress").put("x", x).put("y", y))?.let { return it }
        return settleAndSnapshot(app, snapshot)
    }

    private fun scroll(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        val direction = args.optString("direction").lowercase()
        if (direction !in listOf("up", "down", "left", "right")) {
            return error("Invalid scroll direction: ${args.optString("direction")}")
        }
        val element = snapshot.lookupElement(args.optString("element_index"))
            ?: return error("unknown element_index \"${args.optString("element_index")}\"")
        var pages = args.optDouble("pages", 1.0)
        if (pages <= 0) return error("pages must be > 0")
        val frame = element.frame!!
        var swipes = 0
        while (pages > 0 && swipes < MAX_SCROLL_SWIPES) {
            val fraction = min(pages, 1.0)
            val swipe = scrollSwipe(frame, snapshot.coordinateScale, direction, fraction)
            perform(swipe.put("type", "swipe").put("durationMs", 300))?.let { return it }
            pages -= fraction
            swipes++
            if (pages > 0) sleep(250)
        }
        return settleAndSnapshot(app, snapshot)
    }

    private fun drag(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        for (key in listOf("from_x", "from_y", "to_x", "to_y")) {
            if (!args.has(key)) return error("Missing required argument: $key")
        }
        val scale = snapshot.coordinateScale
        val swipe = JSONObject()
            .put("type", "swipe")
            .put("fromX", deviceCoordinate(args.getDouble("from_x"), scale))
            .put("fromY", deviceCoordinate(args.getDouble("from_y"), scale))
            .put("toX", deviceCoordinate(args.getDouble("to_x"), scale))
            .put("toY", deviceCoordinate(args.getDouble("to_y"), scale))
            .put("durationMs", 800)
        perform(swipe)?.let { return it }
        return settleAndSnapshot(app, snapshot)
    }

    private fun typeText(args: JSONObject): Outcome {
        val app = args.optString("app")
        val text = args.optString("text")
        if (text.isEmpty()) return error("Missing required argument: text")
        val snapshot = current(app) ?: return needsSnapshot(app)
        perform(JSONObject().put("type", "setText").put("text", text))?.let { return it }
        return settleAndSnapshot(app, snapshot)
    }

    private fun setValue(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        val element = snapshot.lookupElement(args.optString("element_index"))
            ?: return error("unknown element_index \"${args.optString("element_index")}\"")
        if ("set_value" !in element.actions) {
            return error("Element ${element.index} is not a settable text element.")
        }
        val (x, y) = elementPoint(element.frame!!, snapshot.coordinateScale)
        perform(JSONObject().put("type", "tap").put("x", x).put("y", y))?.let { return it }
        sleep(250)
        perform(JSONObject().put("type", "setText").put("text", args.optString("value")))
            ?.let { return it }
        return settleAndSnapshot(app, snapshot)
    }

    private fun pressKey(args: JSONObject): Outcome {
        val app = args.optString("app")
        val snapshot = current(app) ?: return needsSnapshot(app)
        when (val action = KeyMapper.map(args.optString("key"))) {
            is KeyMapper.KeyAction.Back -> perform(JSONObject().put("type", "global").put("action", "back"))
            is KeyMapper.KeyAction.Home -> perform(JSONObject().put("type", "global").put("action", "home"))
            is KeyMapper.KeyAction.Recents -> perform(JSONObject().put("type", "global").put("action", "recents"))
            is KeyMapper.KeyAction.Notifications -> perform(JSONObject().put("type", "global").put("action", "notifications"))
            is KeyMapper.KeyAction.ImeEnter -> imeEnter()
            is KeyMapper.KeyAction.DeleteBackward -> deleteBackward()
            is KeyMapper.KeyAction.Unsupported -> return error(action.message)
        }?.let { return it }
        return settleAndSnapshot(app, snapshot)
    }

    private fun imeEnter(): Outcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return error("Return/Enter requires Android 11 or newer.")
        }
        // Stamp so the resulting TYPE_VIEW_TEXT_CHANGED/CLICKED from the target
        // app is recognized as agent-initiated, not a user touch.
        TouchPauseMonitor.noteAgentAction()
        val performed = service.onMainThread {
            val focused = service.rootInActiveWindow
                ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return@onMainThread false
            focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        TouchPauseMonitor.noteAgentAction()
        return if (performed == true) null else error("Could not press Enter: no focused field accepted the IME action.")
    }

    private fun deleteBackward(): Outcome? {
        TouchPauseMonitor.noteAgentAction()
        val result = service.onMainThread {
            val focused = service.rootInActiveWindow
                ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return@onMainThread "no input-focused element"
            if (!focused.isEditable) return@onMainThread "the focused element is not editable"
            val text = focused.text?.toString() ?: ""
            if (text.isEmpty()) return@onMainThread null
            val arguments = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text.dropLast(1),
                )
            }
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) null
            else "ACTION_SET_TEXT was rejected by the focused element"
        }
        TouchPauseMonitor.noteAgentAction()
        return when (result) {
            null -> null
            else -> error("Could not delete: $result")
        }
    }

    // --- shared plumbing ---

    /** Runs an ActionExecutor action; returns an error Outcome on failure, null on success. */
    private fun perform(action: JSONObject): Outcome? {
        TouchPauseMonitor.noteAgentAction()
        when (action.optString("type")) {
            "tap", "longPress" -> {
                val x = action.optInt("x")
                val y = action.optInt("y")
                GestureTrail.tap(x, y)
                noteTap(x, y)
                addGesture(GestureMark.tap(x, y, lastScale, lastShotWidth, lastShotHeight))
            }
            "swipe" -> {
                val fromX = action.optInt("fromX")
                val fromY = action.optInt("fromY")
                val toX = action.optInt("toX")
                val toY = action.optInt("toY")
                GestureTrail.swipe(fromX, fromY, toX, toY)
                noteTap(toX, toY)
                addGesture(GestureMark.swipe(fromX, fromY, toX, toY, lastScale, lastShotWidth, lastShotHeight))
            }
        }
        val result = ActionExecutor.execute(service, action.toString())
        TouchPauseMonitor.noteAgentAction()
        if (result.optBoolean("ok")) return null
        return error(result.optString("error", "action failed"))
    }

    /** Adds a gesture to the current action's overlay list, when a screenshot exists. */
    private fun addGesture(mark: GestureMark) {
        if (lastShotWidth <= 0 || lastShotHeight <= 0) return
        gestureMarks.add(mark)
    }

    /** Records where a gesture landed (device px) as a normalized screenshot fraction. */
    private fun noteTap(deviceX: Int, deviceY: Int) {
        if (lastShotWidth <= 0 || lastShotHeight <= 0) return
        lastTapNormalized = TapHighlight.devicePixelToNormalized(
            deviceX, deviceY, lastScale, lastShotWidth, lastShotHeight,
        )
    }

    private fun settleAndSnapshot(app: String, previous: AppSnapshot): Outcome {
        sleep(SETTLE_DELAY_MS)
        val pkg = foregroundPackage() ?: previous.packageName
        val snapshot = capture(pkg) ?: return error("Action performed, but the new screen state could not be captured.")
        remember(app, snapshot)
        val diff = SnapshotDiff.summarize(previous, snapshot)
        lastActionChanged = diff.changed
        val base = snapshot.renderedText()
        val text = if (diff.summary.isEmpty()) base else "${diff.summary}\n\n$base"
        return Outcome(text, snapshot.screenshotPngBase64.ifEmpty { null }, changed = diff.changed)
    }

    private fun capture(pkg: String): AppSnapshot? {
        // Text-only perception: build the a11y tree at scale 1.0 (device-pixel
        // bounds == "screenshot" space, so element_index/x-y coords stay
        // consistent) and skip the screenshot capture/downscale entirely.
        if (!captureScreenshots) {
            val tree = SnapshotBuilder.build(service)
            if (!tree.optBoolean("ok")) return null
            val snapshotPkg = tree.optString("package").ifEmpty { pkg }
            val flattened = SnapshotFlattener.flatten(tree.optJSONObject("tree"), 1.0)
            lastScale = 1.0
            lastShotWidth = 0
            lastShotHeight = 0
            return AppSnapshot(
                appName = packageLabel(snapshotPkg),
                packageName = snapshotPkg,
                screenshotPngBase64 = "",
                treeLines = flattened.treeLines,
                elements = flattened.elements,
                focusedSummary = flattened.focusedSummary,
                coordinateScale = 1.0,
            )
        }
        val bitmap = ScreenCapture.capture(service) ?: return null
        val downscaled = try {
            ImageBudget.downscale(bitmap)
        } finally {
            bitmap.recycle()
        }
        lastScale = downscaled.scale
        lastShotWidth = downscaled.width
        lastShotHeight = downscaled.height
        val tree = SnapshotBuilder.build(service)
        if (!tree.optBoolean("ok")) return null
        val snapshotPkg = tree.optString("package").ifEmpty { pkg }
        val flattened = SnapshotFlattener.flatten(tree.optJSONObject("tree"), downscaled.scale)
        return AppSnapshot(
            // Android gives apps no window title; renderedText() falls back to
            // appName, so leave windowTitle empty rather than duplicate the label.
            appName = packageLabel(snapshotPkg),
            packageName = snapshotPkg,
            screenshotPngBase64 = Base64.encodeToString(downscaled.png, Base64.NO_WRAP),
            treeLines = flattened.treeLines,
            elements = flattened.elements,
            focusedSummary = flattened.focusedSummary,
            coordinateScale = downscaled.scale,
        )
    }

    private fun resolveAndForeground(app: String): String? {
        val query = app.trim()
        val foreground = foregroundPackage()
        if (query.equals("foreground", ignoreCase = true) || query.isEmpty()) {
            return foreground
        }
        val resolved = matchPackage(query) ?: return null
        if (resolved == foreground) return resolved
        val launch = service.packageManager.getLaunchIntentForPackage(resolved) ?: return resolved
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(launch)
        } catch (_: Exception) {
            // Background-start restrictions may apply; the snapshot will show
            // whatever is actually on screen and the model adapts.
        }
        for (attempt in 0 until 10) {
            if (foregroundPackage() == resolved) break
            sleep(300)
        }
        return resolved
    }

    private fun matchPackage(query: String): String? {
        val candidates = launchableApps()
        val lowered = query.lowercase()
        return candidates.firstOrNull { it.first.equals(query, ignoreCase = true) }?.first
            ?: candidates.firstOrNull { it.second.equals(query, ignoreCase = true) }?.first
            ?: candidates.firstOrNull { it.second.lowercase().contains(lowered) }?.first
            ?: candidates.firstOrNull { it.first.lowercase().contains(lowered) }?.first
    }

    /**
     * (packageName, label) for every launchable app, cached for this executor's
     * lifetime (one per task) — the query is an IPC plus a label load per app,
     * and both list_apps and app resolution hit it every turn.
     */
    private fun launchableApps(): List<Pair<String, String>> {
        cachedLaunchableApps?.let { return it }
        val pm = service.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
        cachedLaunchableApps = apps
        return apps
    }

    private fun foregroundPackage(): String? =
        service.onMainThread { service.rootInActiveWindow?.packageName?.toString() }

    private fun packageLabel(pkg: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast('.')
    }

    private fun current(app: String): AppSnapshot? = snapshots[app.trim().lowercase()]

    /**
     * Phase 6.1: a label-resolved, human-readable summary of an action for the
     * consent sheet, tool log, and transcript — resolves `element_index` to its
     * label via the relevant app's most-recent snapshot.
     */
    fun describeAction(name: String, args: JSONObject): String =
        ActionSummary.describe(current(args.optString("app")), name, args)

    private fun remember(query: String, snapshot: AppSnapshot) {
        // Keyed by query/label/package only — matching the Go bridge. We do NOT
        // alias under "foreground": a later app:"foreground" action must take a
        // fresh snapshot rather than resolve a stale one for whatever app moved
        // to the foreground.
        for (key in listOf(query, snapshot.appName, snapshot.packageName)) {
            val normalized = key.trim().lowercase()
            if (normalized.isNotEmpty()) {
                snapshots[normalized] = snapshot
            }
        }
    }

    private fun needsSnapshot(app: String): Outcome =
        error("No app state is available for $app. Run get_app_state before action tools.")

    private fun targetPoint(args: JSONObject, snapshot: AppSnapshot): Pair<Int, Int>? {
        val elementIndex = args.optString("element_index")
        if (elementIndex.isNotBlank()) {
            val frame = snapshot.lookupElement(elementIndex)?.frame ?: return null
            return elementPoint(frame, snapshot.coordinateScale)
        }
        if (!args.has("x") || !args.has("y")) return null
        return deviceCoordinate(args.getDouble("x"), snapshot.coordinateScale) to
            deviceCoordinate(args.getDouble("y"), snapshot.coordinateScale)
    }

    /** Element center in device pixels (the CoordinateScale invariant, one place). */
    private fun elementPoint(frame: ElementFrame, scale: Double): Pair<Int, Int> =
        deviceCoordinate(frame.centerX(), scale) to deviceCoordinate(frame.centerY(), scale)

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun AppSnapshot.outcome(): Outcome =
        Outcome(renderedText(), screenshotPngBase64.ifEmpty { null })

    companion object {
        private const val SETTLE_DELAY_MS = 800L
        private const val MAX_SCROLL_SWIPES = 20

        // The tools that manipulate the screen (not reads) — gated on the
        // sensitive-screen check in callTool. list_apps/get_app_state are reads.
        private val ACTION_TOOLS = setOf(
            "click", "perform_secondary_action", "scroll", "drag",
            "type_text", "press_key", "set_value",
        )

        private fun error(message: String): Outcome = Outcome(message, isError = true)

        private fun deviceCoordinate(value: Double, scale: Double): Int {
            val effective = if (scale <= 0) 1.0 else scale
            return (value / effective).roundToInt()
        }

        /**
         * Port of device.go scrollSwipe: one swipe (device pixels) that scrolls
         * the element one "page" in the reading direction — scrolling down
         * moves the finger up, etc.
         */
        fun scrollSwipe(frame: ElementFrame, scale: Double, direction: String, fraction: Double): JSONObject {
            val clamped = fraction.coerceIn(0.0, 1.0).let { if (it <= 0) 1.0 else it }
            val centerX = frame.centerX()
            val centerY = frame.centerY()
            val spanX = frame.width * 0.35 * clamped
            val spanY = frame.height * 0.35 * clamped
            val (x1, y1, x2, y2) = when (direction) {
                "down" -> listOf(centerX, centerY + spanY, centerX, centerY - spanY)
                "up" -> listOf(centerX, centerY - spanY, centerX, centerY + spanY)
                "right" -> listOf(centerX + spanX, centerY, centerX - spanX, centerY)
                else -> listOf(centerX - spanX, centerY, centerX + spanX, centerY)
            }
            return JSONObject()
                .put("fromX", deviceCoordinate(x1, scale))
                .put("fromY", deviceCoordinate(y1, scale))
                .put("toX", deviceCoordinate(x2, scale))
                .put("toY", deviceCoordinate(y2, scale))
        }
    }
}
