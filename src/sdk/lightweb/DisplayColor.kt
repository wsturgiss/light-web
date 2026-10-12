package lightweb

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether the open tool has asked for colour (`LightDisplayColor.enabled`, light-amp's SDK
 * patch). On the phone that lifts LightOS's greyscale filter while the tool is in front; here the
 * shell lifts its own (see LightOsShell), and puts it back when the tool closes.
 */
object DisplayColor {
    val toolWantsColour = MutableStateFlow(false)
}
