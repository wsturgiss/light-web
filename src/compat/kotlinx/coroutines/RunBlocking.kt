package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * `runBlocking`, which kotlinx.coroutines has only on threaded platforms. A page has one thread
 * and can't block it, so this runs [block] at once, inline (Dispatchers.Unconfined), and returns
 * its result if it finished without waiting: a read of something already in memory, like a tool's
 * preferences once they're loaded (see lightweb.toolDataStore). If [block] has to wait for real
 * (the network, a timer), it's cancelled and this throws, rather than hanging the page.
 */
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
    var outcome: Result<T>? = null
    val job = CoroutineScope(context + Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
        outcome = try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
    val done = outcome
    if (done == null) {
        job.cancel()
        throw IllegalStateException("runBlocking: the block had to wait, and a page can't block its only thread")
    }
    return done.getOrThrow()
}
