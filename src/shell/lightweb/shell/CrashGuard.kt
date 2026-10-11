package lightweb.shell

import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * What Android does when an app throws: the process dies and the user is back at the launcher,
 * told the app stopped. Here the page reloads, which is the nearest thing to a new process.
 *
 * A reload, not just going back to the home screen, because an exception while composing
 * leaves Compose dead: nothing redraws and no input is handled. An exception in a click or a
 * coroutine leaves the page running but the tool half-way through whatever it was doing, which
 * is no better. A tool's data is in localStorage and written synchronously, so what it saved
 * survives.
 *
 * Only while a tool is open (or opening): an exception in the shell itself is logged and left
 * alone, so a broken shell can't reload forever.
 */
object CrashGuard {
    private const val KEY = "lightweb:crash"

    private var openTool: String? = null

    /** Set by the last reload, if it was a crash: "<tool> stopped" and the error. */
    val lastCrash: Crash? = runCatching { sessionStorage.getItem(KEY) }.getOrNull()?.let { text ->
        runCatching { sessionStorage.removeItem(KEY) }
        val (tool, error) = text.split('\n', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        Crash(tool, error)
    }

    class Crash(val tool: String, val error: String) {
        /** The error's first line: the exception and its message, without the stack. */
        val summary get() = error.lineSequence().first().removePrefix("kotlin.")
    }

    fun install() {
        lastCrash?.let { consoleError("Before this reload, ${it.tool} stopped:\n${it.error}") }
        window.addEventListener("error", ::onUncaught)
        window.addEventListener("unhandledrejection", ::onUncaught)
    }

    fun toolOpening(name: String) {
        openTool = name
    }

    fun toolClosed() {
        openTool = null
    }

    private fun onUncaught(event: Event) {
        val tool = openTool ?: return
        openTool = null
        runCatching { sessionStorage.setItem(KEY, "$tool\n${describe(event)}") }
        window.location.reload()
    }
}

/** The stack (or message) of an `error` event's error, or an `unhandledrejection`'s reason. */
private fun describe(event: Event): String = js(
    """{
        const e = event.error ?? event.reason;
        return String((e && e.stack) || (e && e.message) || event.message || e);
    }"""
)

private fun consoleError(message: String): Unit = js("console.error(message)")
