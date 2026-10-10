package com.thelightphone.sdk.ui

import android.content.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The phone's vibration motor, as `navigator.vibrate` (a no-op on browsers without one). */
object LightHapticFeedback {

    fun click(context: Context) = vibrateForDuration(context, 45.milliseconds)

    fun vibrateForDuration(context: Context, duration: Duration) {
        vibrate(duration.inWholeMilliseconds.toInt())
    }
}

private fun vibrate(ms: Int): Unit = js("{ if (navigator.vibrate) navigator.vibrate(ms); }")
