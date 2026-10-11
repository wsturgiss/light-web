package com.thelightphone.sdk

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlin.reflect.KClass

/**
 * The SDK's `buildDatabase`, on Room 3: the tool's own DAOs and SQL, run by Room's generated code
 * against SQLite in the browser (see lightweb.openWebDatabase). Reified because Room's web
 * builder is; tools call it with `Foo::class.java`, which is the KClass here.
 */
inline fun <reified T : RoomDatabase> SealedLightContext.buildDatabase(dbClass: KClass<T>, dbName: String?): T =
    openDatabase(dbName ?: dbClass.simpleName ?: "db") { Room.databaseBuilder<T>(it) }

@PublishedApi
internal fun <T : RoomDatabase> SealedLightContext.openDatabase(name: String, builder: (fileName: String) -> RoomDatabase.Builder<T>): T =
    lightweb.openWebDatabase(host.tool.id, name, builder)
