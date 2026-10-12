package java.net

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import java.security.cert.CertificateException

open class MalformedURLException(message: String? = null) : IOException(message)
open class SocketException(message: String? = null) : IOException(message)
open class ConnectException(message: String? = null) : SocketException(message)
open class UnknownHostException(message: String? = null) : IOException(message)
open class SocketTimeoutException(message: String? = null) : IOException(message)

/** `java.net.URL`, for opening a connection. */
class URL(spec: String) {
    private val uri: URI = try {
        URI(spec).also { if (it.scheme == null) throw MalformedURLException("no protocol: $spec") }
    } catch (e: URISyntaxException) {
        throw MalformedURLException(e.message)
    }

    val protocol: String get() = uri.scheme!!
    val host: String get() = uri.host.orEmpty()
    val port: Int get() = uri.port
    val path: String get() = uri.rawPath.orEmpty()
    val query: String? get() = uri.rawQuery

    fun toURI(): URI = uri

    fun openConnection(): URLConnection = when (protocol.lowercase()) {
        "http", "https" -> WebHttpURLConnection(this)
        else -> throw MalformedURLException("unknown protocol: $protocol")
    }

    override fun toString() = uri.toString()
    override fun equals(other: Any?) = other is URL && other.uri == uri
    override fun hashCode() = uri.hashCode()
}

abstract class URLConnection(val url: URL) {
    var connectTimeout: Int = 0
    var readTimeout: Int = 0
    abstract fun connect()
    abstract fun setRequestProperty(key: String, value: String?)
    abstract fun getHeaderField(name: String): String?
    abstract val inputStream: InputStream
    abstract val contentLengthLong: Long
    val contentLength: Int get() = contentLengthLong.let { if (it > Int.MAX_VALUE) -1 else it.toInt() }
    val contentType: String? get() = getHeaderField("Content-Type")
}

abstract class HttpURLConnection(url: URL) : URLConnection(url) {
    var instanceFollowRedirects: Boolean = true
    var requestMethod: String = "GET"
    abstract val responseCode: Int
    abstract val responseMessage: String?
    abstract val errorStream: InputStream?
    abstract fun disconnect()

    companion object {
        const val HTTP_OK = 200
        const val HTTP_MOVED_PERM = 301
        const val HTTP_MOVED_TEMP = 302
        const val HTTP_SEE_OTHER = 303
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_INTERNAL_ERROR = 500
    }
}

/**
 * A GET over a synchronous XMLHttpRequest: Android code fetches with a blocking call, and a page
 * has no thread to block, so the page waits until the response is in.
 *
 * Through light-web's dev server (tool/serve.mjs) a request goes to its `/proxy`, which fetches
 * the URL itself: the page can then reach sites that don't allow cross-origin requests, the
 * request carries the tool's User-Agent, redirects come back to the tool to follow (or not, by
 * [instanceFollowRedirects]) as on the phone, and a failure to connect comes back as the kind of
 * failure it was. Served without the proxy, a request goes straight to the site: the browser
 * follows redirects itself, sends its own User-Agent, and a site that doesn't allow the page
 * fails as an unreachable one.
 */
private class WebHttpURLConnection(url: URL) : HttpURLConnection(url) {
    private val requestHeaders = LinkedHashMap<String, String>()
    private var status = -1
    private var headers: Map<String, String> = emptyMap()
    private var body = ByteArray(0)
    private var connected = false

    override fun setRequestProperty(key: String, value: String?) {
        if (value == null) requestHeaders.remove(key) else requestHeaders[key] = value
    }

    override fun connect() {
        if (connected) return
        if (requestMethod != "GET") throw IOException("Only GET is supported on the web")
        val proxied = lightweb.compat.WebHttp.proxied
        val result = xhrGet(url.toString(), proxied, requestHeaders["User-Agent"].orEmpty())
        resultError(result)?.let { kind -> throw failure(kind, resultMessage(result)) }
        status = resultStatus(result)
        headers = parseHeaders(resultHeaders(result))
        // x-user-defined text: each byte is one char, U+0000-U+007F or U+F780-U+F7FF.
        val text = resultBody(result)
        body = ByteArray(text.length) { text[it].code.toByte() }
        connected = true
    }

