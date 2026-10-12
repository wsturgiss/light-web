package java.security

import java.security.spec.AlgorithmParameterSpec

// Keys, signatures and key stores, so code that makes its own compiles. The browser's crypto is
// asynchronous and Java's isn't, so none of it is provided: each getInstance fails with
// NoSuchAlgorithmException, which callers already handle for an algorithm the platform lacks.
// light-amp uses them only for its loopback stream proxy's certificate, and falls back.
// SecureRandom is real (crypto.getRandomValues).

open class KeyStoreException(message: String? = null) : GeneralSecurityException(message)
open class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)
open class SignatureException(message: String? = null) : GeneralSecurityException(message)

private fun none(what: String): Nothing = throw NoSuchAlgorithmException("$what isn't available in the browser")

interface Key {
    val algorithm: String
    val format: String?
    val encoded: ByteArray
}

interface PublicKey : Key
interface PrivateKey : Key

class KeyPair(val public: PublicKey, val private: PrivateKey)

class KeyPairGenerator private constructor() {
    fun initialize(params: AlgorithmParameterSpec, random: SecureRandom?) {}
    fun initialize(keySize: Int) {}
    fun generateKeyPair(): KeyPair = none("Key generation")

    companion object {
        fun getInstance(algorithm: String): KeyPairGenerator = none("Key generation ($algorithm)")
    }
}

class Signature private constructor() {
    fun initSign(key: PrivateKey) {}
    fun initVerify(key: PublicKey) {}
    fun update(data: ByteArray) {}
    fun sign(): ByteArray = none("Signing")
    fun verify(signature: ByteArray): Boolean = none("Signing")

    companion object {
        fun getInstance(algorithm: String): Signature = none("Signing ($algorithm)")
    }
}

class KeyStore private constructor() {
    fun load(stream: java.io.InputStream?, password: CharArray?) {}
    fun setKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out java.security.cert.Certificate>?) {}
    fun setCertificateEntry(alias: String, cert: java.security.cert.Certificate) {}

    companion object {
        fun getDefaultType(): String = "PKCS12"
        fun getInstance(type: String): KeyStore = throw KeyStoreException("Key stores aren't available in the browser ($type)")
    }
}

/** Cryptographically strong random bytes, from the browser's `crypto.getRandomValues`. */
open class SecureRandom : java.util.Random() {
    override fun nextBytes(bytes: ByteArray) {
        val chunk = 65536 // getRandomValues' limit per call
        var i = 0
        while (i < bytes.size) {
            val n = minOf(chunk, bytes.size - i)
            val js = randomValues(n)
            for (k in 0 until n) bytes[i + k] = byteAt(js, k)
            i += n
        }
    }

    override fun nextInt(): Int { val b = ByteArray(4); nextBytes(b); return (b[0].toInt() shl 24) or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff) }
    override fun nextLong(): Long = (nextInt().toLong() shl 32) or (nextInt().toLong() and 0xffffffffL)
    override fun nextInt(bound: Int): Int { require(bound > 0); return ((nextLong() ushr 1) % bound).toInt() }
    override fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    override fun nextBoolean(): Boolean = nextInt() < 0

    companion object {
        fun getInstance(algorithm: String): SecureRandom = SecureRandom()
        fun getInstanceStrong(): SecureRandom = SecureRandom()
    }
}

private fun randomValues(n: Int): JsAny = js("crypto.getRandomValues(new Int8Array(n))")
private fun byteAt(array: JsAny, i: Int): Byte = js("array[i]")
