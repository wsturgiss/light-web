@file:Suppress("PackageDirectoryMismatch", "unused")

package com.thelightphone.training

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

// JVM names this package uses without an import; on wasm they have to be declared here.
// Same set in each package (see light-dnd's src/tools/dnd/JvmBuiltins.kt for why).

internal typealias Math = lightweb.compat.JvmMath
internal typealias System = lightweb.compat.JvmSystem

/** kotlin.jvm.Volatile / Synchronized. The page has one thread. */
internal annotation class Volatile
internal annotation class Synchronized

/** `"%d:%02d".format(...)`. */
internal fun String.format(vararg args: Any?): String = lightweb.compat.formatJvm(this, *args)

/** `Foo::class.java`: the SDK takes the KClass itself on the web. */
internal val <T : Any> KClass<T>.java: KClass<T> get() = this

/** The page has no thread pool; work runs on the one thread. */
internal val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.Default

/** `java.lang.Class`, as the type of `Foo::class.java` above. */
internal typealias Class<T> = KClass<T>

/** kotlin.synchronized. The page has one thread. */
internal inline fun <R> synchronized(lock: Any, block: () -> R): R = block()
