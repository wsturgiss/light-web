package com.thelightphone.lp3Keyboard.ui

/**
 * The LP3 keyboard library's options type, which the SDK's public API carries. The library
 * itself (lightphone/light-keyboard) is Android-only for now, so text entry on the web uses
 * the computer's keyboard; see LightTextInputEditor.web.kt.
 */
data class KeyboardOptions(
    val emojis: List<String> = emptyList(),
    val displayReturn: Boolean = true,
    val displayVoice: Boolean = false,
    val enableKeyAnimation: Boolean = true,
    val swipeEnabled: Boolean = false,
)
