package androidx.room

import kotlin.reflect.KClass

// Room's annotations, so a tool's entities, DAOs and database class compile unchanged. Nothing
// generates code from them here: Room has no wasm target, so a tool's database is implemented
// for the web by hand in src/tools/<tool> and handed out by buildDatabase (see LightTool).

@Target(AnnotationTarget.CLASS) annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = [],
    val foreignKeys: Array<ForeignKey> = [],
)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ColumnInfo(val name: String = "", val defaultValue: String = "", val index: Boolean = false)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Embedded(val prefix: String = "")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Relation(
    val entity: KClass<*> = Any::class,
    val parentColumn: String,
    val entityColumn: String,
    val projection: Array<String> = [],
)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Ignore
annotation class Index(vararg val value: String, val unique: Boolean = false, val name: String = "")
annotation class ForeignKey(
    val entity: KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = 1,
    val onUpdate: Int = 1,
)

@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Delete
@Target(AnnotationTarget.FUNCTION) annotation class Upsert
@Target(AnnotationTarget.FUNCTION) annotation class Transaction

@Target(AnnotationTarget.CLASS) annotation class Database(
    val entities: Array<KClass<*>>,
    val views: Array<KClass<*>> = [],
    val version: Int,
    val exportSchema: Boolean = true,
    val autoMigrations: Array<AutoMigration> = [],
)
annotation class AutoMigration(val from: Int, val to: Int, val spec: KClass<*> = Any::class)
@Target(AnnotationTarget.CLASS) annotation class TypeConverters(vararg val value: KClass<*>)
@Target(AnnotationTarget.FUNCTION) annotation class TypeConverter

object OnConflictStrategy {
    const val NONE = 0
    const val REPLACE = 1
    const val ROLLBACK = 2
    const val ABORT = 3
    const val FAIL = 4
    const val IGNORE = 5
}

/** A tool's database class extends this; its web implementation does the rest. */
abstract class RoomDatabase {
    open fun close() {}
}
