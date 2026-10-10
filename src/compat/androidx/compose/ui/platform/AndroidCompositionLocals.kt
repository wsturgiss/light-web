package androidx.compose.ui.platform

import android.content.Context
import android.content.WebContext
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** Android's `LocalContext`. SDK UI only hands it on; see [Context]. */
val LocalContext = staticCompositionLocalOf<Context> { WebContext }

/**
 * Android's `LocalConfiguration`. The SDK sizes everything off the screen in dp (LightGrid), so
 * the shell provides the LP3's: 1080 × 1240 px at density 3.
 */
val LocalConfiguration = staticCompositionLocalOf { Configuration(screenWidthDp = 360, screenHeightDp = 413) }
