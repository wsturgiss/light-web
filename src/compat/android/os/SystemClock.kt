package android.os

/** `android.os.SystemClock`, from the page's monotonic clock. */
object SystemClock {
    fun uptimeMillis(): Long = performanceNow().toLong()
    fun elapsedRealtime(): Long = performanceNow().toLong()
}

private fun performanceNow(): Double = js("performance.now()")
