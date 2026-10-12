package javax.net.ssl

import java.io.IOException

open class SSLException(message: String? = null, cause: Throwable? = null) : IOException(message, cause)
open class SSLHandshakeException(message: String? = null, cause: Throwable? = null) : SSLException(message, cause)
open class SSLPeerUnverifiedException(message: String? = null) : SSLException(message)

// TLS set-up, so code that makes its own TLS context compiles. A page can't: the browser does
// TLS itself, and every algorithm lookup below fails with NoSuchAlgorithmException.
// light-amp builds one only for its loopback stream proxy, which falls back when this fails.

private fun noTls(what: String): Nothing = throw java.security.NoSuchAlgorithmException("$what isn't available in the browser")

interface KeyManager
interface TrustManager

interface X509TrustManager : TrustManager {
    fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?)
    fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?)
    fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>
    val acceptedIssuers: Array<java.security.cert.X509Certificate> get() = getAcceptedIssuers()
}

abstract class X509ExtendedTrustManager : X509TrustManager {
    abstract fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?, socket: java.net.Socket?)
    abstract fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?, socket: java.net.Socket?)
    abstract fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?, engine: SSLEngine?)
    abstract fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?, engine: SSLEngine?)
}

abstract class SSLEngine

abstract class SSLSocketFactory : javax.net.SocketFactory() {
    abstract fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket
}

abstract class SSLServerSocketFactory {
    abstract fun createServerSocket(port: Int, backlog: Int, ifAddress: java.net.InetAddress?): java.net.ServerSocket
    open fun createServerSocket(): java.net.ServerSocket = java.net.ServerSocket()
}

class SSLContext private constructor() {
    fun init(km: Array<KeyManager>?, tm: Array<TrustManager>?, random: java.security.SecureRandom?) {}
    val socketFactory: SSLSocketFactory get() = noTls("TLS")
    val serverSocketFactory: SSLServerSocketFactory get() = noTls("TLS")

    companion object {
        fun getInstance(protocol: String): SSLContext = noTls("TLS ($protocol)")
        fun getDefault(): SSLContext = noTls("TLS")
    }
}

class KeyManagerFactory private constructor() {
    fun init(ks: java.security.KeyStore?, password: CharArray?) {}
    val keyManagers: Array<KeyManager> get() = emptyArray()

    companion object {
        fun getDefaultAlgorithm(): String = "PKIX"
        fun getInstance(algorithm: String): KeyManagerFactory = noTls("Key managers ($algorithm)")
    }
}

class TrustManagerFactory private constructor() {
    fun init(ks: java.security.KeyStore?) {}
    val trustManagers: Array<TrustManager> get() = emptyArray()

    companion object {
        fun getDefaultAlgorithm(): String = "PKIX"
        fun getInstance(algorithm: String): TrustManagerFactory = noTls("Trust managers ($algorithm)")
    }
}

/** `HttpsURLConnection`: what java.net.URL opens for https. Its TLS can't be configured here. */
abstract class HttpsURLConnection protected constructor(url: java.net.URL) : java.net.HttpURLConnection(url) {
    companion object {
        fun setDefaultSSLSocketFactory(sf: SSLSocketFactory) {}
        fun getDefaultSSLSocketFactory(): SSLSocketFactory = noTls("TLS")
    }
}
