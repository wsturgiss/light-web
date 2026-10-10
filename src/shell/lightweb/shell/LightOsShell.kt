package lightweb.shell

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.LightToolHost
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import lightweb.Assets
import lightweb.DeviceMode
import lightweb.LightTool

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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // What LightOS itself provides to every tool: the SDK's icons and the LP3 keyboard's.
        Assets.load("sdk-ui")
        Assets.load("keyboard")
        state = ShellState.Home
    }
    LaunchedEffect(greyscale) { setGreyscale(greyscale) }

    fun open(tool: LightTool) {
        scope.launch {
            Assets.load(tool.assetPack) { done, total -> state = ShellState.Loading(tool, done, total) }
            val host = LightToolHost(tool) { state = ShellState.Home }
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

    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { keys.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .focusRequester(keys)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Escape && e.type == KeyEventType.KeyDown) {
                    back()
                    return@onPreviewKeyEvent true
                }
                val running = state as? ShellState.Running ?: return@onPreviewKeyEvent false
                val code = toolKeyCode(e.key) ?: return@onPreviewKeyEvent false
                val action = if (e.type == KeyEventType.KeyDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                running.host.dispatchKey(KeyEvent(action, code))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Whole device pixels per dp, so pixel fonts and 1px rules land on the pixel grid.
            val scale = maxOf(1, minOf(constraints.maxWidth / SCREEN_W_DP, constraints.maxHeight / SCREEN_H_DP))
            val outer = LocalDensity.current
            val w = with(outer) { (SCREEN_W_DP * scale).toDp() }
            val h = with(outer) { (SCREEN_H_DP * scale).toDp() }
            Box(Modifier.size(w, h).background(Color.Black)) {
                CompositionLocalProvider(
                    LocalDensity provides Density(scale.toFloat(), fontScale = 1f),
                    LocalConfiguration provides Configuration(SCREEN_W_DP, SCREEN_H_DP),
                ) {
                    val colors by LightThemeController.colors.collectAsState()
                    LightTheme(colors = colors) {
                        Screen(state, onOpen = ::open)
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
private fun Screen(state: ShellState, onOpen: (LightTool) -> Unit) {
    when (state) {
        ShellState.Starting -> Unit
        ShellState.Home -> Home(tools, onOpen)
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

/** Keys a tool can handle (LightKeyHandler), from the computer's keyboard. */
private fun toolKeyCode(key: Key): Int? = when (key) {
    Key.DirectionUp -> KeyEvent.KEYCODE_DPAD_UP
    Key.DirectionDown -> KeyEvent.KEYCODE_DPAD_DOWN
    Key.DirectionLeft -> KeyEvent.KEYCODE_DPAD_LEFT
    Key.DirectionRight -> KeyEvent.KEYCODE_DPAD_RIGHT
    Key.PageUp -> KeyEvent.KEYCODE_VOLUME_UP
    Key.PageDown -> KeyEvent.KEYCODE_VOLUME_DOWN
    else -> null
}

/** LightOS draws everything in greyscale unless a tool turns colour on; this is that filter. */
private fun setGreyscale(on: Boolean): Unit = js(
    "{ document.body.style.filter = on ? 'grayscale(1)' : ''; }"
)
