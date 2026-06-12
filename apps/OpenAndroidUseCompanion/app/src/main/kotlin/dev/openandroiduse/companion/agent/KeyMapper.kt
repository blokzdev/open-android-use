package dev.openandroiduse.companion.agent

/**
 * Maps the press_key tool's xdotool-style key names onto the action surface an
 * accessibility service can actually drive (global actions, IME action,
 * focused-text edits). Android offers no raw key injection to accessibility
 * services, so the supported set is deliberately small and everything else is
 * an informative error — same philosophy as the ADB bridge's ASCII guard.
 */
object KeyMapper {

    sealed class KeyAction {
        object Back : KeyAction()
        object Home : KeyAction()
        object Recents : KeyAction()
        object Notifications : KeyAction()
        object ImeEnter : KeyAction()
        object DeleteBackward : KeyAction()
        data class Unsupported(val message: String) : KeyAction()
    }

    fun map(key: String): KeyAction = when (key.trim().lowercase()) {
        "return", "enter", "kp_enter" -> KeyAction.ImeEnter
        "back", "escape" -> KeyAction.Back
        "home", "super" -> KeyAction.Home
        "recents", "app_switch" -> KeyAction.Recents
        "notifications" -> KeyAction.Notifications
        "backspace", "delete" -> KeyAction.DeleteBackward
        else -> KeyAction.Unsupported(
            "Key \"$key\" is not supported by the on-device agent. Supported keys: " +
                "Return/Enter, Back/Escape, Home, Recents, Notifications, BackSpace/Delete. " +
                "Use type_text or set_value for literal text.",
        )
    }
}
