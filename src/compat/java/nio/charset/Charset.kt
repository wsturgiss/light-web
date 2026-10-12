package java.nio.charset

/**
 * `java.nio.charset.Charset` for the charsets tools name: UTF-8, US-ASCII, ISO-8859-1 and
 * UTF-16 (BE, or by its byte order mark). Kotlin's `Charsets.UTF_8` reaches these through the
 * JVM-name template.
 */
class Charset private constructor(private val canonical: String) {
    fun name(): String = canonical
    fun displayName(): String = canonical
    override fun toString() = canonical

    fun encode(s: String): ByteArray = when (this) {
        UTF_8 -> s.encodeToByteArray()
        UTF_16 -> ByteArray(s.length * 2 + 2).also {
            it[0] = 0xfe.toByte(); it[1] = 0xff.toByte()
            for (i in s.indices) { it[2 + 2 * i] = (s[i].code shr 8).toByte(); it[3 + 2 * i] = s[i].code.toByte() }
        }
        UTF_16BE, UTF_16LE -> ByteArray(s.length * 2).also {
            val little = this == UTF_16LE
            for (i in s.indices) {
                it[2 * i + (if (little) 1 else 0)] = (s[i].code shr 8).toByte()
                it[2 * i + (if (little) 0 else 1)] = s[i].code.toByte()
            }
        }
        else -> {
            val max = if (this == US_ASCII) 0x7f else 0xff
            ByteArray(s.length) { i -> s[i].code.let { if (it > max) '?'.code else it }.toByte() }
        }
    }

    fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String = when (this) {
        UTF_8 -> bytes.decodeToString(offset, offset + length)
        UTF_16, UTF_16BE, UTF_16LE -> {
            var i = offset
            var little = this == UTF_16LE
            if (this == UTF_16 && length >= 2) {
                val b0 = bytes[offset].toInt() and 0xff
                val b1 = bytes[offset + 1].toInt() and 0xff
                if (b0 == 0xfe && b1 == 0xff) i += 2 else if (b0 == 0xff && b1 == 0xfe) { i += 2; little = true }
            }
            buildString {
                while (i + 1 < offset + length) {
                    val hi = bytes[if (little) i + 1 else i].toInt() and 0xff
                    val lo = bytes[if (little) i else i + 1].toInt() and 0xff
                    append(((hi shl 8) or lo).toChar())
                    i += 2
                }
            }
        }
        else -> {
            val max = if (this == US_ASCII) 0x7f else 0xff
            buildString(length) {
                for (i in offset until offset + length) append((bytes[i].toInt() and 0xff).let { if (it > max) '�'.code else it }.toChar())
            }
        }
    }

    override fun equals(other: Any?) = other is Charset && other.canonical == canonical
    override fun hashCode() = canonical.hashCode()

    companion object {
        val UTF_8 = Charset("UTF-8")
        val US_ASCII = Charset("US-ASCII")
        val ISO_8859_1 = Charset("ISO-8859-1")
        val UTF_16 = Charset("UTF-16")
        val UTF_16BE = Charset("UTF-16BE")
        val UTF_16LE = Charset("UTF-16LE")

        private val ALIASES = mapOf(
            "utf8" to UTF_8, "utf-8" to UTF_8,
            "us-ascii" to US_ASCII, "ascii" to US_ASCII,
            "iso-8859-1" to ISO_8859_1, "latin1" to ISO_8859_1, "iso8859_1" to ISO_8859_1,
            "utf-16" to UTF_16, "utf16" to UTF_16,
            "utf-16be" to UTF_16BE, "utf-16le" to UTF_16LE,
        )

        fun forName(charsetName: String): Charset =
            ALIASES[charsetName.lowercase()] ?: throw UnsupportedCharsetException(charsetName)

        fun isSupported(charsetName: String): Boolean = charsetName.lowercase() in ALIASES

        fun defaultCharset(): Charset = UTF_8
    }
}

class UnsupportedCharsetException(val charsetName: String) : IllegalArgumentException(charsetName)

object StandardCharsets {
    val UTF_8 = Charset.UTF_8
    val US_ASCII = Charset.US_ASCII
    val ISO_8859_1 = Charset.ISO_8859_1
    val UTF_16 = Charset.UTF_16
}
