package dev.openandroiduse.companion.agent

import android.content.Context
import android.provider.Settings

/**
 * Reduce-motion support (Phase 4.6b). Compose's own animation APIs and the
 * classic-View Animator framework already honor the system "Remove animations"
 * setting; this is for the few hand-rolled animations that don't (e.g. the
 * GestureTrail Canvas loop) and for making list auto-scroll instant.
 *
 * The pure [isReduced] is unit-tested; [animationsDisabled] reads the system
 * `ANIMATOR_DURATION_SCALE` (0 when the user has turned animations off).
 */
object Motion {

    /** Animations are off when the system animator duration scale is exactly 0. */
    fun isReduced(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    fun animationsDisabled(context: Context): Boolean = isReduced(animatorDurationScale(context))

    private fun animatorDurationScale(context: Context): Float = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
    } catch (_: Settings.SettingNotFoundException) {
        1f
    }
}
