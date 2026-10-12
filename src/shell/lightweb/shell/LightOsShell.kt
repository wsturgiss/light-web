package lightweb.shell

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.LightToolHost
import lightweb.DisplayColor
import lightweb.warmToolDataStore
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lightweb.Assets
import lightweb.DeviceMode
import lightweb.compat.WebFiles
import lightweb.LightTool
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/** The LP3's screen: 1080 × 1240 px at density 3. */
private const val SCREEN_W_DP = 360
private const val SCREEN_H_DP = 413

private sealed interface ShellState {
    data object Starting : ShellState
    data object Home : ShellState
    data class Loading(val tool: LightTool, val done: Int, val total: Int) : ShellState
    data class Running(val host: LightToolHost) : ShellState
}

/**
 * Stands where LightOS does: holds the screen, lists the tools, opens one, and takes it back
 * when its last screen goes back.
 */
@Composable
fun LightOsShell() {
    var state by remember { mutableStateOf<ShellState>(ShellState.Starting) }
    var greyscale by remember { mutableStateOf(true) }
    var crash by remember { mutableStateOf(CrashGuard.lastCrash) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // What LightOS itself provides to every tool: the SDK's icons and the LP3 keyboard's.
        Assets.load("sdk-ui")
        Assets.load("keyboard")
        state = ShellState.Home
    }
    // A tool can ask for colour while it's open (LightDisplayColor), as LightOS lets it on the phone.
    val toolWantsColour by DisplayColor.toolWantsColour.collectAsState()
    val colourForTool = toolWantsColour && state is ShellState.Running
    LaunchedEffect(greyscale, colourForTool) { setGreyscale(greyscale && !colourForTool) }
    LaunchedEffect(DeviceMode.current) { MouseAsTouch.enabled = DeviceMode.current == DeviceMode.Lp3 }

    fun open(tool: LightTool) {
        crash = null
        CrashGuard.toolOpening(tool.name)
        scope.launch {
            Assets.load(tool.assetPack) { done, total -> state = ShellState.Loading(tool, done, total) }
            // What the tool saved before: Android reads its files synchronously, so they're read in now.
            WebFiles.load("/data/${tool.id}")
            // And its preferences, which tools may read synchronously while starting (see toolDataStore).
            warmToolDataStore(tool.id)
            val host = LightToolHost(tool) {
                CrashGuard.toolClosed()
                DisplayColor.toolWantsColour.value = false
                state = ShellState.Home
            }
            tool.entryPoint?.let { ep -> scope.launch { ep.onToolCreate(MutableStateFlow(null)) } }
            state = ShellState.Running(host)
        }
    }

    fun back() {
        when (val s = state) {
            is ShellState.Running -> s.host.back()
            else -> Unit
        }
    }

    // Listened for on the page, not through Compose focus: when the focused element leaves
    // the screen (a number wheel, a text field), Compose has nothing focused and its key
    // handlers hear nothing, but the phone's keys should always work.
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = listener@{ event ->
            val e = event as KeyboardEvent
            if (e.repeat && e.key == "Escape") return@listener
            val down = e.type == "keydown"
            if (e.key == "Escape") {
                if (down) back()
                return@listener
            }
            val running = state as? ShellState.Running ?: return@listener
            val code = toolKeyCode(e.key) ?: return@listener
            running.host.dispatchKey(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, if (e.repeat) 1 else 0))
        }
        window.addEventListener("keydown", listener)
        window.addEventListener("keyup", listener)
        onDispose {
            window.removeEventListener("keydown", listener)
            window.removeEventListener("keyup", listener)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Whole device pixels per dp, so pixel fonts and 1px rules land on the pixel grid.
            val scale = maxOf(1, minOf(constraints.maxWidth / SCREEN_W_DP, constraints.maxHeight / SCREEN_H_DP))
            val outer = LocalDensity.current
            val w = with(outer) { (SCREEN_W_DP * scale).toDp() }
            val h = with(outer) { (SCREEN_H_DP * scale).toDp() }
            Box(Modifier.size(w, h).background(Color.Black)) {
                // The phone's screen as far as tools can ask: its density, its configuration and its
                // window. Tools that size themselves from the window (light-amp scales its layout
                // from containerSize) would otherwise get the whole browser window.
                val outerWindow = LocalWindowInfo.current
                val screenWindow = remember(outerWindow, scale) { ScreenWindowInfo(outerWindow, IntSize(SCREEN_W_DP * scale, SCREEN_H_DP * scale)) }
                CompositionLocalProvider(
                    LocalDensity provides Density(scale.toFloat(), fontScale = 1f),
                    LocalConfiguration provides Configuration(SCREEN_W_DP, SCREEN_H_DP),
                    LocalWindowInfo provides screenWindow,
                ) {
                    val colors by LightThemeController.colors.collectAsState()
                    LightTheme(colors = colors) {
                        Screen(state, crash, onOpen = ::open)
                    }
                }
            }
        }
        Chrome(
            greyscale = greyscale,
            onGreyscale = { greyscale = !greyscale },
            onBack = ::back,
            canGoBack = state is ShellState.Running,
        )
    }
}

