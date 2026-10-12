package lightweb.shell

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * One tab at a time uses light-web's data: its databases (whose storage holds its files
 * exclusively, see src/web/sqlite-worker.js), and tools' preferences and files, which two live
 * copies of a tool would overwrite each other's. The tab using them holds a Web Lock; another
 * tab says so and offers to take over ("Use here").
 *
 * Taking over asks the active tab, on a BroadcastChannel, to let go: it reloads, which frees the
 * lock and everything it had open, and comes back as a tab that isn't using them. The tab taking
 * over had touched nothing, so it simply carries on starting up once the lock is its own.
 */
object TabLock {
    /** Takes the lock if no other tab has it. Without Web Locks (an old browser), always true. */
    suspend fun acquire(): Boolean = acquireJs().await<JsBoolean>().toBoolean()

    /** Asks the tab that has the lock to let go, and returns once this tab has it. */
    suspend fun takeOver() { takeOverJs().await<JsAny?>() }

    /** [handler] runs when another tab asks this one to let go. */
    fun onTakeOver(handler: () -> Unit) = onTakeOverJs(handler)
}

private fun acquireJs(): Promise<JsBoolean> = js(
    """(() => {
        if (!navigator.locks) return Promise.resolve(true);
        return new Promise((answer) => {
            navigator.locks.request('lightweb-active', { ifAvailable: true }, (lock) => {
                answer(lock !== null);
                // Held for as long as the page lives.
                return lock === null ? undefined : new Promise(() => {});
            });
        });
    })()"""
)

private fun takeOverJs(): Promise<JsAny?> = js(
    """new Promise((done) => {
        navigator.locks.request('lightweb-active', () => {
            done(null);
            return new Promise(() => {});
        });
        new BroadcastChannel('lightweb-tabs').postMessage('take-over');
    })"""
)

private fun onTakeOverJs(handler: () -> Unit): Unit = js(
    """{ new BroadcastChannel('lightweb-tabs').onmessage = (e) => { if (e.data === 'take-over') handler(); }; }"""
)
