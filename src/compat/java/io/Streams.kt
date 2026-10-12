package java.io

import java.nio.charset.Charset

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class FileNotFoundException(message: String? = null) : IOException(message)

class EOFException(message: String? = null) : IOException(message)

interface Closeable : AutoCloseable {
    override fun close()
}

interface Flushable {
    fun flush()
}

/** `java.io.InputStream`. kotlin.io's `readBytes()` is a member, as there's no kotlin.io here. */
abstract class InputStream : Closeable {
    abstract fun read(): Int

    open fun read(b: ByteArray): Int = read(b, 0, b.size)

    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var n = 0
        while (n < len) {
            val c = read()
            if (c < 0) break
            b[off + n++] = c.toByte()
        }
        return if (n == 0) -1 else n
    }

    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun readAllBytes(): ByteArray = readBytes()

    open fun skip(n: Long): Long {
        var skipped = 0L
        while (skipped < n && read() >= 0) skipped++
        return skipped
    }

    open fun available(): Int = 0
    open fun markSupported(): Boolean = false
    open fun mark(readlimit: Int) {}
    open fun reset(): Unit = throw IOException("mark/reset not supported")
    override fun close() {}

    fun transferTo(out: OutputStream): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) return total
            out.write(buffer, 0, n)
            total += n
        }
    }

    companion object {
        fun nullInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    }
}

open class ByteArrayInputStream(private val bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) : InputStream() {
    private var pos = offset
    private val end = minOf(offset + length, bytes.size)
    private var marked = offset

    override fun read(): Int = if (pos < end) bytes[pos++].toInt() and 0xff else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= end) return -1
        val n = minOf(len, end - pos)
        bytes.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }

    override fun readBytes(): ByteArray = bytes.copyOfRange(pos, end).also { pos = end }
    override fun skip(n: Long): Long = minOf(n, (end - pos).toLong()).coerceAtLeast(0).also { pos += it.toInt() }
    override fun available(): Int = end - pos
    override fun markSupported() = true
    override fun mark(readlimit: Int) { marked = pos }
    override fun reset() { pos = marked }
}

/** The file's bytes as they were when opened. */
class FileInputStream(file: File) : ByteArrayInputStream(file.readBytes())

open class FilterInputStream(protected val `in`: InputStream) : InputStream() {
    override fun read(): Int = `in`.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len)
    override fun skip(n: Long): Long = `in`.skip(n)
    override fun available(): Int = `in`.available()
    override fun markSupported(): Boolean = `in`.markSupported()
    override fun mark(readlimit: Int) = `in`.mark(readlimit)
    override fun reset() = `in`.reset()
    override fun close() = `in`.close()
}

abstract class OutputStream : Closeable, Flushable {
    abstract fun write(b: Int)
    open fun write(b: ByteArray) = write(b, 0, b.size)
    open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) write(b[i].toInt())
    }
    override fun flush() {}
    override fun close() {}
}

open class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
    protected var buf = ByteArray(maxOf(size, 1))
    protected var count = 0

    private fun ensure(extra: Int) {
        if (count + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, count + extra))
    }

    override fun write(b: Int) {
        ensure(1)
        buf[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }

    fun size(): Int = count
    fun reset() { count = 0 }
    fun toByteArray(): ByteArray = buf.copyOf(count)
    override fun toString(): String = Charset.UTF_8.decode(buf, 0, count)
    fun toString(charsetName: String): String = Charset.forName(charsetName).decode(buf, 0, count)
    fun toString(charset: Charset): String = charset.decode(buf, 0, count)
}

/**
 * Writes [file] whole on every flush and on close: the file is replaced, not streamed into.
 * Opening it empties the file, as Java's does, unless [append].
 */
class FileOutputStream(private val file: File, append: Boolean = false) : ByteArrayOutputStream() {
    constructor(path: String, append: Boolean = false) : this(File(path), append)

    init {
        if (append) file.takeIf { it.isFile }?.readBytes()?.let { write(it) } else file.writeBytes(ByteArray(0))
    }

    private var closed = false

