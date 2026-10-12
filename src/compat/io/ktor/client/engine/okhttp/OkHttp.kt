package io.ktor.client.engine.okhttp

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import lightweb.compat.WebHttp
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Ktor's OkHttp engine, by name: a tool's `HttpClient(OkHttp) { engine { config { ... } } }`
 * compiles unchanged, and its requests go out through the browser ([WebHttp]: fetch, through
 * light-web's proxy when there is one). The OkHttp builder it's given is run for its timeouts;
 * its DNS, socket factory, connection pool and interceptors have nothing to act on in a browser.
 */
object OkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine = WebFetchEngine(OkHttpConfig().apply(block))
}

class OkHttpConfig : HttpClientEngineConfig() {
    internal var builder: OkHttpClient.Builder.() -> Unit = {}

    var preconfigured: OkHttpClient? = null
    var clientCacheSize: Int = 10

    fun config(block: OkHttpClient.Builder.() -> Unit) {
        val previous = builder
        builder = { previous(); block() }
    }

    fun addInterceptor(interceptor: Interceptor) {}
    fun addNetworkInterceptor(interceptor: Interceptor) {}
}

private class WebFetchEngine(override val config: OkHttpConfig) : HttpClientEngineBase("lightweb-fetch") {
    /** The builder's connect and read timeouts, as one bound on the whole request. */
    private val timeoutMs: Long = OkHttpClient.Builder().apply(config.builder).build()
        .let { it.connectTimeoutMillis.toLong() + it.readTimeoutMillis.toLong() }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val headers = mutableListOf<Pair<String, String>>()
        mergeHeaders(data.headers, data.body) { key, value ->
            if (!key.equals("Content-Length", ignoreCase = true)) headers += key to value
        }
        val body = bodyBytes(data.body, callContext)
        val response = if (timeoutMs > 0) {
            withTimeout(timeoutMs) { WebHttp.fetch(data.method.value, data.url.toString(), headers, body) }
        } else {
            WebHttp.fetch(data.method.value, data.url.toString(), headers, body)
        }
        val responseHeaders = Headers.build { response.headers.forEach { (k, v) -> append(k, v) } }
        return HttpResponseData(
            HttpStatusCode.fromValue(response.status),
            GMTDate(),
            responseHeaders,
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(response.body),
            callContext,
        )
    }

    private suspend fun bodyBytes(content: OutgoingContent, context: kotlin.coroutines.CoroutineContext): ByteArray? = when (content) {
        is OutgoingContent.NoContent -> null
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readByteArray()
        is OutgoingContent.WriteChannelContent -> {
            val channel = ByteChannel()
            CoroutineScope(context).launch { content.writeTo(channel); channel.flushAndClose() }
            channel.readRemaining().readByteArray()
        }
        is OutgoingContent.ContentWrapper -> bodyBytes(content.delegate(), context)
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedOperationException("Protocol upgrades aren't supported in the browser")
    }
}
