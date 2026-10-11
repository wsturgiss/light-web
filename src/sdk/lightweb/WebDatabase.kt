package lightweb

import androidx.room.RoomDatabase
import kotlin.reflect.KClass

/**
 * Marks a hand-written web implementation of a tool's Room database [of] (Room has no wasm
 * target). The build finds it under src/tools/<pack> and adds it to that tool's generated
 * registration, as KSP finds @InitialScreen on Android. The class takes the database name.
 */
@Target(AnnotationTarget.CLASS)
annotation class WebDatabase(val of: KClass<out RoomDatabase>)