    private fun failure(kind: String, message: String): IOException = when (kind) {
        "dns" -> UnknownHostException("${url.host}: $message")
        "refused" -> ConnectException("Failed to connect to ${url.host}: $message")
        "cert" -> SSLHandshakeException(message, CertificateException(message))
        "tls" -> SSLException(message)
        "timeout" -> SocketTimeoutException(message)
        else -> IOException("Couldn't reach ${url.host}: $message")
    }

    override val responseCode: Int get() { connect(); return status }
    override val responseMessage: String? get() { connect(); return null }

    override fun getHeaderField(name: String): String? {
        connect()
        return headers[name.lowercase()]
    }

    override val contentLengthLong: Long get() { connect(); return body.size.toLong() }

    /** As Java's: a 4xx or 5xx throws, its body is in [errorStream]. */
    override val inputStream: InputStream get() {
        connect()
        if (status == 404 || status == 410) throw FileNotFoundException(url.toString())
        if (status >= 400) throw IOException("Server returned HTTP response code: $status for URL: $url")
        return ByteArrayInputStream(body)
    }

    override val errorStream: InputStream? get() = if (connected && status >= 400 && body.isNotEmpty()) ByteArrayInputStream(body) else null

    override fun disconnect() {}
}

private fun parseHeaders(raw: String): Map<String, String> = raw.split("\r\n").mapNotNull { line ->
    val colon = line.indexOf(':')
    if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
}.toMap()

private fun xhrGet(url: String, viaProxy: Boolean, userAgent: String): JsAny = js(
    """{
        const x = new XMLHttpRequest();
        x.open('GET', viaProxy ? '/proxy?url=' + encodeURIComponent(url) : url, false);
        x.overrideMimeType('text/plain; charset=x-user-defined');
        // The proxy answers only requests carrying this: other sites can't send it (see tool/serve.mjs).
        if (viaProxy) x.setRequestHeader('X-Lightweb-Proxy', '1');
        if (viaProxy && userAgent) x.setRequestHeader('X-Proxy-User-Agent', userAgent);
        try {
            x.send();
        } catch (e) {
            return { error: 'unreachable', message: String(e && e.message || e) };
        }
        if (viaProxy) {
            const error = x.getResponseHeader('X-Proxy-Error');
            if (error) return { error, message: x.responseText };
            const status = Number(x.getResponseHeader('X-Proxy-Status'));
            return { status, headers: x.getAllResponseHeaders(), body: x.responseText };
        }
        if (x.status === 0) return { error: 'unreachable', message: 'no response (offline, or the site does not allow this page)' };
        return { status: x.status, headers: x.getAllResponseHeaders(), body: x.responseText };
    }"""
)

private fun resultError(r: JsAny): String? = js("r.error ?? null")
private fun resultMessage(r: JsAny): String = js("String(r.message ?? '')")
private fun resultStatus(r: JsAny): Int = js("r.status")
private fun resultHeaders(r: JsAny): String = js("r.headers")
private fun resultBody(r: JsAny): String = js("r.body")

/** `java.net.URLEncoder`: application/x-www-form-urlencoded, as Java encodes it. */
object URLEncoder {
    fun encode(s: String, enc: String): String {
        require(enc.equals("UTF-8", ignoreCase = true)) { "Only UTF-8 is supported: $enc" }
        return encode(s)
    }

    fun encode(s: String, charset: java.nio.charset.Charset): String = encode(s, charset.name())

    private fun encode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xff
            when {
                c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in ".-*_" -> append(c.toChar())
                c == ' '.code -> append('+')
                else -> append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
            }
        }
    }
}
