package androidx.compose.ui.text.font

import lightweb.Assets

/** Android's `Font(R.font.x)`, from the font file's bytes in its (already loaded) pack. */
fun Font(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = androidx.compose.ui.text.platform.Font(
    identity = Assets.pathOf(resId),
    data = Assets.resource(resId),
    weight = weight,
    style = style,
)
