package com.thelightphone.sdk

import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

// The SDK's annotations and entry point, as the Android SDK declares them (less @Keep). On
// Android, KSP finds them and generates a registry; here the build does (generateToolRegistry).

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EntryPoint

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InitialScreen

interface LightEntryPoint {
    suspend fun onToolCreate(serverData: StateFlow<LightServerData?>): Unit = Unit

    suspend fun onPushNotification(data: ByteArray): Unit = Unit

    val enablePushNotifications: Boolean
        get() = false

    val enableRecentsScreenshots: Boolean
        get() = false
}
