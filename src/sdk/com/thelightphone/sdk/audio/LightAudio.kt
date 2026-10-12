package com.thelightphone.sdk.audio

import com.thelightphone.sdk.SealedLightActivity

// The SDK's audio API (v0.2.0 plus light-amp's additive patches), for playback. Recording,
// capture and voice aren't provided yet: no tool light-web runs uses them.

enum class LightAudioUsage { Music, Speech, Alarm, VoiceCall }

/** Whether playback is tied to the tool screen or survives leaving it. */
enum class LightAudioPlayback { Attached, Detached }

enum class LightAudioErrorKind { Source, Unsupported, Output, Unknown }

data class LightAudioError(
    val kind: LightAudioErrorKind,
    val diagnostic: String,
    val itemIndex: Int,
)

data class AudioCapabilities(val sampleRate: Int) {
    companion object {
        val Default = AudioCapabilities(sampleRate = 48_000)
    }
}

data class LightMediaMetadata(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

/** Location of audio that can be added to a [LightAudioPlayer] queue. */
sealed interface LightAudioSource {
    data class FileSource(val file: java.io.File) : LightAudioSource
    data class AssetSource(val assetPath: String) : LightAudioSource
    data class UrlSource(val url: String) : LightAudioSource
}

data class LightAudioItem(
    val source: LightAudioSource,
    val metadata: LightMediaMetadata,
)

class LightAudioPlayerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Queue index reported when a player has no media item. */
const val NO_MEDIA_ITEM = -1

interface LightAudio {
    val capabilities: AudioCapabilities
    fun newPlayer(
        usage: LightAudioUsage = LightAudioUsage.Music,
        playback: LightAudioPlayback = LightAudioPlayback.Attached,
    ): LightAudioPlayer
}

/**
 * Factory for audio components. A [LightAudioPlayback.Detached] player plays on the page's one
 * shared audio element, which carries on after the handle is released and after the tool closes,
 * as the SDK's audio service does on the phone; the next detached handle picks up where it is.
 */
class DefaultLightAudio(private val sealedActivity: SealedLightActivity) : LightAudio {
    override val capabilities: AudioCapabilities get() = AudioCapabilities.Default

    override fun newPlayer(usage: LightAudioUsage, playback: LightAudioPlayback): LightAudioPlayer {
        val assetPack = sealedActivity.activity.tool.assetPack
        return when (playback) {
            LightAudioPlayback.Attached -> LightAudioPlayer(WebAudioEngine(assetPack), playback)
            LightAudioPlayback.Detached -> LightAudioPlayer(WebAudioEngine.detached(assetPack), playback)
        }
    }
}
