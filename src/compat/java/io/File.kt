package java.io

import kotlinx.browser.localStorage

/**
 * The slice of `java.io.File` that tools use to keep a save file: text files in the tool's
 * `filesDir`, kept in localStorage so they survive a reload. Directories aren't stored; a path
 * is just a key.
 */
class File(path: String) {
    constructor(parent: File?, child: String) : this(if (parent == null) child else "${parent.path}/$child")

    val path: String = path.trimEnd('/').ifEmpty { "/" }
    val name: String get() = path.substringAfterLast('/')
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let(::File)
    val absolutePath: String get() = path

    private val key get() = KEY_PREFIX + path

    fun exists(): Boolean = localStorage.getItem(key) != null || isDirectory
    val isDirectory: Boolean get() = keys().any { it.startsWith("$KEY_PREFIX$path/") }
    val isFile: Boolean get() = localStorage.getItem(key) != null

    fun mkdirs(): Boolean = true
    fun mkdir(): Boolean = true

    fun readText(): String = localStorage.getItem(key) ?: throw FileNotFoundException(path)

    fun writeText(text: String) {
        localStorage.setItem(key, text)
        localStorage.setItem(MTIME_PREFIX + path, kotlin.time.Clock.System.now().toEpochMilliseconds().toString())
    }

    fun delete(): Boolean {
        val had = isFile
        localStorage.removeItem(key)
        localStorage.removeItem(MTIME_PREFIX + path)
        return had
    }

    fun renameTo(dest: File): Boolean {
        val text = localStorage.getItem(key) ?: return false
        localStorage.setItem(dest.key, text)
        localStorage.setItem(MTIME_PREFIX + dest.path, lastModified().toString())
        localStorage.removeItem(key)
        localStorage.removeItem(MTIME_PREFIX + path)
        return true
    }

    fun length(): Long = (localStorage.getItem(key)?.length ?: 0).toLong()

    /** Set on every write; tools can set it themselves, as on a real filesystem. */
    fun lastModified(): Long = localStorage.getItem(MTIME_PREFIX + path)?.toLongOrNull() ?: 0L

    fun setLastModified(time: Long): Boolean {
        if (!isFile) return false
        localStorage.setItem(MTIME_PREFIX + path, time.toString())
        return true
    }

    fun readBytes(): ByteArray = readText().encodeToByteArray()

    fun inputStream(): InputStream = ByteArrayInputStream(readBytes())

    /** The files directly in this directory (null if it doesn't exist, like Java). */
    fun listFiles(): Array<File>? {
        val prefix = "$KEY_PREFIX$path/"
        val children = keys().filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
        if (children.isEmpty() && !exists()) return null
        return children.map { File(this, it) }.toTypedArray()
    }

    fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()

    override fun toString() = path
    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()

    companion object {
        private const val KEY_PREFIX = "lightweb:file:"
        private const val MTIME_PREFIX = "lightweb:mtime:"

        private fun keys(): List<String> = (0 until localStorage.length).mapNotNull { localStorage.key(it) }
    }
}

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class FileNotFoundException(message: String? = null) : IOException(message)

/** `java.io.InputStream`, as far as reading a file through `use { }` goes. */
abstract class InputStream : AutoCloseable {
    abstract fun read(): Int

    open fun read(b: ByteArray): Int = read(b, 0, b.size)

    open fun read(b: ByteArray, off: Int, len: Int): Int {
        var n = 0
        while (n < len) {
            val c = read()
            if (c < 0) break
            b[off + n++] = c.toByte()
        }
        return if (n == 0 && len > 0) -1 else n
    }

    open fun readBytes(): ByteArray {
        val out = ArrayList<Byte>()
        while (true) { val c = read(); if (c < 0) break; out.add(c.toByte()) }
        return out.toByteArray()
    }

    override fun close() {}
}

class ByteArrayInputStream(private val bytes: ByteArray) : InputStream() {
    private var pos = 0
    override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xff else -1
    override fun readBytes(): ByteArray = bytes.copyOfRange(pos, bytes.size).also { pos = bytes.size }
}
