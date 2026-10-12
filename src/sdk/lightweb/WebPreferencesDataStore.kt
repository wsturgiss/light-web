package lightweb

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A tool's preferences DataStore, one per tool for the life of the page (as Android's delegate gives one per app). */
fun toolDataStore(toolId: String): DataStore<Preferences> =
    dataStores.getOrPut(toolId) { CachedDataStore(webPreferencesDataStore(toolId)) }

/** Reads [toolId]'s preferences in before its first screen, so reading them later never waits. */
suspend fun warmToolDataStore(toolId: String) = (toolDataStore(toolId) as CachedDataStore).warm()

private val dataStores = HashMap<String, CachedDataStore>()

/**
 * A DataStore whose [data] is its current value, held in memory: once [warm], reading it finishes
 * without waiting, as reading a loaded file does on Android. Tools rely on that: light-amp reads
 * its settings with `runBlocking { dataStore.data.first() }` while it starts, and a page can't
 * block, so the read has to be complete before it begins (see kotlinx.coroutines.runBlocking in
 * src/compat). Writes go to the real store, and the value they return is current at once.
 */
private class CachedDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    private val current = MutableStateFlow<Preferences?>(null)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            delegate.data
                .catch { e -> Logger.withTag("DataStore").e(e) { "Couldn't read preferences; starting empty" }; emit(emptyPreferences()) }
                .collect { current.value = it }
        }
    }

    suspend fun warm() { current.filterNotNull().first() }

    override val data: Flow<Preferences> = current.filterNotNull()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        delegate.updateData(transform).also { current.value = it }
}

/**
 * A tool's preferences DataStore: the real androidx DataStore (1.3 has a wasm build), kept in
 * localStorage under `lightweb:prefs:<tool id>`. A corrupt entry starts over empty rather than
 * failing every read.
 */
fun webPreferencesDataStore(toolId: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage = WebLocalStorage(PreferencesSerializer, "lightweb:prefs:$toolId"),
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        migrations = listOf(JsonPreferencesMigration(toolId)),
    )

/**
 * Brings over what light-web's own DataStore stand-in saved before the real one replaced it:
 * JSON at `lightweb:datastore:<tool id>`, each value tagged with its type. Removed once read.
 */
private class JsonPreferencesMigration(toolId: String) : DataMigration<Preferences> {
    private val oldKey = "lightweb:datastore:$toolId"

    override suspend fun shouldMigrate(currentData: Preferences) =
        runCatching { localStorage.getItem(oldKey) }.getOrNull() != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val text = localStorage.getItem(oldKey) ?: return currentData
        val p = currentData.toMutablePreferences()
        runCatching {
            for ((name, entry) in Json.parseToJsonElement(text).jsonObject) {
                val v = entry.jsonObject["v"] ?: continue
                when (entry.jsonObject["t"]?.jsonPrimitive?.content) {
                    "s" -> p[stringPreferencesKey(name)] = v.jsonPrimitive.content
                    "b" -> p[booleanPreferencesKey(name)] = v.jsonPrimitive.boolean
                    "i" -> p[intPreferencesKey(name)] = v.jsonPrimitive.content.toInt()
                    "l" -> p[longPreferencesKey(name)] = v.jsonPrimitive.content.toLong()
                    "f" -> p[floatPreferencesKey(name)] = v.jsonPrimitive.content.toFloat()
                    "d" -> p[doublePreferencesKey(name)] = v.jsonPrimitive.content.toDouble()
                    "ss" -> p[stringSetPreferencesKey(name)] = v.jsonArray.map { it.jsonPrimitive.content }.toSet()
                }
            }
        }
        return p.toPreferences()
    }

    override suspend fun cleanUp() {
        runCatching { localStorage.removeItem(oldKey) }
    }
}
