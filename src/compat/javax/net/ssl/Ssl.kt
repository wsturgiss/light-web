package javax.net.ssl

import java.io.IOException

open class SSLException(message: String? = null, cause: Throwable? = null) : IOException(message, cause)
open class SSLHandshakeException(message: String? = null, cause: Throwable? = null) : SSLException(message, cause)
open class SSLPeerUnverifiedException(message: String? = null) : SSLException(message)
