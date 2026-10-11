package lightweb.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import lightweb.LightTool

/**
 * The toolbox: the time, then every tool by name, as LightOS lists them. [crash] is a tool
 * that has just stopped (see CrashGuard), said above the list until another tool is opened.
 */
@Composable
fun Home(tools: List<LightTool>, crash: CrashGuard.Crash?, onOpen: (LightTool) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
    ) {
        LightText(clock(), LightTextVariant.Detail, lighten = true)
        if (crash != null) {
            Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
            LightText("${crash.tool} stopped", LightTextVariant.Heading)
            LightText(crash.summary, LightTextVariant.Detail, lighten = true)
        }
        Spacer(Modifier.height(3f.gridUnitsAsDp()))
        Column(verticalArrangement = Arrangement.spacedBy(1.5f.gridUnitsAsDp())) {
            for (tool in tools) {
                LightText(
                    tool.name,
                    LightTextVariant.Title,
                    modifier = Modifier.fillMaxWidth().lightClickable { onOpen(tool) },
                )
            }
        }
    }
}

/** While a tool's assets come down; [done] of [total] files. */
@Composable
fun Loading(done: Int, total: Int) {
    Box(
        Modifier.fillMaxSize().background(LightThemeTokens.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        LightText(if (total == 0) "Opening…" else "Opening… ${done * 100 / total}%", LightTextVariant.Heading, lighten = true)
    }
}

@Composable
private fun clock(): String {
    var now by remember { mutableStateOf(timeNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = timeNow()
        }
    }
    return now
}

private fun timeNow(): String = js(
    "new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date())"
)
