package com.thelightphone.sdk.display

/**
 * light-amp's SDK patch: lifts LightOS's greyscale filter while the tool is in front. Here it
 * lifts the shell's greyscale (lightweb.DisplayColor); [applyForeground] and [restore] are the
 * phone's lifecycle hooks, which the shell's open/close takes care of.
 */
object LightDisplayColor {
    var enabled: Boolean
        get() = lightweb.DisplayColor.toolWantsColour.value
        set(value) { lightweb.DisplayColor.toolWantsColour.value = value }

    fun applyForeground(context: Any?) {}
    fun restore(context: Any?) {}
}
