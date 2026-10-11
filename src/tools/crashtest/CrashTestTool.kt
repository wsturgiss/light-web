package lightweb.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import lightweb.LightTool

/**
 * A tool that fails on purpose, one way per row, for checking crash isolation. Listed only
 * when the page URL has `?crashtest`.
 */
val CrashTestTool = LightTool(
    id = "lightweb.crashtest",
    name = "Crash",
    assetPack = "sdk-ui",
    entryPoint = null,
    initialScreen = { CrashTestScreen(it) },
)

private class CrashTestScreen(activity: SealedLightActivity) : SimpleLightScreen<Unit>(activity) {
    @Composable
    override fun Content() {
        var crashInComposition by remember { mutableStateOf(false) }
        if (crashInComposition) error("Crash test: thrown while composing")
        Column(
            Modifier.fillMaxSize().background(LightThemeTokens.colors.background).padding(2f.gridUnitsAsDp()),
            verticalArrangement = Arrangement.spacedBy(2f.gridUnitsAsDp()),
        ) {
            LightText("Compose", LightTextVariant.Heading, modifier = Modifier.lightClickable { crashInComposition = true })
            LightText("Click", LightTextVariant.Heading, modifier = Modifier.lightClickable { error("Crash test: thrown in a click") })
            LightText("Async", LightTextVariant.Heading, modifier = Modifier.lightClickable {
                MainScope().launch { error("Crash test: thrown in a coroutine") }
            })
        }
    }
}
