package java.time

/** `java.time.Instant`, enough to print an epoch-millis time as ISO-8601. */
class Instant private constructor(private val instant: kotlin.time.Instant) : Comparable<Instant> {
    fun toEpochMilli(): Long = instant.toEpochMilliseconds()
    override fun compareTo(other: Instant) = instant.compareTo(other.instant)
    override fun toString() = instant.toString()
    override fun equals(other: Any?) = other is Instant && other.instant == instant
    override fun hashCode() = instant.hashCode()

    companion object {
        fun ofEpochMilli(ms: Long) = Instant(kotlin.time.Instant.fromEpochMilliseconds(ms))
        fun now() = Instant(kotlin.time.Clock.System.now())
    }
}
