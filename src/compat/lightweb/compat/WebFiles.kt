package lightweb.compat

import kotlin.js.Promise
import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

/**
 * What `java.io.File` reads and writes: every file a tool keeps, as bytes, in IndexedDB.
 *
 * Android reads files synchronously and IndexedDB can't, so a tool's files are read into memory
 * whole when it opens ([load]), as its assets are, and from then on reads come from memory and
 * every write goes to memory at once and to IndexedDB behind it. IndexedDB, not localStorage,
 * because localStorage holds a few MB per site and a book can be bigger than that.
 *
 * A write is in IndexedDB's hands as soon as it's made, but committed a moment later; [flush]
 * waits for that, and a crash's reload waits on it (see CrashGuard).
 */
object WebFiles {
    private val files = HashMap<String, ByteArray>()
    private val mtimes = HashMap<String, Long>()
    private val loaded = HashSet<String>()
    private val pending = HashSet<Promise<JsAny?>>()

    init {
        installDbOpener()
    }

    /** Reads every file under [dir] into memory, once per page. Earlier versions kept text files in localStorage; those move here. */
    suspend fun load(dir: String) {
        val prefix = dir.trimEnd('/') + "/"
        if (loaded.any { prefix.startsWith(it) }) return
        val records = idbGetAll(prefix).await()!!.unsafeCast<JsArray<JsAny>>()
        for (i in 0 until records.length) {
            val r = records[i]!!
            val path = recordPath(r)
            files[path] = recordBytes(r).toByteArray()
            mtimes[path] = recordMtime(r).toLong()
        }
        migrateFromLocalStorage(prefix)
        loaded += prefix
    }

    /** Waits until every write so far is committed. */
    suspend fun flush() {
        for (p in pending.toList()) runCatching { p.await() }
    }

    /** Every file path loaded. */
    fun paths(): Set<String> = files.keys

    fun read(path: String): ByteArray? = files[path]

    fun size(path: String): Long? = files[path]?.size?.toLong()

    fun mtime(path: String): Long? = if (path in files) mtimes[path] ?: 0L else null

    fun write(path: String, bytes: ByteArray, mtime: Long = now()) {
        files[path] = bytes
        mtimes[path] = mtime
        track(idbPut(path, bytes.toUint8Array(), mtime.toDouble()))
    }

    fun setMtime(path: String, mtime: Long) {
        write(path, files[path] ?: return, mtime)
    }

    fun delete(path: String): Boolean {
        val had = files.remove(path) != null
        mtimes.remove(path)
        if (had) track(idbDelete(path))
        return had
    }

    private fun track(p: Promise<JsAny?>) {
        pending += p
        p.then({ pending -= p; null }, { e -> pending -= p; consoleError("Saving a file failed: $e"); null })
    }

    private fun migrateFromLocalStorage(prefix: String) {
        val keys = (0 until localStorage.length).mapNotNull { localStorage.key(it) }
            .filter { it.startsWith(OLD_FILE + prefix) }
        for (key in keys) {
            val path = key.removePrefix(OLD_FILE)
            val text = localStorage.getItem(key) ?: continue
            val mtime = localStorage.getItem(OLD_MTIME + path)?.toLongOrNull() ?: now()
            write(path, text.encodeToByteArray(), mtime)
            localStorage.removeItem(key)
            localStorage.removeItem(OLD_MTIME + path)
        }
    }

    private const val OLD_FILE = "lightweb:file:"
    private const val OLD_MTIME = "lightweb:mtime:"
}

private fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

internal fun ByteArray.toUint8Array(): Uint8Array {
    val out = Uint8Array(size)
    for (i in indices) out[i] = this[i]
    return out
}

internal fun Uint8Array.toByteArray(): ByteArray = ByteArray(length) { this[it] }

private fun consoleError(message: String): Unit = js("console.error(message)")

// One object store, "files", keyed by path: { path, bytes: Uint8Array, mtime: number }.
private fun idbGetAll(prefix: String): Promise<JsAny?> = js(
    """(async () => {
        const db = await globalThis.lightwebFilesDb();
        return await new Promise((resolve, reject) => {
            const range = IDBKeyRange.bound(prefix, prefix + '￿');
            const req = db.transaction('files').objectStore('files').getAll(range);
            req.onsuccess = () => resolve(req.result);
            req.onerror = () => reject(req.error);
        });
    })()"""
)

private fun idbPut(path: String, bytes: Uint8Array, mtime: Double): Promise<JsAny?> = js(
    """(async () => {
        const db = await globalThis.lightwebFilesDb();
        await new Promise((resolve, reject) => {
            const tx = db.transaction('files', 'readwrite');
            tx.objectStore('files').put({ path, bytes, mtime });
            tx.oncomplete = () => resolve(null);
            tx.onerror = () => reject(tx.error);
            tx.onabort = () => reject(tx.error);
        });
        return null;
    })()"""
)

private fun idbDelete(path: String): Promise<JsAny?> = js(
    """(async () => {
        const db = await globalThis.lightwebFilesDb();
        await new Promise((resolve, reject) => {
            const tx = db.transaction('files', 'readwrite');
            tx.objectStore('files').delete(path);
            tx.oncomplete = () => resolve(null);
            tx.onerror = () => reject(tx.error);
        });
        return null;
    })()"""
)

private fun recordPath(r: JsAny): String = js("r.path")
private fun recordBytes(r: JsAny): Uint8Array = js("r.bytes")
private fun recordMtime(r: JsAny): Double = js("r.mtime")

/** Opens the database once per page; installed on first use. */
private fun installDbOpener(): Unit = js(
    """{
        if (globalThis.lightwebFilesDb) return;
        let opening = null;
        globalThis.lightwebFilesDb = () => opening ??= new Promise((resolve, reject) => {
            const req = indexedDB.open('lightweb', 1);
            req.onupgradeneeded = () => req.result.createObjectStore('files', { keyPath: 'path' });
            req.onsuccess = () => resolve(req.result);
            req.onerror = () => reject(req.error);
        });
    }"""
)
