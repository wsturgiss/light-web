package android.graphics

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/** A decoded image. On the web it's already the Compose bitmap; see `asImageBitmap`. */
class Bitmap internal constructor(internal val image: ImageBitmap) {
    val width: Int get() = image.width
    val height: Int get() = image.height

    /** Accepted and ignored: Skia keeps its own pixel format. */
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
}

object BitmapFactory {
    /**
     * Android's decoding options, as far as tools use them. [inJustDecodeBounds] works (the size
     * is read by decoding, then the image is dropped); [inSampleSize] and [inPreferredConfig] are
     * accepted and ignored, so an image always decodes at full size.
     */
    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config? = Bitmap.Config.ARGB_8888
        var outWidth: Int = -1
        var outHeight: Int = -1
        var outMimeType: String? = null
    }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap {
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        return Bitmap(bytes.decodeToImageBitmap())
    }

    /** As Android's: null when [opts] asks only for the bounds, or when the data isn't an image. */
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        val bitmap = try {
            decodeByteArray(data, offset, length)
        } catch (e: Exception) {
            opts?.outWidth = -1
            opts?.outHeight = -1
            return null
        }
        if (opts != null) {
            opts.outWidth = bitmap.width
            opts.outHeight = bitmap.height
            if (opts.inJustDecodeBounds) return null
        }
        return bitmap
    }
}
