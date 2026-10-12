package androidx.media3.common

// The parts of media3's API that appear in the Light SDK's audio player, which tools see: repeat
// modes and the error a player reports. There's no media3 in the browser; the SDK's player is an
// HTML audio element (see com.thelightphone.sdk.audio.LightAudioPlayer).

interface Player {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
    }
}

/** A playback failure, with media3's error codes (the ones the web player can tell apart). */
open class PlaybackException(message: String?, cause: Throwable?, val errorCode: Int) : Exception(message, cause) {
    val errorCodeName: String get() = getErrorCodeName(errorCode)

    companion object {
        const val ERROR_CODE_UNSPECIFIED = 1000
        const val ERROR_CODE_REMOTE_ERROR = 1001
        const val ERROR_CODE_TIMEOUT = 1003
        const val ERROR_CODE_IO_UNSPECIFIED = 2000
        const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
        const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
        const val ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003
        const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
        const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005
        const val ERROR_CODE_IO_NO_PERMISSION = 2006
        const val ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007
        const val ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008
        const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
        const val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003
        const val ERROR_CODE_DECODING_FAILED = 4001
        const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005

        fun getErrorCodeName(errorCode: Int): String = when (errorCode) {
            ERROR_CODE_UNSPECIFIED -> "ERROR_CODE_UNSPECIFIED"
            ERROR_CODE_REMOTE_ERROR -> "ERROR_CODE_REMOTE_ERROR"
            ERROR_CODE_TIMEOUT -> "ERROR_CODE_TIMEOUT"
            ERROR_CODE_IO_UNSPECIFIED -> "ERROR_CODE_IO_UNSPECIFIED"
            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"
            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE"
            ERROR_CODE_IO_BAD_HTTP_STATUS -> "ERROR_CODE_IO_BAD_HTTP_STATUS"
            ERROR_CODE_IO_FILE_NOT_FOUND -> "ERROR_CODE_IO_FILE_NOT_FOUND"
            ERROR_CODE_IO_NO_PERMISSION -> "ERROR_CODE_IO_NO_PERMISSION"
            ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED"
            ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE"
            ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "ERROR_CODE_PARSING_CONTAINER_MALFORMED"
            ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
            ERROR_CODE_DECODING_FAILED -> "ERROR_CODE_DECODING_FAILED"
            ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
            else -> "invalid error code"
        }
    }
}
