package com.thelightphone.sdk.cast

// light-amp's SDK patch: a UPnP-AV control point for casting to speakers on the network. Finding
// renderers takes UDP multicast (SSDP), which a page can't send, so none are ever found and every
// command reports failure. The types are the patch's, so tools compile unchanged.

data class DlnaRenderer(
    val id: String,
    val name: String,
    val controlUrl: String,
    val renderingControlUrl: String? = null,
    val connectionManagerUrl: String? = null,
)

enum class DlnaState { PLAYING, PAUSED, STOPPED, TRANSITIONING, UNKNOWN }

data class DlnaPosition(
    val positionMs: Long,
    val durationMs: Long,
    val trackUri: String? = null,
)

object DlnaCast {
    var allowsNetwork: () -> Boolean = { true }

    suspend fun discover(timeoutMs: Long = 3_000): List<DlnaRenderer> = emptyList()

    suspend fun play(
        renderer: DlnaRenderer,
        url: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mimeType: String = "audio/mpeg",
    ): Boolean = false

    suspend fun setNext(
        renderer: DlnaRenderer,
        url: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        mimeType: String = "audio/mpeg",
    ): Boolean = false

    suspend fun clearNext(renderer: DlnaRenderer): Boolean = false
    suspend fun resume(renderer: DlnaRenderer): Boolean = false
    suspend fun pause(renderer: DlnaRenderer): Boolean = false
    suspend fun stop(renderer: DlnaRenderer): Boolean = false
    suspend fun seek(renderer: DlnaRenderer, positionMs: Long): Boolean = false
    suspend fun sinkFormats(renderer: DlnaRenderer): Set<String> = emptySet()
    suspend fun probeMime(url: String): String? = null
    suspend fun supportsRanges(url: String): Boolean? = null
    suspend fun setVolume(renderer: DlnaRenderer, fraction: Float): Boolean = false
    suspend fun volume(renderer: DlnaRenderer): Float? = null
    suspend fun position(renderer: DlnaRenderer): DlnaPosition? = null
    suspend fun state(renderer: DlnaRenderer): DlnaState = DlnaState.UNKNOWN
}
