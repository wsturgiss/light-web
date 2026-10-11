package lightweb

import androidx.room.RoomDatabase
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import kotlin.reflect.KClass

/**
 * A tool the shell can open. On Android the SDK's KSP processor finds the @InitialScreen and
 * @EntryPoint classes and generates a registry; here the build does the same, writing one of
 * these per tool pack (generateToolRegistry in build.gradle.kts).
 *
 * [assetPack] names the pack (see build.gradle.kts) holding the tool's assets and res/.
 */
class LightTool(
    val id: String,
    val name: String,
    val assetPack: String,
    val entryPoint: LightEntryPoint?,
    val initialScreen: (SealedLightActivity) -> SimpleLightScreen<*>,
    /**
     * Web implementations of the tool's Room databases, by database class; the factory gets the
     * database name. Room has no wasm target, so these are written by hand, marked @WebDatabase
     * (see buildDatabase).
     */
    val databases: Map<KClass<out RoomDatabase>, (name: String) -> RoomDatabase> = emptyMap(),
)
