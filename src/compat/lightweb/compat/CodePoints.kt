package lightweb.compat

/**
 * The code-point helpers Java has on String, StringBuilder and Character, which Kotlin/Wasm's
 * stdlib keeps internal. Packages that use them unqualified alias these (JvmBuiltins.kt files).
 */
object JvmCharacter {
    fun charCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1
    fun isLowSurrogate(ch: Char): Boolean = ch.isLowSurrogate()
    fun isHighSurrogate(ch: Char): Boolean = ch.isHighSurrogate()
    fun isLetter(ch: Char): Boolean = ch.isLetter()
    fun isDigit(ch: Char): Boolean = ch.isDigit()
    fun isWhitespace(ch: Char): Boolean = ch.isWhitespace()
    fun toChars(codePoint: Int): CharArray =
        if (codePoint < 0x10000) charArrayOf(codePoint.toChar())
        else (codePoint - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()) }
}

fun String.jvmCodePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}

fun StringBuilder.jvmAppendCodePoint(codePoint: Int): StringBuilder = apply {
    JvmCharacter.toChars(codePoint).forEach { append(it) }
}
