package com.thelightphone.sdk.audio

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lightweb.compat.WebHttp
import lightweb.compat.WebFiles
import lightweb.compat.toUint8Array
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event

/**
 * What the SDK's player plays on: one HTML audio element and the queue around it, as media3's
 * player is on the phone. A [LightAudioPlayer] is a handle on one of these; a detached one is
 * shared by every detached handle and outlives them all (see [detached]).
 *
 * Items load one at a time: the element plays the current item, and moving on sets its source
 * again. A remote item is fetched by the browser straight from its server, which needs no
 * permission from the server to play (unlike reading it with fetch), so it doesn't go through
 * light-web's proxy.
 */
internal class WebAudioEngine(private val assetPack: String) {
    private val audio = document.createElement("audio") as HTMLAudioElement
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val items = mutableListOf<LightAudioItem>()
    private var loadedUrl: String? = null
    /** Whether [loadedUrl] is playing through light-web's proxy, after the browser couldn't reach it. */
    private var viaProxy = false
    private var objectUrl: String? = null
    /** Bumped on every load, so an answer about an earlier item is ignored. */
    private var generation = 0

    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val isPlaying = MutableStateFlow(false)
    val currentIndex = MutableStateFlow(NO_MEDIA_ITEM)
    val error = MutableStateFlow<LightAudioError?>(null)
    val deviceVolume = MutableStateFlow(1f)

    /** The caller's intent: play once the current item can. */
    var playWhenReady = false
        private set
    var repeatMode = Player.REPEAT_MODE_OFF
    var gain = 1f
        set(value) { field = value; applyVolume() }
    var speed = 1f
        set(value) { field = value; audio.playbackRate = value.toDouble() }

    /** The handle that hears this engine's callbacks: the one most recently attached. */
    var listener: LightAudioPlayer? = null

    val itemCount: Int get() = items.size
    val isSeekable: Boolean get() = hasSeekableRange(audio) && audio.duration.isFinite()
    private val validIndex: Boolean get() = currentIndex.value in items.indices

    init {
        audio.preload = "auto"
        on("playing") { isPlaying.value = true }
        on("pause") { isPlaying.value = false; updatePosition() }
        on("waiting") { isPlaying.value = false }
        on("timeupdate") { updatePosition() }
        on("durationchange") { updateDuration() }
        on("loadedmetadata") { updateDuration() }
        on("ended") { onEnded() }
        on("error") { onMediaError() }
    }

    // --- Queue ------------------------------------------------------------------------------

    fun setQueue(newItems: List<LightAudioItem>, startIndex: Int, startPositionMs: Long) {
        items.clear()
        items.addAll(newItems)
        if (items.isEmpty()) {
            unload()
            currentIndex.value = NO_MEDIA_ITEM
            return
        }
        load(startIndex, startPositionMs)
    }

    fun addItems(newItems: List<LightAudioItem>) {
        items.addAll(newItems)
        if (!validIndex && items.isNotEmpty()) load(0, 0)
    }

    fun addItemAt(index: Int, item: LightAudioItem) {
        val at = index.coerceIn(0, items.size)
        items.add(at, item)
        if (!validIndex) load(0, 0) else if (at <= currentIndex.value) currentIndex.value++
    }

    fun replaceRange(fromIndex: Int, toIndex: Int, newItems: List<LightAudioItem>) {
        val from = fromIndex.coerceIn(0, items.size)
        val to = toIndex.coerceIn(from, items.size)
        val current = currentIndex.value
        repeat(to - from) { items.removeAt(from) }
        items.addAll(from, newItems)
        when {
            current < from -> {}
            current >= to -> currentIndex.value = current - (to - from) + newItems.size
            // The playing item was in the replaced range: as media3, move to what took its place.
            else -> if (items.isEmpty()) { unload(); currentIndex.value = NO_MEDIA_ITEM } else load(minOf(current, items.lastIndex), 0)
        }
    }

