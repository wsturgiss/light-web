package android.media

/**
 * `android.media.MediaMetadataRetriever`. Reading tags and embedded art means parsing audio
 * containers, which the browser doesn't offer synchronously; [setDataSource] fails as Android's
 * does for a file it can't read, so callers fall back to having no tags.
 */
class MediaMetadataRetriever : AutoCloseable {
    fun setDataSource(path: String): Unit = throw IllegalArgumentException("Can't read media metadata in the browser: $path")
    fun extractMetadata(keyCode: Int): String? = null
    val embeddedPicture: ByteArray? get() = null
    fun release() {}
    override fun close() = release()

    companion object {
        const val METADATA_KEY_CD_TRACK_NUMBER = 0
        const val METADATA_KEY_ALBUM = 1
        const val METADATA_KEY_ARTIST = 2
        const val METADATA_KEY_AUTHOR = 3
        const val METADATA_KEY_COMPOSER = 4
        const val METADATA_KEY_DATE = 5
        const val METADATA_KEY_GENRE = 6
        const val METADATA_KEY_TITLE = 7
        const val METADATA_KEY_YEAR = 8
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_NUM_TRACKS = 10
        const val METADATA_KEY_WRITER = 11
        const val METADATA_KEY_MIMETYPE = 12
        const val METADATA_KEY_ALBUMARTIST = 13
        const val METADATA_KEY_DISC_NUMBER = 14
        const val METADATA_KEY_COMPILATION = 15
        const val METADATA_KEY_BITRATE = 20
    }
}
