package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

// DataStore's Preferences, as tools use them: typed keys over a small map. Stored by the SDK
// (SealedLightContext.dataStore) in localStorage; see lightweb.WebPreferencesDataStore.

abstract class Preferences internal constructor() {
    class Key<T> internal constructor(val name: String) {
        override fun equals(other: Any?) = other is Key<*> && other.name == name
        override fun hashCode() = name.hashCode()
        override fun toString() = name
        infix fun to(value: T): Pair<T> = Pair(this, value)
    }

    class Pair<T> internal constructor(internal val key: Key<T>, internal val value: T)

    abstract fun <T> contains(key: Key<T>): Boolean
    abstract operator fun <T> get(key: Key<T>): T?
    abstract fun asMap(): Map<Key<*>, Any>

    fun toMutablePreferences(): MutablePreferences = MutablePreferences(asMap().toMutableMap())
    fun toPreferences(): Preferences = MutablePreferences(asMap().toMutableMap())
}

class MutablePreferences internal constructor(
    internal val values: MutableMap<Preferences.Key<*>, Any> = mutableMapOf(),
) : Preferences() {
    override fun <T> contains(key: Key<T>) = key in values
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: Key<T>): T? = values[key] as T?
    override fun asMap(): Map<Key<*>, Any> = values.toMap()

    operator fun <T> set(key: Key<T>, value: T) {
        if (value == null) values.remove(key) else values[key] = value as Any
    }
    fun <T> remove(key: Key<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return values.remove(key) as T?
    }
    fun clear() = values.clear()
    operator fun plusAssign(pair: Preferences.Pair<*>) { values[pair.key] = pair.value as Any }
    override fun equals(other: Any?) = other is MutablePreferences && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = values.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
}

fun stringPreferencesKey(name: String) = Preferences.Key<String>(name)
fun intPreferencesKey(name: String) = Preferences.Key<Int>(name)
fun longPreferencesKey(name: String) = Preferences.Key<Long>(name)
fun booleanPreferencesKey(name: String) = Preferences.Key<Boolean>(name)
fun floatPreferencesKey(name: String) = Preferences.Key<Float>(name)
fun doublePreferencesKey(name: String) = Preferences.Key<Double>(name)
fun stringSetPreferencesKey(name: String) = Preferences.Key<Set<String>>(name)

fun emptyPreferences(): Preferences = MutablePreferences()
fun mutablePreferencesOf(vararg pairs: Preferences.Pair<*>): MutablePreferences =
    MutablePreferences().apply { pairs.forEach { plusAssign(it) } }

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences =
    updateData { it.toMutablePreferences().apply { transform(this) } }
