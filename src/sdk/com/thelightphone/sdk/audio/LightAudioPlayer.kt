package com.thelightphone.sdk.audio

import androidx.media3.common.PlaybackException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Whether a [LightAudioPlayer] is initializing, accepts commands, or is terminal. */
enum class LightAudioPlayerAvailability { Initializing, Ready, Released }

/**
 * The SDK's player, with light-amp's additive patches (incremental queue edits, a start position,
 * repeat, gain, device volume, error and repeat callbacks), on a [WebAudioEngine]. It's ready at
 * once: there's no service to connect to.
 *
 * Releasing an attached player stops its audio. Releasing a detached one leaves it playing, as the
 * SDK's audio service does; the next detached handle sees and controls the same playback.
 */
class LightAudioPlayer internal constructor(
    private val engine: WebAudioEngine,
    internal val playback: LightAudioPlayback,
) {
    private val _availability = MutableStateFlow(LightAudioPlayerAvailability.Ready)
    private var released = false

    init {
        engine.listener = this
    }

    val positionMs: StateFlow<Long> = engine.positionMs.asStateFlow()
    val durationMs: StateFlow<Long> = engine.durationMs.asStateFlow()
    val isPlaying: StateFlow<Boolean> = engine.isPlaying.asStateFlow()
    val currentMediaItemIndex: StateFlow<Int> = engine.currentIndex.asStateFlow()
    val error: StateFlow<LightAudioError?> = engine.error.asStateFlow()
    val availability: StateFlow<LightAudioPlayerAvailability> = _availability.asStateFlow()

    /** The page's playback level, 0..1. The browser can't set the computer's own volume. */
    val deviceVolume: StateFlow<Float> = engine.deviceVolume.asStateFlow()

    /** Called when playback fails, with media3's error codes. */
    var onPlaybackError: ((PlaybackException) -> Unit)? = null

    /** Called when repeat-one wraps the current item. */
    var onItemRepeated: (() -> Unit)? = null

    /** Per-track gain, 0..1, multiplied into the volume. */
    var playbackGain: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            ifLive { engine.gain = field }
        }

    var speed: Float = 1.0f
        set(value) {
            field = value.coerceAtLeast(MIN_SPEED)
            ifLive { engine.speed = field }
        }

    fun setSystemVolume(fraction: Float) = ifLive { engine.setDeviceVolume(fraction) }

    fun setSource(file: File) {
        setMediaQueue(listOf(LightAudioItem(LightAudioSource.FileSource(file), LightMediaMetadata(file.nameWithoutExtension))))
    }

    fun setMediaQueue(items: List<LightAudioItem>, startIndex: Int = 0) {
        if (items.isNotEmpty()) require(startIndex in items.indices) { "Start index must reference a queue item" }
        ifLive { engine.setQueue(items, startIndex, 0) }
    }

    fun setMediaQueueAt(items: List<LightAudioItem>, startIndex: Int, startPositionMs: Long) {
        if (items.isNotEmpty()) require(startIndex in items.indices) { "Start index must reference a queue item" }
        ifLive { engine.setQueue(items, startIndex, startPositionMs) }
    }

    fun play() = ifLive { engine.play() }
    fun pause() = ifLive { engine.pause() }
    fun stop() = ifLive { engine.stop() }

    val playWhenReady: Boolean get() = !released && engine.playWhenReady
    val isCurrentItemSeekable: Boolean get() = !released && engine.isSeekable

    /** Seeks to [ms], clamped to the known duration (0 while it's unknown, as the SDK's). */
    fun seekTo(ms: Long) = ifLive { engine.seekTo(ms.coerceIn(0L, durationMs.value.coerceAtLeast(0L))) }

    fun skipBack() = seekTo((positionMs.value - SKIP_INTERVAL_MS).coerceAtLeast(0))
    fun skipForward() = seekTo(positionMs.value + SKIP_INTERVAL_MS)
    fun skipToNext() = ifLive { engine.skipToNext() }
    fun skipToPrevious() = ifLive { engine.skipToPrevious() }

    fun addItems(items: List<LightAudioItem>) { if (items.isNotEmpty()) ifLive { engine.addItems(items) } }
    fun addItemAt(index: Int, item: LightAudioItem) = ifLive { engine.addItemAt(index, item) }
    fun replaceRange(fromIndex: Int, toIndex: Int, items: List<LightAudioItem>) = ifLive { engine.replaceRange(fromIndex, toIndex, items) }
    fun removeItem(index: Int) = ifLive { engine.removeItem(index) }
    fun moveItem(from: Int, to: Int) = ifLive { engine.moveItem(from, to) }
    fun seekToIndex(index: Int) = ifLive { engine.seekToIndex(index) }

    /** media3's constants: Player.REPEAT_MODE_OFF, _ONE or _ALL. */
    var repeatMode: Int
        get() = engine.repeatMode
        set(value) = ifLive { engine.repeatMode = value }

    /** Waits for connection, returning false if this player is released first. */
    suspend fun awaitReady(): Boolean =
        availability.first { it != LightAudioPlayerAvailability.Initializing } == LightAudioPlayerAvailability.Ready

    fun release() {
        if (released) return
        released = true
        _availability.value = LightAudioPlayerAvailability.Released
        if (engine.listener === this) engine.listener = null
        if (playback == LightAudioPlayback.Attached) engine.release()
    }

    private inline fun ifLive(block: () -> Unit) {
        if (!released) block()
    }

    private companion object {
        const val SKIP_INTERVAL_MS = 15_000L
        const val MIN_SPEED = 0.1f
    }
}
