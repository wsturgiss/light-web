package java.net

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// Sockets and network interfaces, so code that uses them compiles. A page can't open a socket,
// listen on a port or send UDP, so each fails the way a network without that service would: an
// IOException at the point of use. light-amp uses them for its loopback stream proxy, Plex's GDM
// discovery and companion listener, and DLNA discovery; it falls back when they fail.

private fun noSockets(what: String): Nothing = throw SocketException("$what isn't available in the browser")

open class InetAddress internal constructor(private val host: String?, private val address: ByteArray) {
    open val hostAddress: String get() = address.joinToString(".") { (it.toInt() and 0xff).toString() }
    open val hostName: String get() = host ?: hostAddress
    open fun getAddress(): ByteArray = address.copyOf()
    open val isLoopbackAddress: Boolean get() = address.size == 4 && address[0].toInt() == 127
    open val isSiteLocalAddress: Boolean get() = false
    open val isLinkLocalAddress: Boolean get() = false
    open val isAnyLocalAddress: Boolean get() = address.all { it.toInt() == 0 }
    open val isMulticastAddress: Boolean get() = address.size == 4 && (address[0].toInt() and 0xf0) == 0xe0

    override fun toString() = "${host.orEmpty()}/$hostAddress"
    override fun equals(other: Any?) = other is InetAddress && other.address.contentEquals(address)
    override fun hashCode() = address.contentHashCode()

    companion object {
        /** A literal IPv4 address only: a page can't resolve names. */
        fun getByName(host: String?): InetAddress {
            val name = host ?: "localhost"
            if (name == "localhost") return Inet4Address(name, byteArrayOf(127, 0, 0, 1))
            val parts = name.split('.')
            if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
                return Inet4Address(null, ByteArray(4) { parts[it].toInt().toByte() })
            }
            throw UnknownHostException("$name (names can't be resolved in the browser)")
        }

        fun getAllByName(host: String?): Array<InetAddress> = arrayOf(getByName(host))

        fun getByAddress(address: ByteArray): InetAddress = Inet4Address(null, address.copyOf())

        fun getByAddress(host: String?, address: ByteArray): InetAddress = Inet4Address(host, address.copyOf())

        fun getLoopbackAddress(): InetAddress = getByName("localhost")

        fun getLocalHost(): InetAddress = getLoopbackAddress()
    }
}

class Inet4Address internal constructor(host: String?, address: ByteArray) : InetAddress(host, address)

open class SocketAddress

class InetSocketAddress(val address: InetAddress?, val port: Int) : SocketAddress() {
    constructor(port: Int) : this(null, port)
    constructor(hostname: String, port: Int) : this(addressOrNull(hostname), port)
    val hostString: String get() = address?.hostAddress.orEmpty()
}

private fun addressOrNull(hostname: String): InetAddress? = try { InetAddress.getByName(hostname) } catch (e: Exception) { null }

class NetworkInterface private constructor() {
    val name: String = ""
    val isUp: Boolean = false
    val isLoopback: Boolean = false
    fun getInetAddresses(): java.util.Enumeration<InetAddress> = java.util.Collections.emptyEnumeration()
    val inetAddresses: java.util.Enumeration<InetAddress> get() = getInetAddresses()

    companion object {
        /** None: a page can't see the machine's network interfaces. */
        fun getNetworkInterfaces(): java.util.Enumeration<NetworkInterface> = java.util.Collections.emptyEnumeration()
    }
}

open class Socket() : Closeable {
    constructor(host: String, port: Int) : this() { noSockets("Socket") }
    constructor(host: InetAddress, port: Int) : this() { noSockets("Socket") }
    constructor(host: String, port: Int, localAddr: InetAddress?, localPort: Int) : this() { noSockets("Socket") }
    constructor(address: InetAddress, port: Int, localAddr: InetAddress?, localPort: Int) : this() { noSockets("Socket") }

    open var soTimeout: Int = 0
    open var tcpNoDelay: Boolean = false
    open var keepAlive: Boolean = false
    open val isClosed: Boolean get() = true
    open val isConnected: Boolean get() = false
    open val inetAddress: InetAddress? get() = null
    open val port: Int get() = 0

    open fun connect(endpoint: SocketAddress): Unit = noSockets("Socket")
    open fun connect(endpoint: SocketAddress, timeout: Int): Unit = noSockets("Socket")
    open fun getInputStream(): InputStream = noSockets("Socket")
    open fun getOutputStream(): OutputStream = noSockets("Socket")
    val inputStream: InputStream get() = getInputStream()
    val outputStream: OutputStream get() = getOutputStream()
    open fun shutdownOutput() {}
    open fun shutdownInput() {}
    override fun close() {}
}

open class ServerSocket(port: Int = 0, backlog: Int = 50, bindAddr: InetAddress? = null) : Closeable {
    init { noSockets("Listening on a port") }

    open val localPort: Int get() = -1
    open var soTimeout: Int = 0
    open var reuseAddress: Boolean = false
    open val isClosed: Boolean get() = true
    open fun accept(): Socket = noSockets("Listening on a port")
    override fun close() {}
}

class DatagramPacket(val data: ByteArray, val offset: Int, var length: Int, private var to: InetAddress?, var port: Int) {
    /** Java's is a platform type, used as non-null once a packet has arrived. */
    var address: InetAddress
        get() = checkNotNull(to) { "No address" }
        set(value) { to = value }
    constructor(buf: ByteArray, length: Int) : this(buf, 0, length, null, 0)
    constructor(buf: ByteArray, length: Int, address: InetAddress, port: Int) : this(buf, 0, length, address, port)
    fun getData(): ByteArray = data
    val socketAddress: SocketAddress get() = InetSocketAddress(to, port)
}

open class DatagramSocket() : Closeable {
    constructor(port: Int) : this()
    init { noSockets("UDP") }

    open var broadcast: Boolean = false
    open var soTimeout: Int = 0
    open var reuseAddress: Boolean = false
    open fun send(packet: DatagramPacket): Unit = noSockets("UDP")
    open fun receive(packet: DatagramPacket): Unit = noSockets("UDP")
    open fun bind(address: SocketAddress?): Unit = noSockets("UDP")
    override fun close() {}
}

class MulticastSocket(port: Int = 0) : DatagramSocket() {
    fun joinGroup(address: InetAddress): Unit = noSockets("UDP")
    fun leaveGroup(address: InetAddress) {}
    var timeToLive: Int = 1
}
