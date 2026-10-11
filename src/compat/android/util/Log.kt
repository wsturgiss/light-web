package android.util

import co.touchlab.kermit.Logger

/**
 * `android.util.Log`, on Kermit, which writes to the browser console at the matching level.
 * Kermit's own `Logger.setMinSeverity` / `setLogWriters` apply here too.
 */
object Log {
    fun v(tag: String, msg: String, tr: Throwable? = null): Int = 0.also { Logger.v(tr, tag) { msg } }
    fun d(tag: String, msg: String, tr: Throwable? = null): Int = 0.also { Logger.d(tr, tag) { msg } }
    fun i(tag: String, msg: String, tr: Throwable? = null): Int = 0.also { Logger.i(tr, tag) { msg } }
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = 0.also { Logger.w(tr, tag) { msg } }
    fun w(tag: String, tr: Throwable): Int = w(tag, "", tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = 0.also { Logger.e(tr, tag) { msg } }
    fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
}
