package lightweb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A tool's preferences DataStore, kept in localStorage as JSON. Each value is stored with its
 * type, since Preferences keys are typed and JSON numbers aren't.
 */
class WebPreferencesDataStore(name: String) : DataStore<Preferences> {
    private val storageKey = "lightweb:datastore:$name"
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            runCatching { localStorage.setItem(storageKey, encode(next).toString()) }
            next
        }

    private fun load(): Preferences {
        val text = runCatching { localStorage.getItem(storageKey) }.getOrNull() ?: return emptyPreferences()
        return runCatching { decode(Json.parseToJsonElement(text).jsonObject) }.getOrElse { emptyPreferences() }
    }

    private fun encode(p: Preferences): JsonObject = JsonObject(
        p.asMap().mapKeys { it.key.name }.mapValues { (_, v) ->
            val (type, value) = when (v) {
                is String -> "s" to JsonPrimitive(v)
                is Boolean -> "b" to JsonPrimitive(v)
                is Int -> "i" to JsonPrimitive(v)
                is Long -> "l" to JsonPrimitive(v)
                is Float -> "f" to JsonPrimitive(v)
                is Double -> "d" to JsonPrimitive(v)
                is Set<*> -> "ss" to JsonArray(v.map { JsonPrimitive(it as String) })
                else -> error("Unsupported preference type ${v::class.simpleName}")
            }
            JsonObject(mapOf("t" to JsonPrimitive(type), "v" to value))
        }
    )

    private fun decode(o: JsonObject): Preferences {
        val p = emptyPreferences().toMutablePreferences()
        for ((name, entry) in o) {
            val e = entry.jsonObject
            val v: JsonElement = e["v"] ?: continue
            when (e["t"]?.jsonPrimitive?.content) {
                "s" -> p[stringPreferencesKey(name)] = v.jsonPrimitive.content
                "b" -> p[booleanPreferencesKey(name)] = v.jsonPrimitive.boolean
                "i" -> p.setInt(name, v.jsonPrimitive.content.toInt())
                "l" -> p[longPreferencesKey(name)] = v.jsonPrimitive.content.toLong()
                "f" -> p.setFloat(name, v.jsonPrimitive.content.toFloat())
                "d" -> p[doublePreferencesKey(name)] = v.jsonPrimitive.content.toDouble()
                "ss" -> p[stringSetPreferencesKey(name)] = v.jsonArray.map { it.jsonPrimitive.content }.toSet()
            }
        }
        return p
    }
}

private fun MutablePreferences.setInt(name: String, v: Int) {
    this[androidx.datastore.preferences.core.intPreferencesKey(name)] = v
}

private fun MutablePreferences.setFloat(name: String, v: Float) {
    this[androidx.datastore.preferences.core.floatPreferencesKey(name)] = v
}
