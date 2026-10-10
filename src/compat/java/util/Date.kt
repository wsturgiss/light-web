package java.util

/** A point in time, in epoch milliseconds, for `java.text.DateFormat`. */
class Date(val time: Long) {
    constructor() : this(kotlin.time.Clock.System.now().toEpochMilliseconds())
}
