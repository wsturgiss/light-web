package android.view

/**
 * The parts of `android.view.KeyEvent` that tools use. The SDK's key handler interface takes one,
 * so the shell builds these from browser key events (see lightweb.KeyMapping).
 */
class KeyEvent(
    val action: Int,
    val keyCode: Int,
    val repeatCount: Int = 0,
    val unicodeChar: Int = 0,
    val characters: String? = null,
) {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MULTIPLE = 2

        const val KEYCODE_UNKNOWN = 0
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_POWER = 26
        const val KEYCODE_CAMERA = 27
        const val KEYCODE_ENTER = 66
        const val KEYCODE_DEL = 67
        const val KEYCODE_ESCAPE = 111
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    }
}
