package android.graphics

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/** A decoded image. On the web it's already the Compose bitmap; see `asImageBitmap`. */
class Bitmap internal constructor(internal val image: ImageBitmap) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}

object BitmapFactory {
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap {
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        return Bitmap(bytes.decodeToImageBitmap())
    }
}
