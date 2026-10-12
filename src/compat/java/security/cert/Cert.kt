package java.security.cert

import java.security.GeneralSecurityException

open class CertificateException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
open class CertPathValidatorException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
open class CertPathBuilderException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)

/** A certificate. None can be made or parsed in the browser (see [CertificateFactory]). */
abstract class Certificate(val type: String) {
    abstract val encoded: ByteArray
    abstract fun verify(key: java.security.PublicKey)
    abstract val publicKey: java.security.PublicKey
}

abstract class X509Certificate : Certificate("X.509")

class CertificateFactory private constructor() {
    fun generateCertificate(inStream: java.io.InputStream): Certificate =
        throw CertificateException("Certificates can't be parsed in the browser")

    companion object {
        fun getInstance(type: String): CertificateFactory = CertificateFactory()
    }
}
