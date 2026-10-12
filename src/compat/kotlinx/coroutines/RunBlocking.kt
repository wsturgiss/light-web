package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * `runBlocking`, which kotlinx.coroutines has only on threaded platforms. A page has one thread
 * and can't block it, so this runs [block] at once, inline (Dispatchers.Unconfined), and returns
 * its result if it finished without waiting: a read of something already in memory, like a tool's
 * preferences once they're loaded (see lightweb.toolDataStore).
 *
 * If [block] has to wait for real (the network, a timer), it carries on in the background and this
 * returns null at once. That suits the calls tools make that way, which are teardown work whose
 * result is thrown away: amp, leaving, tells its server playback stopped and saves its place,
 * each under a timeout (PlaybackController.release). A caller that does use the result gets null
 * where it expected a value, and fails there; the warning below says why.
 */
@Suppress("UNCHECKED_CAST")
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
    var outcome: Result<T>? = null
    CoroutineScope(context + Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
        outcome = try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
    val done = outcome
    if (done == null) {
        println("runBlocking: the block has to wait; a page can't, so it finishes in the background and the caller gets null")
        return null as T
    }
    return done.getOrThrow()
}
