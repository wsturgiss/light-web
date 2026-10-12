package java.io

import java.nio.charset.Charset
import lightweb.compat.WebFiles
import kotlin.random.Random

/**
 * The slice of `java.io.File` that tools use: files in the tool's `filesDir`, kept by
 * [WebFiles] (IndexedDB, read into memory when the tool opens). Directories aren't stored; a
 * directory exists while a file is under it, and making one always succeeds.
 *
 * kotlin.io's extensions (`readText`, `nameWithoutExtension`...) are members here: off the JVM
 * there's no kotlin.io.File for them to extend.
 */
class File(path: String) : Comparable<File> {
    constructor(parent: File?, child: String) : this(if (parent == null) child else "${parent.path}/$child")
    constructor(parent: String?, child: String) : this(parent?.let(::File), child)

    val path: String = path.trimEnd('/').ifEmpty { "/" }
    val name: String get() = path.substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let(::File)
    val absolutePath: String get() = path
    val absoluteFile: File get() = this
    val canonicalPath: String get() = normalize(path)
    val canonicalFile: File get() = File(canonicalPath)

    /** kotlin.io's File.normalize(): `.` and `..` resolved in the path. */
    fun normalize(): File = File(normalize(path))

    fun exists(): Boolean = isFile || isDirectory
    val isFile: Boolean get() = WebFiles.read(path) != null
    val isDirectory: Boolean get() = WebFiles.paths().any { it.startsWith("$path/") }

    fun mkdirs(): Boolean = true
    fun mkdir(): Boolean = true

    /** Like Java's: false if it already exists. */
    fun createNewFile(): Boolean {
        if (isFile) return false
        WebFiles.write(path, ByteArray(0))
        return true
    }

    fun readBytes(): ByteArray = WebFiles.read(path) ?: throw FileNotFoundException("$path (No such file or directory)")
    fun readText(charset: Charset = Charset.UTF_8): String = charset.decode(readBytes())
    fun readLines(charset: Charset = Charset.UTF_8): List<String> = readText(charset).lines().let { if (it.lastOrNull() == "") it.dropLast(1) else it }

    fun writeBytes(bytes: ByteArray) = WebFiles.write(path, bytes.copyOf())
    fun writeText(text: String, charset: Charset = Charset.UTF_8) = WebFiles.write(path, charset.encode(text))
    fun appendBytes(bytes: ByteArray) = WebFiles.write(path, (WebFiles.read(path) ?: ByteArray(0)) + bytes)
    fun appendText(text: String, charset: Charset = Charset.UTF_8) = appendBytes(charset.encode(text))

    fun inputStream(): InputStream = FileInputStream(this)
    fun outputStream(): FileOutputStream = FileOutputStream(this)

    /** Deletes a file. A directory isn't stored, so there's nothing to delete for one. */
    fun delete(): Boolean = WebFiles.delete(path)

    fun deleteRecursively(): Boolean {
        WebFiles.paths().filter { it.startsWith("$path/") }.forEach(WebFiles::delete)
        WebFiles.delete(path)
        return true
    }

    fun renameTo(dest: File): Boolean {
        val bytes = WebFiles.read(path) ?: return false
        WebFiles.write(dest.path, bytes, lastModified())
        WebFiles.delete(path)
        return true
    }

    fun length(): Long = WebFiles.size(path) ?: 0L

    /** Set on every write; tools can set it themselves, as on a real filesystem. */
    fun lastModified(): Long = WebFiles.mtime(path) ?: 0L

    fun setLastModified(time: Long): Boolean {
        if (!isFile) return false
        WebFiles.setMtime(path, time)
        return true
    }

    /** The browser gives a site a share of the disk; this doesn't ask how much is left. */
    fun getUsableSpace(): Long = USABLE_SPACE
    val usableSpace: Long get() = USABLE_SPACE
    fun getFreeSpace(): Long = USABLE_SPACE

    /** The files directly in this directory (null if it doesn't exist, like Java). */
    fun listFiles(): Array<File>? {
        val prefix = "$path/"
        val children = WebFiles.paths().filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
        if (children.isEmpty() && !exists()) return null
        return children.map { File(this, it) }.toTypedArray()
    }

    fun listFiles(filter: FileFilter): Array<File>? = listFiles()?.filter { filter.accept(it) }?.toTypedArray()

    fun list(): Array<String>? = listFiles()?.map { it.name }?.toTypedArray()

    override fun compareTo(other: File) = path.compareTo(other.path)
    override fun toString() = path
    override fun equals(other: Any?) = other is File && other.path == path
    override fun hashCode() = path.hashCode()

    companion object {
        const val separator = "/"
        const val separatorChar = '/'

        /** Generous, so a tool that checks space before writing goes ahead. */
        private const val USABLE_SPACE = 1L shl 32

        fun createTempFile(prefix: String, suffix: String? = null, directory: File? = null): File {
            val dir = directory ?: File("/tmp")
            while (true) {
                val f = File(dir, "$prefix${Random.nextLong().toULong()}${suffix ?: ".tmp"}")
                if (f.createNewFile()) return f
            }
        }
    }
}

fun interface FileFilter {
    fun accept(pathname: File): Boolean
}

private fun normalize(path: String): String {
    val out = ArrayList<String>()
    for (part in path.split('/')) when (part) {
        "", "." -> Unit
        ".." -> out.removeLastOrNull()
        else -> out += part
    }
    return "/" + out.joinToString("/")
}
