package com.thelightphone.sdk

import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import org.w3c.dom.events.Event

data class NetworkStatus(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val isMetered: Boolean
)

/**
 * The SDK's LightConnectivity, from the browser's online/offline state. A browser doesn't say
 * how it's connected, so a computer reads as on Wi-Fi and unmetered.
 */
class LightConnectivity internal constructor() {
    val currentStatus: NetworkStatus
        get() = NetworkStatus(isConnected = window.navigator.onLine, isWifi = window.navigator.onLine, isMetered = false)

    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val listener: (Event) -> Unit = { trySend(currentStatus) }
        trySend(currentStatus)
        window.addEventListener("online", listener)
        window.addEventListener("offline", listener)
        awaitClose {
            window.removeEventListener("online", listener)
            window.removeEventListener("offline", listener)
        }
    }.conflate().distinctUntilChanged()
}
