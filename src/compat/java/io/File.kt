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
    }

    fun delete(): Boolean {
        val had = isFile
        localStorage.removeItem(key)
        return had
    }

    fun renameTo(dest: File): Boolean {
        val text = localStorage.getItem(key) ?: return false
        localStorage.setItem(dest.key, text)
        localStorage.removeItem(key)
        return true
    }

    fun length(): Long = (localStorage.getItem(key)?.length ?: 0).toLong()

    fun listFiles(): Array<File> = keys()
        .filter { it.startsWith("$KEY_PREFIX$path/") }
        .map { File(it.removePrefix(KEY_PREFIX)) }
        .toTypedArray()

    override fun toString() = path
    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()

    companion object {
        private const val KEY_PREFIX = "lightweb:file:"

        private fun keys(): List<String> = (0 until localStorage.length).mapNotNull { localStorage.key(it) }
    }
}

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class FileNotFoundException(message: String? = null) : IOException(message)
