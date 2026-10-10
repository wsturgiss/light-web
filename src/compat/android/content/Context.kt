package android.content

/**
 * Stands in for `android.content.Context` where SDK UI code only passes one along (to haptics,
 * to font lookup). Nothing reads from it; there is one, [WebContext].
 */
open class Context internal constructor() {
    open val packageName: String get() = "lightweb"
}

object WebContext : Context()
