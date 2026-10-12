package android.security

/**
 * `android.security.NetworkSecurityPolicy`. A tool's manifest can forbid plain http on Android;
 * in the browser that's the page's to decide, and a page served over http (light-web's local
 * server) may use it, so cleartext is permitted.
 */
class NetworkSecurityPolicy private constructor() {
    fun isCleartextTrafficPermitted(): Boolean = true
    fun isCleartextTrafficPermitted(hostname: String): Boolean = true

    companion object {
        private val instance = NetworkSecurityPolicy()
        fun getInstance(): NetworkSecurityPolicy = instance
    }
}
