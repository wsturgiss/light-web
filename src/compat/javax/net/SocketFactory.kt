package javax.net

import java.net.InetAddress
import java.net.Socket

/** `javax.net.SocketFactory`. Sockets aren't available in the browser (see java.net.Socket). */
abstract class SocketFactory protected constructor() {
    open fun createSocket(): Socket = Socket()
    abstract fun createSocket(host: String, port: Int): Socket
    abstract fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket
    abstract fun createSocket(host: InetAddress, port: Int): Socket
    abstract fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket

    companion object {
        fun getDefault(): SocketFactory = Default
    }

    private object Default : SocketFactory() {
        override fun createSocket(host: String, port: Int) = Socket(host, port)
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) = Socket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress, port: Int) = Socket(host, port)
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) = Socket(address, port, localAddress, localPort)
    }
}
