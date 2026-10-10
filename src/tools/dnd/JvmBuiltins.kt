// JVM names light-dnd uses without an import (Math, System, @Volatile, String.format,
// toSortedMap, Dispatchers.IO). On the JVM they're default imports or JVM-only stdlib; on wasm
// they have to be declared in each package that uses them, which these files do. Each would go
// away with a small change in light-dnd (Int.floorDiv, Clock.System, a Kotlin number
// formatter...), whenever that's worth doing.

@file:Suppress("PackageDirectoryMismatch")

package com.wsturgiss.dnd

import lightweb.compat.sortedByKey

/** `"%.1f".format(x)` and friends. */
internal fun String.format(vararg args: Any?): String = lightweb.compat.formatJvm(this, *args)

/** `Map.toSortedMap()`. */
internal fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): Map<K, V> = sortedByKey()
