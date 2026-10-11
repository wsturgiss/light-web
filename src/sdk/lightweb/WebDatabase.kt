package lightweb

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.Worker
import org.w3c.dom.WorkerOptions
import org.w3c.dom.MODULE
import org.w3c.dom.WorkerType
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.SAME_ORIGIN

/**
 * A tool's Room database, built once per tool and name for the life of the page, as Android's
 * process would keep it. The file is `<tool id>/<name>` in the SQLite worker's storage
 * (src/web/sqlite-worker.js: OPFS, one store per origin).
 */
fun <T : RoomDatabase> openWebDatabase(toolId: String, name: String, builder: (fileName: String) -> RoomDatabase.Builder<T>): T {
    val fileName = "$toolId/$name"
    @Suppress("UNCHECKED_CAST")
    return openDatabases.getOrPut(fileName) {
        builder(fileName)
            .setDriver(driver)
            // WAL needs shared memory, which SQLite's OPFS storage doesn't have.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addCallback(LocalStorageImport("lightweb:room:$fileName"))
            .build()
    } as T
}

private val openDatabases = HashMap<String, RoomDatabase>()

/** One worker, so one SQLite, for every database on the page. Started when the first one opens. */
private val driver by lazy { WebWorkerSQLiteDriver(Worker("sqlite-worker.js", WorkerOptions(type = WorkerType.MODULE, credentials = RequestCredentials.SAME_ORIGIN))) }

/**
 * Brings over what light-web saved before it had Room: a tool's tables as JSON in localStorage
 * under `lightweb:room:<tool id>/<name>`, `{"<table>": [{"<column>": value, ...}, ...], ...}`
 * with the tool's real table and column names (other top-level keys were bookkeeping). The rows
 * go in as they are, ids included, in one transaction; the key is removed once it commits. A key
 * that can't be read is logged and left alone, never dropped.
 */
private class LocalStorageImport(private val key: String) : RoomDatabase.Callback() {
    override suspend fun onOpen(connection: SQLiteConnection) {
        val text = runCatching { localStorage.getItem(key) }.getOrNull() ?: return
        val tables = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse {
            log.e(it) { "$key isn't the JSON light-web wrote; leaving it in place" }
            return
        }
        val known = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { s ->
            buildSet { while (s.step()) add(s.getText(0)) }
        }
        var count = 0
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            for ((table, rows) in tables) {
                if (rows !is JsonArray) continue
                check(table in known) { "$key has a table `$table` the database doesn't" }
                for (row in rows) {
                    val columns = row.jsonObject.entries.toList()
                    val sql = "INSERT INTO `$table` (${columns.joinToString { "`${it.key}`" }}) VALUES (${columns.joinToString { "?" }})"
                    connection.prepare(sql).use { s ->
                        columns.forEachIndexed { i, (_, v) ->
                            val p = v as? JsonPrimitive
                            when {
                                p == null || p is JsonNull -> s.bindNull(i + 1)
                                p.isString -> s.bindText(i + 1, p.content)
                                p.content.toLongOrNull() != null -> s.bindLong(i + 1, p.content.toLong())
                                else -> s.bindDouble(i + 1, p.content.toDouble())
                            }
                        }
                        s.step()
                    }
                    count++
                }
            }
            connection.execSQL("END TRANSACTION")
        } catch (e: Throwable) {
            connection.execSQL("ROLLBACK TRANSACTION")
            throw e
        }
        runCatching { localStorage.removeItem(key) }
        log.i { "Imported $count rows from $key" }
    }
}

private val log = Logger.withTag("WebDatabase")
