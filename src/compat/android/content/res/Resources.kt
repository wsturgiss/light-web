package android.content.res

import lightweb.generated.resourceIds

/**
 * Looks resources up by name, as the SDK does for an optional local copy of Akkurat
 * (`akkuratll_regular` and so on in some pack's res/font). Put one in a pack and it's used.
 */
class Resources internal constructor() {
    fun getIdentifier(name: String, defType: String, defPackage: String?): Int =
        resourceIds["$defType/$name"] ?: 0
}
