package lightweb.compat

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array

/**
 * HTTP for tools, asynchronously (fetch), through light-web's proxy when the page is served by
 * tool/serve.mjs: the proxy makes the request itself, so a site needn't allow cross-origin
 * requests, any header can be sent, and redirects come back unfollowed, as on the phone. Without
 * the proxy, requests go straight to the site, as far as the browser lets them.
 *
 * Ktor's engine (io.ktor.client.engine.okhttp) runs on this. java.net's HttpURLConnection stays
 * synchronous (an XMLHttpRequest), since the code calling it blocks.
 */
object WebHttp {
    class Response(val status: Int, val headers: List<Pair<String, String>>, val body: ByteArray)

    /** A failure to get any answer, of a kind tool/serve.mjs reports: dns, refused, cert, tls, timeout, blocked, unreachable. */
    class Failure(val kind: String, message: String) : java.io.IOException(message)

    /** Whether the page is served by tool/serve.mjs, which has `/proxy`. Asked once. */
    val proxied: Boolean by lazy { probeProxy() }

    suspend fun fetch(method: String, url: String, headers: List<Pair<String, String>>, body: ByteArray?): Response {
        val names = headers.map { it.first }.toJsArray()
        val values = headers.map { it.second }.toJsArray()
        val result = fetchJs(method, url, names, values, body?.toUint8Array(), proxied).await<JsAny>()
        resultError(result)?.let { throw Failure(it, resultMessage(result)) }
        val headerList = resultHeaders(result)
        val pairs = (0 until headerList.length step 2).map { headerList[it].toString() to headerList[it + 1].toString() }
        return Response(resultStatus(result), pairs, resultBody(result).toByteArray())
    }

    /** [url]'s status, from a request for its first byte; null if there was no answer. */
    suspend fun statusOf(url: String): Int? =
        try {
            fetch("GET", url, listOf("Range" to "bytes=0-0"), null).status
        } catch (e: Exception) {
            null
        }
}

private fun List<String>.toJsArray(): JsArray<JsString> {
    val a = JsArray<JsString>()
    forEachIndexed { i, s -> a[i] = s.toJsString() }
    return a
}

private fun probeProxy(): Boolean = js(
    """{
        try {
            const x = new XMLHttpRequest();
            x.open('GET', '/proxy?probe', false);
            x.setRequestHeader('X-Lightweb-Proxy', '1');
            x.send();
            return x.getResponseHeader('X-Lightweb-Proxy') === '1';
        } catch (e) {
            return false;
        }
    }"""
)

private fun fetchJs(method: String, url: String, names: JsArray<JsString>, values: JsArray<JsString>, body: Uint8Array?, viaProxy: Boolean): Promise<JsAny> = js(
    """(async () => {
        const headers = new Headers();
        for (let i = 0; i < names.length; i++) {
            if (viaProxy) headers.append('X-Proxy-H-' + names[i], values[i]);
            else headers.append(names[i], values[i]);
        }
        if (viaProxy) headers.set('X-Lightweb-Proxy', '1');
        const init = { method, headers, redirect: 'manual', cache: 'no-store' };
        if (body !== null && method !== 'GET' && method !== 'HEAD') init.body = body;
        let r;
        try {
            r = await fetch(viaProxy ? '/proxy?url=' + encodeURIComponent(url) : url, init);
        } catch (e) {
            return { error: 'unreachable', message: String(e && e.message || e) };
        }
        if (viaProxy) {
            const error = r.headers.get('X-Proxy-Error');
            if (error) return { error, message: await r.text() };
        } else if (r.type === 'opaqueredirect') {
            return { error: 'unreachable', message: 'redirected, and the browser hides where to (no proxy)' };
        }
        const out = [];
        r.headers.forEach((v, k) => { if (!/^x-proxy-/i.test(k)) out.push(k, v); });
        const status = viaProxy ? Number(r.headers.get('X-Proxy-Status')) : r.status;
        return { status, headers: out, body: new Uint8Array(await r.arrayBuffer()) };
    })()"""
)

private fun resultError(r: JsAny): String? = js("r.error ?? null")
private fun resultMessage(r: JsAny): String = js("String(r.message ?? '')")
private fun resultStatus(r: JsAny): Int = js("r.status")
private fun resultHeaders(r: JsAny): JsArray<JsString> = js("r.headers")
private fun resultBody(r: JsAny): Uint8Array = js("r.body")
