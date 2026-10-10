package com.thelightphone.lp3Keyboard.ui

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

// The web version of the keyboard library's HardwareKeyboardInput.kt. Upstream reads Android
// InputDevices to undo the LP3's remapping of external keyboards; a browser has none of that,
// so the computer's keys go straight to the on-screen keyboard's callback, as an external
// keyboard's do on the phone.

/** Key codes reported by the hardware input on an LP3. */
enum class LightDeviceKeys(
    val keyCode: Int
) {
    VolumeUp(24),
    VolumeDown(25),
    ShutterPressed(27),
    ShutterHalfPressed(80),
    RotaryTurnUp(317),
    RotaryTurnDown(318),
    RotaryButtonPress(319)
    ;
    companion object {
        val mapping = entries.associateBy { it.keyCode }
    }
}

/** No LightOS key layout to undo in a browser. */
fun lightOsRemap(nativeKeyEvent: KeyEvent): Int = nativeKeyEvent.keyCode

@Composable
fun Modifier.hardwareKeyboardInput(
    callback: Lp3KeyboardCallback,
    remapKeyCode: ((KeyEvent) -> Int)? = ::lightOsRemap
): Modifier {
    val focusRequester = remember { FocusRequester() }
    // Browsers repeat keydown while a key is held; the keyboard's callback does its own repeat.
    val held = remember { HashSet<Key>() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { e ->
            // Leave browser shortcuts (copy, reload...) alone.
            if (e.isCtrlPressed || e.isMetaPressed) return@onKeyEvent false
            val special = when (e.key) {
                Key.Backspace -> SpecialKey.Backspace
                Key.Enter, Key.NumPadEnter -> SpecialKey.Return
                else -> null
            }
            val codePoint = e.utf16CodePoint.takeIf { it != 0 }
            if (special == null && codePoint == null) return@onKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    if (held.add(e.key)) {
                        if (special != null) callback.onSpecialKeyPressed(special) else callback.onKeyPressed(codePoint!!)
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    held.remove(e.key)
                    if (special != null) callback.onSpecialKeyReleased(special) else codePoint?.let(callback::onKeyReleased)
                    true
                }
                else -> false
            }
        }
}
