package okhttp3

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okio.BufferedSource

// OkHttp's API, as far as light-amp uses it, so its code compiles unchanged. There's no OkHttp in
// the browser: a page can't open sockets, and a call can't block. Ktor's OkHttp engine is a web
// engine here (io.ktor.client.engine.okhttp), which only reads the builder; a call made through
// OkHttp directly ([Call.execute]) fails as an IOException. light-amp makes those only for
// downloads and for its loopback stream proxy, and treats the failure as it would a dead network.

class OkHttpClient private constructor(builder: Builder) {
    val connectTimeoutMillis: Int = builder.connectTimeoutMs.toInt()
    val readTimeoutMillis: Int = builder.readTimeoutMs.toInt()

    fun newCall(request: Request): Call = Call(request)

    fun newBuilder(): Builder = Builder()

    class Builder {
        internal var connectTimeoutMs = 10_000L
        internal var readTimeoutMs = 10_000L

        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply { connectTimeoutMs = unit.toMillis(timeout) }
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply { readTimeoutMs = unit.toMillis(timeout) }
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun dns(dns: Dns): Builder = this
        fun socketFactory(socketFactory: SocketFactory): Builder = this
        fun connectionPool(connectionPool: ConnectionPool): Builder = this
        fun addInterceptor(interceptor: Interceptor): Builder = this
        fun addNetworkInterceptor(interceptor: Interceptor): Builder = this
        fun followRedirects(followRedirects: Boolean): Builder = this
        fun followSslRedirects(followProtocolRedirects: Boolean): Builder = this
        fun retryOnConnectionFailure(retryOnConnectionFailure: Boolean): Builder = this
        fun build(): OkHttpClient = OkHttpClient(this)
    }
}

class ConnectionPool(maxIdleConnections: Int = 5, keepAliveDuration: Long = 5, timeUnit: TimeUnit = TimeUnit.MINUTES) {
    fun evictAll() {}
    fun connectionCount(): Int = 0
    fun idleConnectionCount(): Int = 0
}

interface Dns {
    fun lookup(hostname: String): List<InetAddress>

    companion object {
        val SYSTEM: Dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList()
        }
    }
}

fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
    }
}

class Call internal constructor(private val request: Request) {
    private var canceled = false

    fun request(): Request = request

    fun execute(): Response =
        throw IOException("Can't fetch ${request.url} directly in the browser: there's no OkHttp here")

    fun cancel() { canceled = true }

    fun isCanceled(): Boolean = canceled
}

class Request private constructor(val url: HttpUrl, val method: String, val headers: Map<String, String>) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun newBuilder(): Builder = Builder().also { b -> b.url = url; b.method = method; b.headers.putAll(headers) }

    class Builder {
        internal var url: HttpUrl? = null
        internal var method = "GET"
        internal val headers = LinkedHashMap<String, String>()

        fun url(url: String): Builder = apply { this.url = HttpUrl.get(url) }
        fun url(url: HttpUrl): Builder = apply { this.url = url }
        fun header(name: String, value: String): Builder = apply { headers[name] = value }
        fun addHeader(name: String, value: String): Builder = apply { headers[name] = value }
        fun removeHeader(name: String): Builder = apply { headers.remove(name) }
        fun get(): Builder = apply { method = "GET" }
        fun head(): Builder = apply { method = "HEAD" }
        fun build(): Request = Request(checkNotNull(url) { "url == null" }, method, LinkedHashMap(headers))
    }
}

class Response private constructor(
    val request: Request,
    val code: Int,
    val message: String,
    private val headers: Map<String, String>,
    val body: ResponseBody,
) : Closeable {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String, defaultValue: String? = null): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: defaultValue

    fun newBuilder(): Builder = Builder().also { b ->
        b.request = request; b.code = code; b.message = message; b.headers.putAll(headers); b.body = body
    }

    override fun close() = body.close()

    class Builder {
        internal var request: Request? = null
        internal var code = -1
        internal var message = ""
        internal val headers = LinkedHashMap<String, String>()
        internal var body: ResponseBody? = null

        fun request(request: Request): Builder = apply { this.request = request }
        fun code(code: Int): Builder = apply { this.code = code }
        fun message(message: String): Builder = apply { this.message = message }
        fun header(name: String, value: String): Builder = apply { headers[name] = value }
        fun body(body: ResponseBody): Builder = apply { this.body = body }
        fun build(): Response = Response(checkNotNull(request), code, message, LinkedHashMap(headers), checkNotNull(body))
    }
}

abstract class ResponseBody : Closeable {
    abstract fun contentType(): MediaType?
    abstract fun contentLength(): Long
    abstract fun source(): BufferedSource

    fun byteStream(): InputStream = object : InputStream() {
        private val source = source()
        override fun read(): Int = if (source.exhausted()) -1 else source.readByte().toInt() and 0xff
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = source.read(b, off, len)
            return if (n <= 0) -1 else n
        }
        override fun close() = source.close()
    }

    fun bytes(): ByteArray {
        val source = source()
        try {
            return source.readByteArray()
        } finally {
            source.close()
        }
    }

    fun string(): String = bytes().decodeToString()

    override fun close() = source().close()
}

class MediaType private constructor(private val text: String, val type: String, val subtype: String) {
    override fun toString() = text

    companion object {
        fun String.toMediaType(): MediaType = parse(this) ?: throw IllegalArgumentException("No subtype found for: \"$this\"")

        fun String.toMediaTypeOrNull(): MediaType? = parse(this)

        fun get(text: String): MediaType = text.toMediaType()

        fun parse(text: String): MediaType? {
            val main = text.substringBefore(';').trim()
            val slash = main.indexOf('/')
            if (slash <= 0 || slash == main.length - 1) return null
            return MediaType(text, main.substring(0, slash).lowercase(), main.substring(slash + 1).lowercase())
        }
    }
}

/** An http(s) URL, parsed as far as light-amp asks of one. */
class HttpUrl private constructor(private val text: String, val scheme: String, val host: String, val port: Int) {
    val isHttps: Boolean get() = scheme == "https"

    override fun toString() = text
    override fun equals(other: Any?) = other is HttpUrl && other.text == text
    override fun hashCode() = text.hashCode()

    companion object {
        fun String.toHttpUrlOrNull(): HttpUrl? = parse(this)

        fun String.toHttpUrl(): HttpUrl = parse(this) ?: throw IllegalArgumentException("Invalid URL: $this")

        fun get(url: String): HttpUrl = url.toHttpUrl()

        fun parse(url: String): HttpUrl? {
            val m = Regex("""^(https?)://(\[[^\]]+]|[^/:?#]+)(?::(\d+))?([/?#].*)?$""", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
            val scheme = m.groupValues[1].lowercase()
            val host = m.groupValues[2].lowercase().removePrefix("[").removeSuffix("]")
            if (host.isEmpty()) return null
            val port = m.groupValues[3].toIntOrNull() ?: if (scheme == "https") 443 else 80
            return HttpUrl(url.trim(), scheme, host, port)
        }
    }
}
