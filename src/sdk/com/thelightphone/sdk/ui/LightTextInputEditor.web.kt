package com.thelightphone.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.text.KeyboardOptions as ComposeKeyboardOptions

private const val INPUT_UNDERLINE_THICKNESS_PX = 3f
private const val INPUT_UNDERLINE_GAP_GRID_UNITS = 0.5f

fun defaultKeyboardOptions() = KeyboardOptions(
    displayReturn = true,
    displayVoice = false,
    enableKeyAnimation = true,
    swipeEnabled = false,
)

/**
 * The SDK's full-screen text entry, laid out as upstream's (top bar, underlined heading-style
 * input, bottom bar) but typed into from the computer's keyboard, or a phone's own, instead
 * of the embedded LP3 keyboard. Return submits a single-line field.
 */
@Composable
fun LightTextInputEditor(
    title: String,
    state: TextFieldState,
    onSubmit: (CharSequence) -> Unit,
    onBack: () -> Unit,
    keyboardOptionsFlow: StateFlow<KeyboardOptions>,
    modifier: Modifier = Modifier,
    submitLabel: String = "SUBMIT",
    submitIcon: LightIconConfiguration? = null,
    leftBottomBarItem: LightBottomBarItem? = null,
    rightBottomBarItem: LightBottomBarItem? = null,
    showBackButton: Boolean = true,
    singleLine: Boolean = false,
    initialCaps: Boolean = false,
    editorKey: Any = remember { Any() },
) {
    val colors = LightThemeTokens.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface {
        Column(modifier = modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = if (showBackButton) LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack) else null,
                center = LightTopBarCenter.Text(title),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp()),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        state = state,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = lightInputTextStyle(),
                        cursorBrush = SolidColor(colors.content),
                        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
                        keyboardOptions = ComposeKeyboardOptions(
                            capitalization = if (initialCaps) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                        ),
                        onKeyboardAction = KeyboardActionHandler { if (singleLine) onSubmit(state.text) },
                    )
                    Spacer(Modifier.height(INPUT_UNDERLINE_GAP_GRID_UNITS.gridUnitsAsDp()))
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(INPUT_UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
                            .background(colors.content),
                    )
                }
            }

            val submitItem: LightBottomBarItem = when (submitIcon) {
                null -> LightBarButton.Text(text = submitLabel, onClick = { onSubmit(state.text) })
                else -> LightBarButton.LightIcon(icon = submitIcon, onClick = { onSubmit(state.text) }, contentDescription = submitLabel)
            }
            LightBottomBar(
                items = if (leftBottomBarItem == null && rightBottomBarItem == null) {
                    listOf(submitItem)
                } else {
                    listOf(leftBottomBarItem, submitItem, rightBottomBarItem)
                },
            )
        }
    }
}

@Composable
private fun lightInputTextStyle(): TextStyle {
    val colors = LightThemeTokens.colors
    return LightThemeTokens.typography.heading
        .copy(color = colors.content)
        .scaledForScreenHeight()
}
