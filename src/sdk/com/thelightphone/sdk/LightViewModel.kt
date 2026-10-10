package com.thelightphone.sdk

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import com.thelightphone.sdk.ui.LightKeyHandler
import kotlin.reflect.KClass

// The SDK's LightScreen and LightViewModel, as on Android. The one difference is the type of
// viewModelClass: a KClass, since there's no java.lang.Class here. Tools write
// `Foo::class.java` and a per-package `java` extension (src/tools/<tool>/JvmBuiltins*.kt) hands
// back the KClass.

abstract class LightViewModel<T> : ViewModel(), LightKeyHandler {
    open fun onScreenShow(screen: SimpleLightScreen<T>) {}
    open fun onScreenHide(screen: SimpleLightScreen<T>) {}
    open fun onAppPause() {}

    /** Returns false if the ViewModel does not consume the back button event. */
    open fun onBackPressed(): Boolean = false
}

abstract class LightScreen<ResultType, VM : LightViewModel<ResultType>>(
    sealedActivity: SealedLightActivity
) : SimpleLightScreen<ResultType>(sealedActivity), ViewModelStoreOwner {
    abstract val viewModelClass: KClass<VM>
    abstract fun createViewModel(): VM

    override val viewModelStore: ViewModelStore = ViewModelStore()

    @Suppress("UNCHECKED_CAST")
    val viewModel: VM by lazy {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
                createViewModel() as T
        }
        ViewModelProvider.create(this, factory)[viewModelClass]
    }

    override fun destroy() {
        super.destroy()
        viewModelStore.clear()
    }

    override fun notifyWillShow() {
        super.notifyWillShow()
        viewModel.onScreenShow(this)
    }

    override fun notifyWillHide() {
        super.notifyWillHide()
        viewModel.onScreenHide(this)
    }

    override fun notifyAppPause() {
        super.notifyAppPause()
        viewModel.onAppPause()
    }

    override fun goBack(result: ResultType?) {
        if (!viewModel.onBackPressed()) {
            super.goBack(result)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = viewModel.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = viewModel.onKeyUp(keyCode, event)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent) =
        viewModel.onKeyMultiple(keyCode, repeatCount, event)
}
