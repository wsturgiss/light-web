package java.util.zip

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

open class ZipException(message: String? = null) : IOException(message)

/** `java.util.zip.ZipEntry`, as read from a zip's central directory. */
class ZipEntry(val name: String) {
    var size: Long = -1
    var compressedSize: Long = -1
    var crc: Long = -1
    var method: Int = -1
    var time: Long = -1
    val isDirectory: Boolean get() = name.endsWith("/")
    internal var localHeaderOffset = 0L

    override fun toString() = name

    companion object {
        const val STORED = 0
        const val DEFLATED = 8
    }
}

/**
 * `java.util.zip.ZipFile`: reads the central directory when opened (a file without one throws
 * [ZipException], as Java's does) and an entry's data when it's opened. Stored and deflated
 * entries; no ZIP64, no encryption. The browser's own decompression is asynchronous only, so
 * deflate is decoded here ([inflate]).
 */
class ZipFile(file: File) : Closeable {
    constructor(name: String) : this(File(name))

    val name: String = file.path
    private val data: ByteArray = file.readBytes()
    private val byName = LinkedHashMap<String, ZipEntry>()
    private var closed = false

    init {
        readCentralDirectory()
    }

    fun getEntry(name: String): ZipEntry? {
        check(!closed) { "zip file closed" }
        return byName[name] ?: byName["$name/"]
    }

    fun size(): Int = byName.size

    fun getInputStream(entry: ZipEntry): InputStream {
        check(!closed) { "zip file closed" }
        val e = byName[entry.name] ?: throw ZipException("no such entry: ${entry.name}")
        val at = e.localHeaderOffset.toInt()
        if (at + 30 > data.size || u32(at) != LOCAL_SIG) throw ZipException("invalid LOC header (bad signature)")
        val start = at + 30 + u16(at + 26) + u16(at + 28)
        val end = start + e.compressedSize.toInt()
        if (end > data.size) throw ZipException("invalid entry compressed size")
        return when (e.method) {
            ZipEntry.STORED -> ByteArrayInputStream(data, start, e.compressedSize.toInt())
            ZipEntry.DEFLATED -> ByteArrayInputStream(inflate(data, start, end, e.size.toInt()))
            else -> throw ZipException("invalid compression method ${e.method}")
        }
    }

    override fun close() {
        closed = true
    }

    private fun readCentralDirectory() {
        val min = maxOf(0, data.size - 22 - 0xffff)
        var eocd = data.size - 22
        while (eocd >= min && u32(eocd) != END_SIG) eocd--
        if (eocd < min) throw ZipException("zip END header not found")
        val count = u16(eocd + 10)
        val cdSize = u32(eocd + 12)
        val cdOffset = u32(eocd + 16)
        if (cdOffset == 0xffffffffL || count == 0xffff) throw ZipException("ZIP64 isn't supported")
        if (cdOffset + cdSize > eocd) throw ZipException("invalid END header (bad central directory offset)")
        var p = cdOffset.toInt()
        repeat(count) {
            if (p + 46 > data.size || u32(p) != CENTRAL_SIG) throw ZipException("invalid CEN header (bad signature)")
            val nameLen = u16(p + 28)
            val extraLen = u16(p + 30)
            val commentLen = u16(p + 32)
            if (p + 46 + nameLen > data.size) throw ZipException("invalid CEN header (bad entry name)")
            val entry = ZipEntry(data.decodeToString(p + 46, p + 46 + nameLen)).apply {
                method = u16(p + 10)
                time = dosTime(u16(p + 12), u16(p + 14))
                crc = u32(p + 16)
                compressedSize = u32(p + 20)
                size = u32(p + 24)
                localHeaderOffset = u32(p + 42)
            }
            byName.getOrPut(entry.name) { entry }
            p += 46 + nameLen + extraLen + commentLen
        }
    }

    private fun u16(i: Int): Int = (data[i].toInt() and 0xff) or ((data[i + 1].toInt() and 0xff) shl 8)
    private fun u32(i: Int): Long = (u16(i).toLong()) or (u16(i + 2).toLong() shl 16)

    private companion object {
        const val LOCAL_SIG = 0x04034b50L
        const val CENTRAL_SIG = 0x02014b50L
        const val END_SIG = 0x06054b50L

        /** A DOS date and time, as local time; close enough for what tools read it for. */
        fun dosTime(time: Int, date: Int): Long {
            val year = ((date shr 9) and 0x7f) + 1980
            val month = (date shr 5) and 0x0f
            val day = date and 0x1f
            val days = daysFromCivil(year, month, day)
            return ((days * 24 + ((time shr 11) and 0x1f)) * 60 + ((time shr 5) and 0x3f)) * 60_000L + (time and 0x1f) * 2_000L
        }

        fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
            val y = if (m <= 2) y0 - 1 else y0
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468
        }
    }
}

/**
 * Raw deflate (RFC 1951) from [src] between [start] and [end], [expected] bytes long if known
 * (-1 if not). A straightforward decoder after zlib's `puff`: correct, not the fastest.
 */
internal fun inflate(src: ByteArray, start: Int, end: Int, expected: Int): ByteArray {
    val s = Inflate(src, start, end, if (expected > 0) expected else (end - start) * 4)
    s.run()
    return s.output()
}

private class Huffman(val count: ShortArray, val symbol: ShortArray)

private class Inflate(private val src: ByteArray, private var pos: Int, private val end: Int, capacity: Int) {
    private var out = ByteArray(maxOf(capacity, 64))
    private var outLen = 0
    private var bitBuf = 0
    private var bitCnt = 0

    fun output(): ByteArray = if (outLen == out.size) out else out.copyOf(outLen)

