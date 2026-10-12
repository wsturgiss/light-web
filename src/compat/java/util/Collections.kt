package java.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// java.util pieces light-amp uses. The page has one thread, so the synchronized and concurrent
// variants are the plain ones.

interface Enumeration<E> {
    fun hasMoreElements(): Boolean
    fun nextElement(): E
}

object Collections {
    fun <T> synchronizedSet(s: MutableSet<T>): MutableSet<T> = s
    fun <T> synchronizedList(l: MutableList<T>): MutableList<T> = l
    fun <K, V> synchronizedMap(m: MutableMap<K, V>): MutableMap<K, V> = m

    /** A set backed by [map]'s keys, as Java's. */
    fun <E> newSetFromMap(map: MutableMap<E, Boolean>): MutableSet<E> = object : AbstractMutableSet<E>() {
        override val size: Int get() = map.size
        override fun add(element: E): Boolean = map.put(element, true) == null
        override fun remove(element: E): Boolean = map.remove(element) != null
        override fun contains(element: E): Boolean = map.containsKey(element)
        override fun clear() = map.clear()
        override fun iterator(): MutableIterator<E> = map.keys.iterator()
    }

    fun <T> emptyEnumeration(): Enumeration<T> = enumeration(emptyList())

    fun <T> enumeration(c: Collection<T>): Enumeration<T> = object : Enumeration<T> {
        private val it = c.iterator()
        override fun hasMoreElements() = it.hasNext()
        override fun nextElement() = it.next()
    }

    fun <T> list(e: Enumeration<T>): ArrayList<T> = ArrayList(lightweb.compat.enumerationToList(e))

    fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
    fun <T> emptySet(): Set<T> = kotlin.collections.emptySet()
    fun <K, V> emptyMap(): Map<K, V> = kotlin.collections.emptyMap()
    fun <T> unmodifiableList(l: List<T>): List<T> = l
    fun <T> unmodifiableSet(s: Set<T>): Set<T> = s
    fun <K, V> unmodifiableMap(m: Map<K, V>): Map<K, V> = m
}

/**
 * `java.util.WeakHashMap`. Kotlin/Wasm has no weak references, so entries are held strongly:
 * nothing is collected while the map holds it. Fine for the small registries tools keep in one.
 */
typealias WeakHashMap<K, V> = HashMap<K, V>

/** `java.util.Random`, seeded or not. Not the JVM's sequence for a given seed. */
open class Random(seed: Long? = null) {
    constructor() : this(null)
    private val r = if (seed == null) kotlin.random.Random.Default else kotlin.random.Random(seed)
    open fun nextInt(): Int = r.nextInt()
    open fun nextInt(bound: Int): Int = r.nextInt(bound)
    open fun nextLong(): Long = r.nextLong()
    open fun nextDouble(): Double = r.nextDouble()
    open fun nextFloat(): Float = r.nextFloat()
    open fun nextBoolean(): Boolean = r.nextBoolean()
    open fun nextBytes(bytes: ByteArray) { r.nextBytes(bytes) }
}

/** `java.util.TimeZone`: UTC and the browser's own zone. */
class TimeZone private constructor(val id: String) {
    internal val zone: kotlinx.datetime.TimeZone get() = kotlinx.datetime.TimeZone.of(id)

    companion object {
        fun getTimeZone(id: String): TimeZone =
            TimeZone(if (runCatching { kotlinx.datetime.TimeZone.of(id) }.isSuccess) id else "UTC")

        fun getDefault(): TimeZone = TimeZone(kotlinx.datetime.TimeZone.currentSystemDefault().id)
    }
}

/** `java.util.Calendar`, for setting a date's fields and reading its time (and back). */
abstract class Calendar protected constructor(private val tz: TimeZone) {
    private val fields = IntArray(17)

    fun clear() {
        fields.fill(0)
        fields[YEAR] = 1970
        fields[DAY_OF_MONTH] = 1
    }

    fun set(year: Int, month: Int, date: Int) {
        fields[YEAR] = year; fields[MONTH] = month; fields[DAY_OF_MONTH] = date
    }

    fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int = 0) {
        set(year, month, date); fields[HOUR_OF_DAY] = hourOfDay; fields[MINUTE] = minute; fields[SECOND] = second
    }

    fun set(field: Int, value: Int) { fields[field] = value }

    fun get(field: Int): Int = fields[field]

    /** Out-of-range fields roll over, as Java's lenient calendar does (month 12 is next January). */
    var timeInMillis: Long
        get() {
            val date = LocalDate(fields[YEAR], 1, 1)
                .plus(fields[MONTH], DateTimeUnit.MONTH)
                .plus(fields[DAY_OF_MONTH] - 1, DateTimeUnit.DAY)
            val midnight = LocalDateTime(date, LocalTime(0, 0)).toInstant(tz.zone).toEpochMilliseconds()
            return midnight + ((fields[HOUR_OF_DAY] * 60L + fields[MINUTE]) * 60 + fields[SECOND]) * 1000 + fields[MILLISECOND]
        }
        set(value) {
            val local = kotlin.time.Instant.fromEpochMilliseconds(value).toLocalDateTime(tz.zone)
            fields[YEAR] = local.year; fields[MONTH] = local.month.ordinal; fields[DAY_OF_MONTH] = local.day
            fields[HOUR_OF_DAY] = local.hour; fields[MINUTE] = local.minute; fields[SECOND] = local.second
            fields[MILLISECOND] = value.mod(1000L).toInt()
        }

    var time: Date
        get() = Date(timeInMillis)
        set(value) { timeInMillis = value.time }

    companion object {
        const val YEAR = 1
        const val MONTH = 2
        const val DAY_OF_MONTH = 5
        const val DATE = 5
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14

        fun getInstance(): Calendar = GregorianCalendar()
        fun getInstance(zone: TimeZone): Calendar = GregorianCalendar(zone)
    }
}

class GregorianCalendar(zone: TimeZone = TimeZone.getDefault()) : Calendar(zone) {
    init { timeInMillis = kotlin.time.Clock.System.now().toEpochMilliseconds() }
}
