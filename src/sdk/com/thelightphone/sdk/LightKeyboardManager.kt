package com.thelightphone.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * On the phone this asks LightOS for the user's keyboard settings. There's no LightOS here to
 * ask, so it's the defaults.
 */
@Composable
fun rememberKeyboardOptions(
    initialOptions: KeyboardOptions = defaultKeyboardOptions(),
    allowDictationKey: Boolean = false,
): StateFlow<KeyboardOptions> = remember { MutableStateFlow(initialOptions) }

/** On the phone, the user's keyboard settings from LightOS. Here, the defaults. */
suspend fun refreshKeyboardOptions(): KeyboardOptions? = defaultKeyboardOptions()
