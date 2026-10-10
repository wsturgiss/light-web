package android.util

/** `android.util.Log`, to the browser console. */
object Log {
    fun v(tag: String, msg: String, tr: Throwable? = null): Int = out("debug", tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null): Int = out("debug", tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null): Int = out("info", tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = out("warn", tag, msg, tr)
    fun w(tag: String, tr: Throwable): Int = out("warn", tag, "", tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = out("error", tag, msg, tr)

    private fun out(level: String, tag: String, msg: String, tr: Throwable?): Int {
        val line = buildString {
            append(tag).append(": ").append(msg)
            if (tr != null) append('\n').append(tr.stackTraceToString())
        }
        consoleLog(level, line)
        return 0
    }
}

private fun consoleLog(level: String, line: String): Unit = js("console[level](line)")
