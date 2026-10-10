package lightweb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.localStorage

/**
 * How the page behaves: as an LP3 emulator, or as a way to use a tool from a computer. It
 * decides how text is entered: the LP3's on-screen keyboard, or the computer's own. Kept per
 * browser.
 */
enum class DeviceMode(val label: String) {
    /** The phone as it is: text through the on-screen LP3 keyboard (the computer's keys type on it too). */
    Lp3("LP3 emulator"),

    /** Text fields take the computer's keyboard directly; no on-screen keyboard. */
    Computer("computer"),
    ;

    companion object {
        private const val KEY = "lightweb:mode"

        var current: DeviceMode by mutableStateOf(
            runCatching { localStorage.getItem(KEY)?.let(DeviceMode::valueOf) }.getOrNull() ?: Lp3
        )
            private set

        fun set(mode: DeviceMode) {
            current = mode
            runCatching { localStorage.setItem(KEY, mode.name) }
        }
    }
}
