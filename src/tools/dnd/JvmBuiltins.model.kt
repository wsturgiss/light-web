@file:Suppress("PackageDirectoryMismatch")

package com.wsturgiss.dnd.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// See JvmBuiltins.kt.

internal typealias Math = lightweb.compat.JvmMath
internal typealias System = lightweb.compat.JvmSystem

/** kotlin.jvm.Volatile. The page has one thread, so there's nothing for it to do. */
internal annotation class Volatile

/** Store saves on Dispatchers.IO; the page has no thread pool, and localStorage is synchronous anyway. */
internal val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.Default
