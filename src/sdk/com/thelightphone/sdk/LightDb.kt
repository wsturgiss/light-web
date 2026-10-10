package com.thelightphone.sdk

import androidx.room.RoomDatabase
import kotlin.reflect.KClass

/**
 * The SDK's `buildDatabase`. Room has no wasm target, so there's nothing to build from the
 * class: the tool's registration (LightTool.databases) supplies a hand-written web
 * implementation, and this hands it out, one per name, for the life of the page.
 */
fun <T : RoomDatabase> SealedLightContext.buildDatabase(dbClass: KClass<T>, dbName: String?): T {
    val tool = host.tool
    val key = "${tool.id}/${dbName ?: dbClass.simpleName}"
    @Suppress("UNCHECKED_CAST")
    return openDatabases.getOrPut(key) {
        val factory = tool.databases[dbClass]
            ?: error("${tool.name} has no web implementation of ${dbClass.simpleName}; add one to its LightTool.databases")
        factory(dbName ?: dbClass.simpleName ?: "db")
    } as T
}

private val openDatabases = HashMap<String, RoomDatabase>()
