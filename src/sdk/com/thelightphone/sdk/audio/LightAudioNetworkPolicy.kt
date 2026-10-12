package com.thelightphone.sdk.audio

/**
 * light-amp's SDK patch: the tool's say over whether its players may use the network. Asked
 * before each remote item loads (see WebAudioEngine); a refusal is a playback error whose cause
 * is [LightAudioNetworkBlockedException]. The browser streams on its own once an item has
 * started, so it isn't asked again mid-item as it is on the phone.
 */
object LightAudioNetworkPolicy {
    var allowsNetwork: () -> Boolean = { true }
}

class LightAudioNetworkBlockedException : java.io.IOException("The tool's network policy refused this connection")
