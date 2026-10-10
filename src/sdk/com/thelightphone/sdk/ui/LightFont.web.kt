package com.thelightphone.sdk.ui

import android.content.Context
import androidx.compose.ui.text.font.FontFamily

/**
 * LightOS's typeface is Akkurat, which LP3s have as a system font and which can't be served
 * from a public page under its licence. Until there's a licensed or look-alike face to bundle,
 * SDK text is in the browser's default sans. Tools that bundle their own fonts are unaffected.
 */
fun lightFontFamily(context: Context): FontFamily = FontFamily.SansSerif