@Composable
private fun Screen(state: ShellState, crash: CrashGuard.Crash?, onOpen: (LightTool) -> Unit) {
    when (state) {
        ShellState.Starting -> Unit
        ShellState.Home -> Home(tools, crash, onOpen)
        is ShellState.Loading -> Loading(state.done, state.total)
        is ShellState.Running -> state.host.Content()
    }
}

/** Below the screen: what the phone's hardware and settings would do. */
@Composable
private fun Chrome(greyscale: Boolean, onGreyscale: () -> Unit, onBack: () -> Unit, canGoBack: Boolean) {
    val style = TextStyle(color = Color(0xFF8A8A8A), fontSize = 14.sp)
    Row(
        Modifier.fillMaxWidth().height(40.dp()).padding(horizontal = 16.dp()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            if (canGoBack) "‹ back  (esc)" else "light-web",
            style = style,
            modifier = Modifier.lightClickable(enabled = canGoBack, hapticsEnabled = false, onClick = onBack),
        )
        BasicText("   ·   ", style = style)
        BasicText(
            "mode: ${DeviceMode.current.label}",
            style = style,
            modifier = Modifier.lightClickable(hapticsEnabled = false) {
                DeviceMode.set(if (DeviceMode.current == DeviceMode.Lp3) DeviceMode.Computer else DeviceMode.Lp3)
            },
        )
        BasicText("   ·   ", style = style)
        BasicText(
            if (greyscale) "greyscale: on" else "greyscale: off",
            style = style,
            modifier = Modifier.lightClickable(hapticsEnabled = false, onClick = onGreyscale),
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** Keys a tool can handle (LightKeyHandler), from the computer's keyboard (DOM `KeyboardEvent.key`). */
private fun toolKeyCode(key: String): Int? = when (key) {
    "ArrowUp" -> KeyEvent.KEYCODE_DPAD_UP
    "ArrowDown" -> KeyEvent.KEYCODE_DPAD_DOWN
    "ArrowLeft" -> KeyEvent.KEYCODE_DPAD_LEFT
    "ArrowRight" -> KeyEvent.KEYCODE_DPAD_RIGHT
    "PageUp" -> KeyEvent.KEYCODE_VOLUME_UP
    "PageDown" -> KeyEvent.KEYCODE_VOLUME_DOWN
    else -> null
}

/** LightOS draws everything in greyscale unless a tool turns colour on; this is that filter. */
private fun setGreyscale(on: Boolean): Unit = js(
    "{ document.body.style.filter = on ? 'grayscale(1)' : ''; }"
)

/** The browser's window info, but sized as the phone's screen. */
private class ScreenWindowInfo(outer: WindowInfo, override val containerSize: IntSize) : WindowInfo by outer
