package android.content

/**
 * Stands in for `android.content.Context` where SDK UI code passes one along (to haptics, to font
 * lookup). There is one, [WebContext].
 */
open class Context internal constructor() {
    open val packageName: String get() = "lightweb"
    val resources: android.content.res.Resources = android.content.res.Resources()
}

object WebContext : Context()