    val fd: FileDescriptor = FileDescriptor { flush() }

    override fun write(b: Int) {
        if (closed) throw IOException("Stream Closed")
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (closed) throw IOException("Stream Closed")
        super.write(b, off, len)
    }

    override fun flush() {
        if (!closed) file.writeBytes(toByteArray())
    }

    override fun close() {
        if (closed) return
        flush()
        closed = true
    }
}

/** `sync()` has nothing more to wait for than the flush it makes. */
class FileDescriptor internal constructor(private val onSync: () -> Unit) {
    fun sync() = onSync()
}

abstract class Reader : Closeable {
    abstract fun read(cbuf: CharArray, off: Int, len: Int): Int
    open fun read(): Int {
        val one = CharArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].code
    }
    fun readText(): String {
        val out = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n < 0) return out.toString()
            out.appendRange(buffer, 0, n)
        }
    }
    override fun close() {}
}

class StringReader(private val s: String) : Reader() {
    private var pos = 0
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= s.length) return -1
        val n = minOf(len, s.length - pos)
        s.toCharArray(cbuf, off, pos, pos + n)
        pos += n
        return n
    }
}

/** Marker interface; serialization isn't provided. */
interface Serializable

/** Buffers nothing: the stream under it is in memory already. */
class BufferedOutputStream(private val out: OutputStream, size: Int = 8192) : OutputStream() {
    override fun write(b: Int) = out.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
    override fun flush() = out.flush()
    override fun close() = out.close()
}

/**
 * `java.io.RandomAccessFile` over a light-web file (bytes in memory, see `WebFiles`). Mode "r"
 * reads; "rw" also writes, saving the file on [close].
 */
class RandomAccessFile(private val file: File, mode: String) : Closeable {
    constructor(name: String, mode: String) : this(File(name), mode)

    private val writable = 'w' in mode
    private var bytes: ByteArray = if (file.isFile) file.readBytes() else if (writable) ByteArray(0) else throw FileNotFoundException("${file.path} (No such file or directory)")
    private var length = bytes.size
    private var pos = 0L
    private var dirty = false

    val filePointer: Long get() = pos
    fun getFilePointer(): Long = pos

    fun length(): Long = length.toLong()

    fun seek(pos: Long) {
        if (pos < 0) throw IOException("Negative seek offset")
        this.pos = pos
    }

    fun skipBytes(n: Int): Int {
        val skip = maxOf(0, minOf(n.toLong(), length - pos)).toInt()
        pos += skip
        return skip
    }

    fun read(): Int = if (pos >= length) -1 else bytes[pos++.toInt()].toInt() and 0xff

    fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
        if (len == 0) return 0
        if (pos >= length) return -1
        val n = minOf(len.toLong(), length - pos).toInt()
        bytes.copyInto(b, off, pos.toInt(), pos.toInt() + n)
        pos += n
        return n
    }

    fun readFully(b: ByteArray, off: Int = 0, len: Int = b.size) {
        if (pos + len > length) throw EOFException()
        read(b, off, len)
    }

    fun readInt(): Int {
        val b = ByteArray(4).also(::readFully)
        return ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
    }

    fun readUnsignedByte(): Int = read().also { if (it < 0) throw EOFException() }

    fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
        if (!writable) throw IOException("Opened read-only")
        val end = (pos + len).toInt()
        if (end > bytes.size) bytes = bytes.copyOf(maxOf(end, bytes.size * 2))
        b.copyInto(bytes, pos.toInt(), off, off + len)
        pos = end.toLong()
        length = maxOf(length, end)
        dirty = true
    }

    fun write(b: Int) = write(byteArrayOf(b.toByte()))

    fun setLength(newLength: Long) {
        if (!writable) throw IOException("Opened read-only")
        if (newLength > bytes.size) bytes = bytes.copyOf(newLength.toInt())
        length = newLength.toInt()
        if (pos > length) pos = length.toLong()
        dirty = true
    }

    override fun close() {
        if (dirty) file.writeBytes(bytes.copyOf(length))
        dirty = false
    }
}
