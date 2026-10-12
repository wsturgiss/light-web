package java.util.concurrent

/** `java.util.concurrent.TimeUnit`, for converting durations. */
enum class TimeUnit(private val nanos: Long) {
    NANOSECONDS(1L),
    MICROSECONDS(1_000L),
    MILLISECONDS(1_000_000L),
    SECONDS(1_000_000_000L),
    MINUTES(60_000_000_000L),
    HOURS(3_600_000_000_000L),
    DAYS(86_400_000_000_000L);

    private fun scale(d: Long, to: TimeUnit): Long =
        if (nanos >= to.nanos) d * (nanos / to.nanos) else d / (to.nanos / nanos)

    fun toNanos(d: Long) = scale(d, NANOSECONDS)
    fun toMicros(d: Long) = scale(d, MICROSECONDS)
    fun toMillis(d: Long) = scale(d, MILLISECONDS)
    fun toSeconds(d: Long) = scale(d, SECONDS)
    fun toMinutes(d: Long) = scale(d, MINUTES)
    fun toHours(d: Long) = scale(d, HOURS)
    fun toDays(d: Long) = scale(d, DAYS)
    fun convert(sourceDuration: Long, sourceUnit: TimeUnit): Long = sourceUnit.scale(sourceDuration, this)
}

/**
 * `java.util.concurrent.ArrayBlockingQueue`. With one thread nothing can fill a queue while
 * another waits on it, so the blocking calls don't block: [take] and [put] fail on an empty or
 * full queue instead of hanging the page.
 */
class ArrayBlockingQueue<E>(private val capacity: Int) {
    private val items = ArrayDeque<E>()
    val size: Int get() = items.size
    fun offer(e: E): Boolean = if (items.size < capacity) { items.addLast(e); true } else false
    fun offer(e: E, timeout: Long, unit: TimeUnit): Boolean = offer(e)
    fun put(e: E) = check(offer(e)) { "ArrayBlockingQueue is full; it can't wait for room on the web" }
    fun poll(): E? = items.removeFirstOrNull()
    fun poll(timeout: Long, unit: TimeUnit): E? = poll()
    fun take(): E = checkNotNull(poll()) { "ArrayBlockingQueue is empty; it can't wait for an item on the web" }
    fun peek(): E? = items.firstOrNull()
    fun clear() = items.clear()
    fun isEmpty(): Boolean = items.isEmpty()
    fun remainingCapacity(): Int = capacity - items.size
}
