package android.os

/**
 * `android.os.StatFs`. The browser reports storage only asynchronously
 * (`navigator.storage.estimate()`), so this reports none free: a tool sizing a download by it
 * declines, as it would on a full phone.
 */
class StatFs(path: String) {
    val availableBytes: Long get() = 0L
    val freeBytes: Long get() = 0L
    val totalBytes: Long get() = 0L
    val blockSizeLong: Long get() = 4096L
    val availableBlocksLong: Long get() = 0L
}
