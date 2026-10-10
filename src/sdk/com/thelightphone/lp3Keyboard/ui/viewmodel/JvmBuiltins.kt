package com.thelightphone.lp3Keyboard.ui.viewmodel

import lightweb.compat.jvmAppendCodePoint
import lightweb.compat.jvmCodePointAt

// Java code-point helpers this package uses unqualified; see lightweb.compat.CodePoints.

internal typealias Character = lightweb.compat.JvmCharacter

internal fun String.codePointAt(index: Int): Int = jvmCodePointAt(index)

internal fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder = jvmAppendCodePoint(codePoint)
