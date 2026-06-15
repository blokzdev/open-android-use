package dev.openandroiduse.companion

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * A coarse device-capability tier (Phase 5.4). It answers "how well can this
 * device run heavier features" — distinct from [Readiness], which answers "can
 * the agent run at all" (accessibility + key). The tier is the signal the
 * on-device model tier (5.5) and adaptive perception (5.6) will gate on; 5.4
 * only produces and surfaces it (a diagnostic on the About screen) — it changes
 * no agent-loop or perception behavior.
 */
enum class DeviceTier { HIGH, MEDIUM, LOW }

/**
 * Honest device facts, free of Android types so the tier rule is unit-testable.
 * Collect on-device with [detectDeviceCapability].
 */
data class DeviceCapability(
    val totalRamBytes: Long,
    val cpuCores: Int,
    val sdkInt: Int,
    val is64Bit: Boolean,
    val isLowRamDevice: Boolean,
) {
    val tier: DeviceTier
        get() = classifyTier(totalRamBytes, cpuCores, sdkInt, is64Bit, isLowRamDevice)
}

/**
 * Coarse, **provisional** tier rule. The exact "can run an on-device model"
 * cutoff is tuned in Phase 5.5 against the real model; nothing gates on this in
 * 5.4, so a rough cutoff is harmless (it only labels a diagnostic). A 32-bit or
 * low-RAM-flagged or <4 GiB device is LOW; a roomy, modern, multi-core 64-bit
 * device is HIGH; everything else is MEDIUM.
 */
fun classifyTier(
    totalRamBytes: Long,
    cpuCores: Int,
    sdkInt: Int,
    is64Bit: Boolean,
    isLowRamDevice: Boolean,
): DeviceTier = when {
    isLowRamDevice || !is64Bit || totalRamBytes < gibibytes(4) -> DeviceTier.LOW
    totalRamBytes >= gibibytes(8) && cpuCores >= 6 && sdkInt >= Build.VERSION_CODES.S -> DeviceTier.HIGH
    else -> DeviceTier.MEDIUM
}

private fun gibibytes(n: Long): Long = n * 1024L * 1024L * 1024L

/** Read the real device facts. Android-only; the logic lives in [classifyTier]. */
fun detectDeviceCapability(context: Context): DeviceCapability {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return DeviceCapability(
        totalRamBytes = memoryInfo.totalMem,
        cpuCores = Runtime.getRuntime().availableProcessors(),
        sdkInt = Build.VERSION.SDK_INT,
        is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
        isLowRamDevice = activityManager.isLowRamDevice,
    )
}
