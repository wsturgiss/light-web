package lightweb.compat

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * `java.lang` statics that Android code uses unqualified. A tool package aliases these as
 * `Math` / `System` (see src/tools/dnd/JvmBuiltins*.kt), since java.lang's default imports
 * don't exist off the JVM.
 */
object JvmMath {
    fun floorDiv(x: Int, y: Int): Int = x.floorDiv(y)
    fun floorDiv(x: Long, y: Long): Long = x.floorDiv(y)
    fun floorMod(x: Int, y: Int): Int = x.mod(y)
    fun floorMod(x: Long, y: Long): Long = x.mod(y)
    fun toRadians(deg: Double): Double = deg / 180.0 * PI
    fun toDegrees(rad: Double): Double = rad * 180.0 / PI
    fun abs(x: Int): Int = kotlin.math.abs(x)
    fun abs(x: Double): Double = kotlin.math.abs(x)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    /** Java rounds half up (Math.round(-2.5) == -2), unlike kotlin.math.round's half-even. */
    fun round(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
    fun round(x: Float): Int = kotlin.math.floor(x + 0.5f).toInt()
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
    fun pow(a: Double, b: Double): Double = a.pow(b)
}

/** `java.util.TreeMap` by way of `toSortedMap()`: a map iterating in key order. */
fun <K : Comparable<K>, V> Map<out K, V>.sortedByKey(): Map<K, V> =
    entries.sortedBy { it.key }.associateTo(LinkedHashMap()) { it.key to it.value }

object JvmSystem {
    fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    fun nanoTime(): Long = (performanceNow() * 1_000_000).toLong()
}

private fun performanceNow(): Double = js("performance.now()")

/**
 * `String.format` for the specifiers Android tools actually use: `%s`, `%d`, `%x`, `%.Nf`, `%%`,
 * with an optional width. Not a full java.util.Formatter.
 */
fun formatJvm(format: String, vararg args: Any?): String {
    val spec = Regex("""%(-)?(0)?(\d+)?(?:\.(\d+))?([sdfxX%])""")
    var next = 0
    return spec.replace(format) { m ->
        val (left, zero, width, precision, conv) = m.destructured
        if (conv == "%") return@replace "%"
        val arg = args.getOrNull(next++)
        val body = when (conv) {
            "d" -> (arg as Number).toLong().toString()
            "x" -> (arg as Number).toLong().let { if (arg is Byte || arg is Int) it.toInt().toUInt().toString(16) else it.toULong().toString(16) }
            "X" -> (arg as Number).toLong().let { if (arg is Byte || arg is Int) it.toInt().toUInt().toString(16) else it.toULong().toString(16) }.uppercase()
            "f" -> fixed((arg as Number).toDouble(), precision.ifEmpty { "6" }.toInt())
            else -> arg.toString()
        }
        val w = width.toIntOrNull() ?: 0
        when {
            body.length >= w -> body
            left.isNotEmpty() -> body.padEnd(w)
            zero.isNotEmpty() && conv != "s" -> {
                val neg = body.startsWith("-")
                (if (neg) "-" else "") + body.removePrefix("-").padStart(w - (if (neg) 1 else 0), '0')
            }
            else -> body.padStart(w)
        }
    }
}

private fun fixed(x: Double, digits: Int): String {
    if (x.isNaN() || x.isInfinite()) return x.toString()
    val scale = 10.0.pow(digits)
    val r = round(abs(x) * scale)
    val whole = (r / scale).toLong()
    val frac = (r - whole * scale).toLong()
    val sign = if (x < 0 && r != 0.0) "-" else ""
    return if (digits == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(digits, '0')}"
}
