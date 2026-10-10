package com.thelightphone.sdk

import androidx.compose.runtime.Composable
import com.thelightphone.sdk.ui.LightKeyHandler
import java.io.File
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import lightweb.Assets
import lightweb.WebPreferencesDataStore

/**
 * The SDK's screen, with the same public API as on Android. Navigation goes to the
 * [LightToolHost] that stands where `LightActivity` does on the phone.
 */
// Unlike the Android SDK, this shares a Kotlin module with the tools, so its `internal` members
// are visible to them and can collide with theirs; hence the lw prefix.
abstract class SimpleLightScreen<ResultType>(sealedActivity: SealedLightActivity) :
    LightKeyHandler {
    internal val lwHost = sealedActivity.activity
    internal var lwResult: ResultType? = null
    protected val lightContext = SealedLightContext(sealedActivity.activity)

    @Composable
    abstract fun Content()
    open fun willShow() {}
    open fun willHide() {}
    open fun onAppPause() {}
    open fun onScreenDestroy() {}

    internal open fun notifyWillShow() {
        willShow()
    }

    internal open fun notifyWillHide() {
        willHide()
    }

    internal open fun notifyAppPause() {
        onAppPause()
    }

    internal open fun destroy() {
        onScreenDestroy()
    }

    fun <T> navigateTo(screenFactory: (SealedLightActivity) -> SimpleLightScreen<T>, resultCallback: ((T) -> Unit)? = null) {
        val screen = screenFactory(SealedLightActivity(lwHost))
        lwHost.navigateTo(screen, resultCallback)
    }

    open fun goBack(result: ResultType? = null) {
        this.lwResult = result
        lwHost.goBack()
    }

    /** Pops every screen above the initial one (the fork's SDK patch; harmless where unused). */
    fun popToRoot() {
        lwHost.popToRoot()
    }
}

/**
 * What a tool gets instead of an Android Context. [filesDir] and [dataStore] are kept in
 * localStorage; assets come from the tool's pack, loaded before its first screen.
 */
class SealedLightContext internal constructor(internal val host: LightToolHost) {
    val filesDir: File by lazy { File("/data/${host.tool.id}/files") }
    val dataStore: DataStore<Preferences> by lazy { dataStores.getOrPut(host.tool.id) { WebPreferencesDataStore(host.tool.id) } }
    fun readAsset(path: String): ByteArray = Assets.readAsset(host.tool.assetPack, path)
}

// One per tool for the life of the page, as Android's preferencesDataStore delegate gives one per app.
private val dataStores = HashMap<String, DataStore<Preferences>>()

/** Wraps the host so user code can pass it around without reaching into it, as on Android. */
class SealedLightActivity(internal val activity: LightToolHost)