    private fun bits(need: Int): Int {
        var v = bitBuf
        while (bitCnt < need) {
            if (pos >= end) throw ZipException("invalid stored block lengths: input ended")
            v = v or ((src[pos++].toInt() and 0xff) shl bitCnt)
            bitCnt += 8
        }
        bitBuf = v ushr need
        bitCnt -= need
        return v and ((1 shl need) - 1)
    }

    private fun put(b: Int) {
        if (outLen == out.size) out = out.copyOf(out.size * 2)
        out[outLen++] = b.toByte()
    }

    fun run() {
        do {
            val last = bits(1)
            when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LEN, FIXED_DIST)
                2 -> dynamic()
                else -> throw ZipException("invalid block type")
            }
        } while (last == 0)
    }

    private fun stored() {
        bitBuf = 0
        bitCnt = 0
        if (pos + 4 > end) throw ZipException("invalid stored block lengths")
        val len = (src[pos].toInt() and 0xff) or ((src[pos + 1].toInt() and 0xff) shl 8)
        val nlen = (src[pos + 2].toInt() and 0xff) or ((src[pos + 3].toInt() and 0xff) shl 8)
        pos += 4
        if (len != (nlen.inv() and 0xffff)) throw ZipException("invalid stored block lengths")
        if (pos + len > end) throw ZipException("invalid stored block lengths")
        while (outLen + len > out.size) out = out.copyOf(out.size * 2)
        src.copyInto(out, outLen, pos, pos + len)
        outLen += len
        pos += len
    }

    private fun decode(h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..MAX_BITS) {
            code = code or bits(1)
            val count = h.count[len].toInt()
            if (code - count < first) return h.symbol[index + (code - first)].toInt()
            index += count
            first += count
            first = first shl 1
            code = code shl 1
        }
        throw ZipException("invalid code")
    }

    private fun codes(lencode: Huffman, distcode: Huffman) {
        while (true) {
            var symbol = decode(lencode)
            if (symbol < 256) {
                put(symbol)
            } else if (symbol == 256) {
                return
            } else {
                symbol -= 257
                if (symbol >= 29) throw ZipException("invalid literal/length code")
                val len = LBASE[symbol] + bits(LEXT[symbol])
                val ds = decode(distcode)
                if (ds >= 30) throw ZipException("invalid distance code")
                val dist = DBASE[ds] + bits(DEXT[ds])
                if (dist > outLen) throw ZipException("invalid distance too far back")
                while (outLen + len > out.size) out = out.copyOf(out.size * 2)
                var from = outLen - dist
                repeat(len) { out[outLen++] = out[from++] }
            }
        }
    }

    private fun dynamic() {
        val nlen = bits(5) + 257
        val ndist = bits(5) + 1
        val ncode = bits(4) + 4
        if (nlen > 286 || ndist > 30) throw ZipException("invalid code lengths set")
        val lengths = ShortArray(320)
        for (i in 0 until ncode) lengths[ORDER[i]] = bits(3).toShort()
        val lencode = build(lengths, 0, 19) ?: throw ZipException("invalid code lengths set")
        var index = 0
        val all = ShortArray(nlen + ndist)
        while (index < nlen + ndist) {
            var symbol = decode(lencode)
            if (symbol < 16) {
                all[index++] = symbol.toShort()
            } else {
                var len = 0
                when (symbol) {
                    16 -> {
                        if (index == 0) throw ZipException("invalid bit length repeat")
                        len = all[index - 1].toInt()
                        symbol = 3 + bits(2)
                    }
                    17 -> symbol = 3 + bits(3)
                    else -> symbol = 11 + bits(7)
                }
                if (index + symbol > nlen + ndist) throw ZipException("invalid bit length repeat")
                repeat(symbol) { all[index++] = len.toShort() }
            }
        }
        if (all[256].toInt() == 0) throw ZipException("invalid code -- missing end-of-block")
        val lc = build(all, 0, nlen) ?: throw ZipException("invalid literal/lengths set")
        val dc = build(all, nlen, ndist) ?: throw ZipException("invalid distances set")
        codes(lc, dc)
    }

    companion object {
        const val MAX_BITS = 15
        val LBASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LEXT = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DBASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        val DEXT = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
        val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

        /** A canonical Huffman code from code lengths; null if over-subscribed. Incomplete codes are allowed, as zlib allows them. */
        fun build(lengths: ShortArray, from: Int, n: Int): Huffman? {
            val count = ShortArray(MAX_BITS + 1)
            for (i in 0 until n) count[lengths[from + i].toInt()]++
            count[0] = 0
            var left = 1
            for (len in 1..MAX_BITS) {
                left = (left shl 1) - count[len]
                if (left < 0) return null
            }
            val offs = ShortArray(MAX_BITS + 1)
            for (len in 1 until MAX_BITS) offs[len + 1] = (offs[len] + count[len]).toShort()
            val symbol = ShortArray(n)
            for (i in 0 until n) {
                val l = lengths[from + i].toInt()
                if (l != 0) symbol[offs[l]++.toInt()] = i.toShort()
            }
            return Huffman(count, symbol)
        }

        val FIXED_LEN: Huffman
        val FIXED_DIST: Huffman

        init {
            val l = ShortArray(288)
            for (i in 0 until 144) l[i] = 8
            for (i in 144 until 256) l[i] = 9
            for (i in 256 until 280) l[i] = 7
            for (i in 280 until 288) l[i] = 8
            FIXED_LEN = build(l, 0, 288)!!
            FIXED_DIST = build(ShortArray(30) { 5 }, 0, 30)!!
        }
    }
}
