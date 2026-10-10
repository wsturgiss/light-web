package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import lightweb.Assets
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector

/**
 * Android's `painterResource(R.drawable.x)`: an Android vector drawable or a bitmap, read from
 * its (already loaded) pack.
 */
@Composable
fun painterResource(id: Int): Painter {
    val path = Assets.pathOf(id)
    return if (path.endsWith(".xml")) {
        val density = LocalDensity.current
        val vector = remember(id, density) { Assets.read(path).decodeToImageVector(density) }
        rememberVectorPainter(vector)
    } else {
        remember(id) { BitmapPainter(Assets.read(path).decodeToImageBitmap()) }
    }
}
