package lightweb

import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen

/**
 * A tool the shell can open. On Android the SDK's KSP processor finds the @InitialScreen and
 * @EntryPoint classes and generates a registry; here each tool lists them by hand in src/tools.
 *
 * [assetPack] names the pack (see build.gradle.kts) holding the tool's assets and res/.
 */
class LightTool(
    val id: String,
    val name: String,
    val assetPack: String,
    val entryPoint: LightEntryPoint?,
    val initialScreen: (SealedLightActivity) -> SimpleLightScreen<*>,
)
