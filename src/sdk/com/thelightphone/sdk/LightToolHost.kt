package com.thelightphone.sdk

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.thelightphone.sdk.ui.LightModalManager
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import lightweb.LightTool

private class ScreenViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private class BackStackEntry<T>(
    val screen: SimpleLightScreen<T>,
    val callback: ((T) -> Unit)? = null,
) {
    // As in LightActivity: composables inside a screen that call viewModel() get a store scoped
    // to this navigation, not one that outlives it.
    val viewModelStoreOwner: ViewModelStoreOwner =
        screen as? ViewModelStoreOwner ?: ScreenViewModelStoreOwner()

    fun deliverResult() {
        val result = screen.lwResult ?: return
        callback?.invoke(result)
    }
}

/**
 * Runs one tool: what `LightActivity` is on the phone. The back stack and its show / hide /
 * destroy order are LightActivity's, so screens see the same lifecycle. [onFinish] is called
 * when the last screen goes back, where an Activity would finish().
 */
class LightToolHost(
    val tool: LightTool,
    private val onFinish: () -> Unit,
) {
    private val backStack = mutableListOf<BackStackEntry<*>>()
    private val currentScreen = mutableStateOf<BackStackEntry<*>?>(null)

    init {
        val initial = BackStackEntry(tool.initialScreen(SealedLightActivity(this)))
        backStack.add(initial)
        currentScreen.value = initial
        initial.screen.notifyWillShow()
    }

    internal fun <T> navigateTo(screen: SimpleLightScreen<T>, resultCallback: ((T) -> Unit)? = null) {
        currentScreen.value?.screen?.notifyWillHide()
        val entry = BackStackEntry(screen, resultCallback)
        backStack.add(entry)
        screen.notifyWillShow()
        currentScreen.value = entry
    }

    internal fun popToRoot() {
        if (backStack.size <= 1) {
            backStack.lastOrNull()?.screen?.notifyWillShow()
            return
        }
        while (backStack.size > 1) {
            val entry = backStack.last()
            entry.screen.notifyWillHide()
            entry.screen.destroy()
            entry.viewModelStoreOwner.viewModelStore.clear()
            backStack.removeAt(backStack.lastIndex)
        }
        currentScreen.value = backStack.last()
        backStack.last().screen.notifyWillShow()
    }

    internal fun goBack() {
        val current = currentScreen.value ?: return
        val popped = current.screen
        popped.notifyWillHide()
        popped.destroy()
        current.viewModelStoreOwner.viewModelStore.clear()
        backStack.removeAt(backStack.lastIndex)
        if (backStack.isEmpty()) {
            currentScreen.value = null
            onFinish()
            return
        }
        val previous = backStack.last()
        previous.screen.notifyWillShow()
        currentScreen.value = previous
        current.deliverResult()
    }

    /** The system back gesture: the screen's own goBack, so a LightScreen's view model can veto it. */
    fun back() {
        currentScreen.value?.screen?.goBack(null)
    }

    /** Leaving the tool for the shell, as onPause would. */
    fun pause() {
        currentScreen.value?.screen?.notifyAppPause()
    }

    /** Hands a key to the current screen, as LightActivity.onKeyDown/onKeyUp do. */
    fun dispatchKey(event: KeyEvent): Boolean {
        val screen = currentScreen.value?.screen ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> screen.onKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> screen.onKeyUp(event.keyCode, event)
            else -> false
        }
    }

    @Composable
    fun Content() {
        Box(modifier = Modifier.fillMaxSize()) {
            val entry = currentScreen.value
            if (entry != null) {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides entry.viewModelStoreOwner,
                    // The phone reads this from LightOS preferences; it's on by default there.
                    LocalHapticsEnabled provides true,
                    content = { entry.screen.Content() },
                )
            }
            // Transient modals draw on top of the current screen.
            val activeModal by LightModalManager.activeModal.collectAsState()
            activeModal?.Content()
        }
    }
}
