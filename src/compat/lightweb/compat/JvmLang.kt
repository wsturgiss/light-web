package lightweb.compat

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * `java.lang` statics that Android code uses unqualified. A tool package aliases these as
 * `Math` / `System` (see src/templates/JvmBuiltins.kt.template), since java.lang's default imports
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
            "x", "X" -> hex(arg as Number).let { if (conv == "X") it.uppercase() else it }
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

/** Java's %x: a negative value as its two's complement in the argument's own width (a Byte is two digits). */
private fun hex(n: Number): String = when (n) {
    is Byte -> (n.toInt() and 0xff).toString(16)
    is Short -> (n.toInt() and 0xffff).toString(16)
    is Int -> n.toUInt().toString(16)
    else -> n.toLong().toULong().toString(16)
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

/**
 * `java.lang.Thread`, as tools use it: the current thread's name and the default uncaught
 * exception handler. The page has one thread, "main". CrashGuard calls the handler with what it
 * caught before the page reloads, as Android's runtime does before the process dies.
 */
class JvmThread private constructor(val name: String, private val target: JvmRunnable?) {
    /** A thread to run [target]. It can't be started: the page has one thread (see [start]). */
    constructor(target: JvmRunnable?, name: String) : this(name, target)
    constructor(target: JvmRunnable?) : this("Thread", target)

    fun getName(): String = name

    var isDaemon: Boolean = false
    private var handler: UncaughtExceptionHandler? = null
    fun setUncaughtExceptionHandler(handler: UncaughtExceptionHandler?) { this.handler = handler }

    /** A second thread can't run beside the page's one, so this fails rather than pretend. */
    fun start(): Unit = throw UnsupportedOperationException("Threads can't be started in the browser")

    fun join(millis: Long = 0) {}
    fun interrupt() {}
    val isInterrupted: Boolean get() = false
    val isAlive: Boolean get() = false

    fun interface UncaughtExceptionHandler {
        fun uncaughtException(t: JvmThread, e: Throwable)
    }

    companion object {
        private val main = JvmThread("main", null)
        private var defaultHandler: UncaughtExceptionHandler? = null

        fun currentThread(): JvmThread = main

        /** Never interrupted: nothing can interrupt the page's one thread. */
        fun interrupted(): Boolean = false

        /** A page can't sleep its only thread; code that would wait must suspend instead. */
        fun sleep(millis: Long): Unit = throw UnsupportedOperationException("Thread.sleep can't block the browser's only thread")
        fun getDefaultUncaughtExceptionHandler(): UncaughtExceptionHandler? = defaultHandler
        fun setDefaultUncaughtExceptionHandler(handler: UncaughtExceptionHandler?) { defaultHandler = handler }

        /** For CrashGuard: tells the tool's handler, if it set one. Never throws. */
        fun reportUncaught(e: Throwable) {
            runCatching { defaultHandler?.uncaughtException(main, e) }
        }
    }
}

/** `kotlin.text.Charsets`, which is JVM-only. */
object JvmCharsets {
    val UTF_8 = java.nio.charset.Charset.UTF_8
    val US_ASCII = java.nio.charset.Charset.US_ASCII
    val ISO_8859_1 = java.nio.charset.Charset.ISO_8859_1
    val UTF_16 = java.nio.charset.Charset.UTF_16
    val UTF_16BE = java.nio.charset.Charset.UTF_16BE
    val UTF_16LE = java.nio.charset.Charset.UTF_16LE
}

/** `java.lang.Runnable`. */
fun interface JvmRunnable {
    fun run()
}

/** `java.lang.InterruptedException`. */
open class JvmInterruptedException(message: String? = null) : Exception(message)

/** For the template's `InputStream.copyTo`. */
fun copyStream(input: java.io.InputStream, out: java.io.OutputStream, bufferSize: Int): Long {
    val buffer = ByteArray(bufferSize)
    var total = 0L
    while (true) {
        val n = input.read(buffer, 0, buffer.size)
        if (n < 0) return total
        out.write(buffer, 0, n)
        total += n
    }
}

/** For the template's `Enumeration.toList()`. */
fun <T> enumerationToList(e: java.util.Enumeration<T>): List<T> = buildList { while (e.hasMoreElements()) add(e.nextElement()) }

/** For the template's `MutableMap.remove(key, value)`. */
fun <K, V> removeIfMapped(map: MutableMap<K, V>, key: K, value: V): Boolean =
    if (map.containsKey(key) && map[key] == value) { map.remove(key); true } else false

/**
 * A LinkedHashMap that can keep entries in access order and drop its eldest on insertion, as
 * java.util.LinkedHashMap(capacity, loadFactor, accessOrder) with removeEldestEntry does: the
 * usual LRU cache. Kotlin/Wasm's LinkedHashMap is final and keeps insertion order only, so tools
 * that subclass it get this through an exact rewrite (build.gradle.kts).
 */
open class AccessOrderLinkedHashMap<K, V>(
    initialCapacity: Int = 16,
    loadFactor: Float = 0.75f,
    private val accessOrder: Boolean = false,
) : AbstractMutableMap<K, V>() {
    private val map = LinkedHashMap<K, V>()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
    override val size: Int get() = map.size
    override fun containsKey(key: K) = map.containsKey(key)

    override fun get(key: K): V? {
        if (!map.containsKey(key)) return null
        val v = map.getValue(key)
        if (accessOrder) { map.remove(key); map[key] = v }
        return v
    }

    override fun put(key: K, value: V): V? {
        val previous = map.remove(key)
        map[key] = value
        if (previous == null) {
            val eldest = map.entries.first()
            if (removeEldestEntry(eldest)) map.remove(eldest.key)
        }
        return previous
    }

    override fun remove(key: K): V? = map.remove(key)
    override fun clear() = map.clear()

    protected open fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = false
}