    fun removeItem(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        val current = currentIndex.value
        when {
            items.isEmpty() -> { unload(); currentIndex.value = NO_MEDIA_ITEM }
            index < current -> currentIndex.value = current - 1
            index == current -> load(minOf(index, items.lastIndex), 0)
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
        val current = currentIndex.value
        currentIndex.value = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
    }

    // --- Transport --------------------------------------------------------------------------

    fun play() {
        playWhenReady = true
        if (validIndex) startPlaying()
    }

    fun pause() {
        playWhenReady = false
        audio.pause()
    }

    fun stop() {
        playWhenReady = false
        audio.pause()
        seekTo(0)
    }

    fun seekTo(ms: Long) {
        if (!validIndex) return
        audio.currentTime = ms.coerceAtLeast(0) / 1000.0
        updatePosition()
    }

    fun seekToIndex(index: Int) {
        if (index in items.indices) load(index, 0)
    }

    fun skipToNext() {
        val next = currentIndex.value + 1
        when {
            next in items.indices -> load(next, 0)
            repeatMode == Player.REPEAT_MODE_ALL && items.isNotEmpty() -> load(0, 0)
        }
    }

    /** As media3: the start of this item, unless near it already, then the previous one. */
    fun skipToPrevious() {
        val previous = currentIndex.value - 1
        when {
            positionMs.value > MAX_POSITION_FOR_PREVIOUS_MS || previous < 0 && repeatMode != Player.REPEAT_MODE_ALL -> seekTo(0)
            previous >= 0 -> load(previous, 0)
            else -> load(items.lastIndex, 0)
        }
    }

    fun setDeviceVolume(fraction: Float) {
        deviceVolume.value = fraction.coerceIn(0f, 1f)
        applyVolume()
    }

    fun release() {
        playWhenReady = false
        audio.pause()
        unload()
        items.clear()
        currentIndex.value = NO_MEDIA_ITEM
        listener = null
    }

    // --- Inside -----------------------------------------------------------------------------

    private fun load(index: Int, startPositionMs: Long) {
        val item = items[index]
        val gen = ++generation
        currentIndex.value = index
        error.value = null
        positionMs.value = startPositionMs.coerceAtLeast(0)
        durationMs.value = item.metadata.durationMs?.takeIf { it > 0 } ?: 0L
        isPlaying.value = false
        val source = item.source
        if (source is LightAudioSource.UrlSource && !LightAudioNetworkPolicy.allowsNetwork()) {
            unload()
            fail(gen, PlaybackException("Blocked by the network policy", LightAudioNetworkBlockedException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            return
        }
        val url = try {
            urlFor(source)
        } catch (e: Exception) {
            unload()
            fail(gen, PlaybackException(e.message, e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
            return
        }
        loadedUrl = url
        viaProxy = false
        audio.src = url
        if (startPositionMs > 0) {
            once("loadedmetadata") { if (gen == generation) audio.currentTime = startPositionMs / 1000.0 }
        }
        audio.load()
        if (playWhenReady) startPlaying()
    }

    private fun urlFor(source: LightAudioSource): String {
        objectUrl?.let { revokeObjectUrl(it) }
        objectUrl = null
        return when (source) {
            is LightAudioSource.UrlSource -> source.url
            is LightAudioSource.AssetSource -> "assets/$assetPack/assets/${source.assetPath.trimStart('/')}"
            is LightAudioSource.FileSource -> {
                val bytes = WebFiles.read(source.file.path) ?: throw java.io.FileNotFoundException(source.file.path)
                objectUrlFor(bytes.toUint8Array()).also { objectUrl = it }
            }
        }
    }

    private fun unload() {
        generation++
        loadedUrl = null
        audio.removeAttribute("src")
        audio.load()
        objectUrl?.let { revokeObjectUrl(it) }
        objectUrl = null
        isPlaying.value = false
        positionMs.value = 0
        durationMs.value = 0
    }

    private fun startPlaying() = playElement(audio)

    private fun onEnded() {
        isPlaying.value = false
        when {
            repeatMode == Player.REPEAT_MODE_ONE -> {
                audio.currentTime = 0.0
                startPlaying()
                listener?.onItemRepeated?.invoke()
            }
            currentIndex.value + 1 in items.indices -> load(currentIndex.value + 1, 0)
            repeatMode == Player.REPEAT_MODE_ALL && items.isNotEmpty() -> load(0, 0)
            else -> { playWhenReady = false; updatePosition() }
        }
    }

    /**
     * The element's errors are coarse: "not supported" covers both a server that answered with
     * an error and audio that can't be decoded. media3 tells them apart, and tools act on the
     * difference (a bad status means the server is up), so a source the element refused is asked
     * for its first byte to find out which.
     */
    private fun onMediaError() {
        val gen = generation
        val url = loadedUrl ?: return
        val code = mediaErrorCode(audio)
        isPlaying.value = false
        when (code) {
            MEDIA_ERR_ABORTED -> return
            MEDIA_ERR_NETWORK -> fail(gen, PlaybackException("Network error", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
            MEDIA_ERR_DECODE -> fail(gen, PlaybackException("Couldn't decode", null, PlaybackException.ERROR_CODE_DECODING_FAILED))
            else -> scope.launch {
                val status = if (url.startsWith("http")) WebHttp.statusOf(url) else null
                val e = when {
                    status == null && url.startsWith("http") ->
                        PlaybackException("Couldn't reach the server", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
                    status != null && status >= 400 ->
                        PlaybackException("Response code: $status", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                    status != null && !viaProxy && WebHttp.proxied -> {
                        // The proxy reached it where the browser couldn't (a home server's public
                        // address seen from inside the home, say): play it through the proxy.
                        if (gen == generation) playViaProxy(url)
                        return@launch
                    }
                    else -> PlaybackException("The browser can't play this format", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                }
                fail(gen, e)
            }
        }
    }

    private fun playViaProxy(url: String) {
        val position = audio.currentTime
        viaProxy = true
        audio.src = "/proxy?url=" + encodeUriComponent(url)
        if (position > 0) once("loadedmetadata") { audio.currentTime = position }
        audio.load()
        if (playWhenReady) startPlaying()
    }

    private fun fail(gen: Int, e: PlaybackException) {
        if (gen != generation) return
        error.value = LightAudioError(kindFor(e.errorCode), e.errorCodeName, currentIndex.value)
        listener?.onPlaybackError?.invoke(e)
    }

    private fun updatePosition() {
        if (validIndex) positionMs.value = (audio.currentTime * 1000).toLong().coerceAtLeast(0)
    }

    private fun updateDuration() {
        val d = audio.duration
        if (d.isFinite() && d > 0) durationMs.value = (d * 1000).toLong()
    }

    private fun applyVolume() {
        audio.volume = (gain * deviceVolume.value).coerceIn(0f, 1f).toDouble()
    }

    private fun on(type: String, handler: () -> Unit) = audio.addEventListener(type, { _: Event -> handler() })

    private fun once(type: String, handler: () -> Unit) = addOnce(audio, type) { handler() }

    companion object {
        private var shared: WebAudioEngine? = null

        /** The page's one detached engine, made on first use. */
        fun detached(assetPack: String): WebAudioEngine = shared ?: WebAudioEngine(assetPack).also { shared = it }

        private const val MAX_POSITION_FOR_PREVIOUS_MS = 3_000L
        private const val MEDIA_ERR_ABORTED = 1
        private const val MEDIA_ERR_NETWORK = 2
        private const val MEDIA_ERR_DECODE = 3

        private fun kindFor(errorCode: Int): LightAudioErrorKind = when (errorCode) {
            in 2000..2999 -> LightAudioErrorKind.Source
            in 3000..4999 -> LightAudioErrorKind.Unsupported
            in 5000..5999 -> LightAudioErrorKind.Output
            else -> LightAudioErrorKind.Unknown
        }
    }
}

private fun playElement(audio: HTMLAudioElement): Unit = js(
    "{ audio.play().catch(e => { if (e.name !== 'AbortError') console.warn('light-web audio: play refused:', e.name, e.message); }); }"
)

private fun hasSeekableRange(audio: HTMLAudioElement): Boolean = js("audio.seekable.length > 0")

private fun mediaErrorCode(audio: HTMLAudioElement): Int = js("audio.error ? audio.error.code : 0")

private fun addOnce(audio: HTMLAudioElement, type: String, handler: () -> Unit): Unit =
    js("audio.addEventListener(type, () => handler(), { once: true })")

private fun objectUrlFor(bytes: org.khronos.webgl.Uint8Array): String = js("URL.createObjectURL(new Blob([bytes]))")

private fun revokeObjectUrl(url: String): Unit = js("URL.revokeObjectURL(url)")

private fun encodeUriComponent(s: String): String = js("encodeURIComponent(s)")
