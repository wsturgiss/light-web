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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.designVerticalPxToSp
import kotlinx.coroutines.delay
import lightweb.LightTool

/**
 * The toolbox, as LightOS shows it: every tool by name, alphabetically, centred, six to a page (fewer
 * if six don't fit). Swipe up and down to change pages; dots down the right edge say which page
 * this is, filled for the current one and rings for the rest. [crash] is a tool that has just stopped (see CrashGuard), said above the
 * first page's names until another tool is opened.
 */
@Composable
fun Home(tools: List<LightTool>, crash: CrashGuard.Crash?, onOpen: (LightTool) -> Unit) {
    val sorted = remember(tools) { tools.sortedBy { it.name.lowercase() } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
    ) {
        // A name is one line of Subtitle text (as LightText scales it), and the gap after it.
        val title = LightThemeTokens.typography.subtitle
        val line = (if (title.lineHeight.isSpecified) title.lineHeight else title.fontSize * 1.2f).value.designVerticalPxToSp()
        val rowHeight = with(LocalDensity.current) { line.toDp() } + 1.5f.gridUnitsAsDp()
        // The last name has no gap after it.
        val perPage = ((maxHeight + 1.5f.gridUnitsAsDp()) / rowHeight).toInt().coerceIn(1, PER_PAGE)
        val pages = sorted.chunked(perPage).ifEmpty { listOf(emptyList()) }
        val pageHeight = with(LocalDensity.current) { maxHeight.toPx() }
        var page by remember { mutableStateOf(0) }
        if (page > pages.lastIndex) page = pages.lastIndex
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        fun settle(to: Int) {
            val target = to.coerceIn(0, pages.lastIndex)
            scope.launch {
                // Hand the offset over to the new page, then let it come to rest.
                drag.snapTo(drag.value + (target - page) * pageHeight)
                page = target
                drag.animateTo(0f)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(pages.size, pageHeight) { swipePages(pages.size, pageHeight, { page }, drag, ::settle) },
        ) {
            pages.forEachIndexed { index, names ->
                if (abs(index - page) > 1) return@forEachIndexed
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (index - page) * pageHeight + drag.value }
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (crash != null && index == 0) {
                        LightText("${crash.tool} stopped", LightTextVariant.Heading, align = TextAlign.Center)
                        LightText(crash.summary, LightTextVariant.Detail, align = TextAlign.Center, lighten = true)
                        Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.5f.gridUnitsAsDp()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (tool in names) {
                            LightText(
                                tool.name,
                                LightTextVariant.Subtitle,
                                align = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().lightClickable { onOpen(tool) },
                            )
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val color = LightThemeTokens.colors.content
                repeat(pages.size) { i ->
                    Canvas(Modifier.size(8.dp)) {
                        val ring = size.minDimension * 0.18f
                        if (i == page) drawCircle(color)
                        else drawCircle(color, radius = (size.minDimension - ring) / 2, style = Stroke(ring))
                    }
                }
            }
        }
    }
}

/**
 * Turns pages: a vertical drag moves them with the finger and, let go, settles on the next page if
 * it went a fifth of the way or was flung, else back. Watched before the names see it
 * ([PointerEventPass.Initial]), so a drag that turns pages doesn't also open a tool. The mouse
 * wheel turns a page per notch.
 */
private suspend fun PointerInputScope.swipePages(
    count: Int,
    pageHeight: Float,
    page: () -> Int,
    drag: Animatable<Float, *>,
    settle: (Int) -> Unit,
) = coroutineScope {
    launch {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                if (dy != 0.0) settle(page() + if (dy > 0) 1 else -1)
                event.changes.forEach { it.consume() }
            }
        }
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var dy = 0f
        var dragging = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            velocity.addPosition(change.uptimeMillis, change.position)
            if (!change.pressed) {
                if (dragging) change.consume()
                break
            }
            dy += change.positionChange().y
            if (!dragging && abs(dy) > viewConfiguration.touchSlop) dragging = true
            if (dragging) {
                change.consume()
                // Past the first and last pages the drag gives a little, then stops.
                val atEdge = (dy > 0 && page() == 0) || (dy < 0 && page() == count - 1)
                launch { drag.snapTo(if (atEdge) dy / 4 else dy) }
            }
        }
        if (!dragging) return@awaitEachGesture
        val fling = velocity.calculateVelocity().y
        val step = when {
            dy < -pageHeight / 5 || fling < -1000f -> 1
            dy > pageHeight / 5 || fling > 1000f -> -1
            else -> 0
        }
        settle(page() + step)
    }
}

/** Names on a full page, as on the phone. */
private const val PER_PAGE = 6

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
