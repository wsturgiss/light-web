package android.graphics.fonts

import java.io.File

/**
 * The device's installed fonts. The SDK and the LP3 keyboard look here for Akkurat, which LP3s
 * have and a browser doesn't, so there are none: they fall back to a bundled copy (see
 * Resources.getIdentifier) or the default font.
 */
object SystemFonts {
    fun getAvailableFonts(): Set<Font> = emptySet()
}

class Font private constructor(val file: File?, val style: FontStyle)

class FontStyle(val weight: Int = 400, val slant: Int = 0)
