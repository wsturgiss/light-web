package java.net

/** `java.net.URISyntaxException`: the input, and why it isn't a URI. */
class URISyntaxException(val input: String, val reason: String, val index: Int = -1) :
    Exception("$reason${if (index >= 0) " at index $index" else ""}: $input")

/**
 * `java.net.URI`: parsed by RFC 3986's grammar, rejecting what Java's rejects (spaces, `"<>\^`{|}`,
 * control characters, a `%` not followed by two hex digits). Non-ASCII characters are allowed,
 * as Java allows them, and percent-encoded as UTF-8 by [toASCIIString]. [resolve] follows RFC 3986
 * section 5.2, which Java's RFC 2396 resolution matches for the URLs tools resolve.
 */
class URI private constructor(
    private val string: String,
    val scheme: String?,
    val rawAuthority: String?,
    val rawPath: String?,
    val rawQuery: String?,
    val rawFragment: String?,
) : Comparable<URI> {
    constructor(str: String) : this(parse(str))
    private constructor(p: URI) : this(p.string, p.scheme, p.rawAuthority, p.rawPath, p.rawQuery, p.rawFragment)

    val isAbsolute: Boolean get() = scheme != null
    val isOpaque: Boolean get() = scheme != null && rawAuthority == null && !rawPath.orEmpty().startsWith("/")

    val authority: String? get() = rawAuthority?.let(::decode)
    val path: String? get() = rawPath?.let(::decode)
    val query: String? get() = rawQuery?.let(::decode)
    val fragment: String? get() = rawFragment?.let(::decode)
    val rawUserInfo: String? get() = rawAuthority?.substringBefore('@', "")?.takeIf { '@' in rawAuthority }
    val userInfo: String? get() = rawUserInfo?.let(::decode)

    /** Null when there's no authority, as Java's is for an authority that isn't a server. */
    val host: String? get() {
        val a = rawAuthority ?: return null
        val hostPort = a.substringAfterLast('@')
        val h = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        return h.ifEmpty { null }
    }

    val port: Int get() {
        val a = rawAuthority ?: return -1
        val hostPort = a.substringAfterLast('@')
        val afterHost = if (hostPort.startsWith("[")) hostPort.substringAfter(']', "") else hostPort.substringAfter(':', "").let { if (it.isEmpty()) "" else ":$it" }
        return afterHost.removePrefix(":").toIntOrNull() ?: -1
    }

    val rawSchemeSpecificPart: String get() = buildString {
        rawAuthority?.let { append("//").append(it) }
        append(rawPath.orEmpty())
        rawQuery?.let { append('?').append(it) }
    }

    fun resolve(str: String): URI = resolve(URI(str))

    /** RFC 3986 section 5.2.2. */
    fun resolve(ref: URI): URI {
        if (isOpaque || ref.isOpaque) return ref
        if (ref.scheme != null) return of(ref.scheme, ref.rawAuthority, removeDots(ref.rawPath.orEmpty()), ref.rawQuery, ref.rawFragment)
        if (ref.rawAuthority != null) return of(scheme, ref.rawAuthority, removeDots(ref.rawPath.orEmpty()), ref.rawQuery, ref.rawFragment)
        val refPath = ref.rawPath.orEmpty()
        return when {
            refPath.isEmpty() -> of(scheme, rawAuthority, rawPath.orEmpty(), ref.rawQuery ?: rawQuery, ref.rawFragment)
            refPath.startsWith("/") -> of(scheme, rawAuthority, removeDots(refPath), ref.rawQuery, ref.rawFragment)
            else -> {
                val base = rawPath.orEmpty()
                val merged = if (rawAuthority != null && base.isEmpty()) "/$refPath" else base.substringBeforeLast('/', "").let { if ('/' in base) "$it/$refPath" else refPath }
                of(scheme, rawAuthority, removeDots(merged), ref.rawQuery, ref.rawFragment)
            }
        }
    }

    fun normalize(): URI = of(scheme, rawAuthority, removeDots(rawPath.orEmpty()), rawQuery, rawFragment)

    fun toURL(): URL {
        if (!isAbsolute) throw IllegalArgumentException("URI is not absolute")
        return URL(toString())
    }

    /** The URI with every non-ASCII character percent-encoded as UTF-8. */
    fun toASCIIString(): String {
        if (string.all { it.code < 0x80 }) return string
        return buildString {
            var k = 0
            while (k < string.length) {
                if (string[k].code < 0x80) { append(string[k++]); continue }
                // A run of non-ASCII characters at once, so a surrogate pair is encoded as one character.
                val start = k
                while (k < string.length && string[k].code >= 0x80) k++
                string.substring(start, k).encodeToByteArray().forEach { append('%').append(HEX[(it.toInt() shr 4) and 0xf]).append(HEX[it.toInt() and 0xf]) }
            }
        }
    }

    override fun toString() = string
    override fun equals(other: Any?) = other is URI && other.string == string
    override fun hashCode() = string.hashCode()
    override fun compareTo(other: URI) = string.compareTo(other.string)

    companion object {
        fun create(str: String): URI = try {
            URI(str)
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException(e.message, e)
        }

        private val HEX = "0123456789ABCDEF".toCharArray()
        private const val ILLEGAL = " \"<>\\^`{|}"
        private val SHAPE = Regex("""^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""", RegexOption.DOT_MATCHES_ALL)

        private fun parse(str: String): URI {
            for ((k, c) in str.withIndex()) {
                if (c.code < 0x20 || c.code == 0x7f || c in ILLEGAL) throw URISyntaxException(str, "Illegal character in ${partAt(str, k)}", k)
                if (c == '%' && !(k + 2 < str.length && str[k + 1].isHex() && str[k + 2].isHex())) throw URISyntaxException(str, "Malformed escape pair", k)
            }
            if (str.isEmpty()) return URI("", null, null, "", null, null)
            if (str.startsWith(":")) throw URISyntaxException(str, "Expected scheme name", 0)
            val m = SHAPE.find(str) ?: throw URISyntaxException(str, "Malformed URI")
            val (scheme, authority, path, query, fragment) = m.destructured
            val hasAuthority = str.removePrefix(if (scheme.isNotEmpty()) "$scheme:" else "").startsWith("//")
            if (scheme.isNotEmpty() && !hasAuthority && path.isEmpty() && m.groups[4] == null) throw URISyntaxException(str, "Expected scheme-specific part", str.length)
            return URI(
                str,
                scheme.ifEmpty { null },
                if (hasAuthority) authority.ifEmpty { null } else null,
                path,
                m.groups[4]?.value,
                m.groups[5]?.value,
            ).also { u ->
                if (hasAuthority && authority.isEmpty() && path.isEmpty()) throw URISyntaxException(str, "Expected authority", str.length)
                if (u.rawAuthority != null && u.rawAuthority.substringAfterLast('@').let { hp -> !hp.startsWith("[") && hp.substringAfter(':', "").let { p -> p.isNotEmpty() && p.toIntOrNull() == null } })
                    throw URISyntaxException(str, "Illegal character in port number")
            }
        }

        private fun partAt(str: String, k: Int): String = when {
            '#' in str && k > str.indexOf('#') -> "fragment"
            '?' in str && k > str.indexOf('?') -> "query"
            else -> "path"
        }

        private fun of(scheme: String?, authority: String?, path: String, query: String?, fragment: String?): URI {
            val s = buildString {
                scheme?.let { append(it).append(':') }
                authority?.let { append("//").append(it) }
                append(path)
                query?.let { append('?').append(it) }
                fragment?.let { append('#').append(it) }
            }
            return URI(s, scheme, authority, path, query, fragment)
        }

        /** RFC 3986 section 5.2.4. */
        private fun removeDots(path: String): String {
            if ("." !in path) return path
            val input = StringBuilder(path)
            val out = StringBuilder()
            while (input.isNotEmpty()) {
                when {
                    input.startsWith("../") -> input.deleteRange(0, 3)
                    input.startsWith("./") -> input.deleteRange(0, 2)
                    input.startsWith("/./") -> input.deleteRange(0, 2)
                    input.toString() == "/." -> input.setRange(0, 2, "/")
                    input.startsWith("/../") -> { input.deleteRange(0, 3); out.setLength(maxOf(out.lastIndexOf("/"), 0)) }
                    input.toString() == "/.." -> { input.setRange(0, 3, "/"); out.setLength(maxOf(out.lastIndexOf("/"), 0)) }
                    input.toString() == "." || input.toString() == ".." -> input.setLength(0)
                    else -> {
                        val next = input.indexOf("/", if (input.startsWith("/")) 1 else 0).let { if (it < 0) input.length else it }
                        out.append(input, 0, next)
                        input.deleteRange(0, next)
                    }
                }
            }
            return out.toString()
        }

        private fun decode(s: String): String {
            if ('%' !in s) return s
            val bytes = ArrayList<Byte>()
            val out = StringBuilder()
            var k = 0
            fun flush() {
                if (bytes.isNotEmpty()) { out.append(bytes.toByteArray().decodeToString()); bytes.clear() }
            }
            while (k < s.length) {
                if (s[k] == '%' && k + 2 < s.length) {
                    bytes += s.substring(k + 1, k + 3).toInt(16).toByte()
                    k += 3
                } else {
                    flush()
                    out.append(s[k++])
                }
            }
            flush()
            return out.toString()
        }
    }
}

private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
