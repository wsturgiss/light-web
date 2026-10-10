package lightweb

import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import lightweb.generated.packFiles
import lightweb.generated.resourcePaths
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response

/**
 * Every file a tool reads, held in memory.
 *
 * Android reads assets and resources synchronously (`readAsset`, `R.font.x`), and tools rely on
 * that. A browser can only fetch asynchronously, so a pack is fetched whole before anything
 * that reads from it runs: the SDK UI's at startup, a tool's when it's opened.
 */
object Assets {
    private val files = HashMap<String, ByteArray>()
    private val loaded = HashSet<String>()

    fun isLoaded(pack: String) = pack in loaded

    /** Fetches every file in [pack]; [progress] gets (done, total). */
    suspend fun load(pack: String, progress: (Int, Int) -> Unit = { _, _ -> }) {
        if (pack in loaded) return
        val paths = packFiles[pack] ?: error("No asset pack '$pack'")
        var done = 0
        coroutineScope {
            paths.map { path ->
                async {
                    files[path] = fetchBytes(path)
                    progress(++done, paths.size)
                }
            }.awaitAll()
        }
        loaded += pack
    }

    /** A file by its served path, `assets/<pack>/...`. */
    fun read(path: String): ByteArray =
        files[path] ?: error("$path isn't loaded (is its pack loaded? is the path right?)")

    /** An Android asset of [pack], by the path the tool asked `readAsset` for. */
    fun readAsset(pack: String, path: String): ByteArray = read("assets/$pack/assets/$path")

    fun pathOf(resId: Int): String =
        resourcePaths[resId] ?: error("No resource 0x${resId.toString(16)}")

    fun resource(resId: Int): ByteArray = read(pathOf(resId))
}

private suspend fun fetchBytes(path: String): ByteArray {
    val response = window.fetch(path).await()
    check(response.ok) { "Fetching $path: HTTP ${response.status}" }
    val buffer = response.arrayBuffer().await()
    return buffer.toByteArray()
}

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
