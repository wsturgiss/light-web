// The web worker behind androidx.sqlite's WebWorkerSQLiteDriver: Room's SQL runs here, in
// SQLite's own wasm build, on files kept in the origin-private file system (OPFS).
//
// The driver's message protocol (documented on WebWorkerSQLiteDriver in androidx.sqlite's
// sqlite-web) is four commands. Requests are {id, data: {cmd, ...}}; replies are {id, data} or
// {id, error}, where an SQLite error keeps sqlite-wasm's "<NAME>: sqlite3 result code <n>: <msg>"
// text, which the driver turns into an SQLiteException.
//
//   open    {fileName}                     -> {databaseId}
//   prepare {databaseId, sql}              -> {statementId, parameterCount, columnNames}
//   step    {statementId, bindings: [...]} -> {rows: [[...]], columnTypes: [...]}
//   close   {statementId | databaseId}     (no reply)
//
// Storage is the opfs-sahpool VFS: unlike SQLite's plain "opfs" VFS it needs no
// cross-origin-isolation headers, so a static host can serve it. It holds the files exclusively,
// so a second tab of the same origin can't open them while the first has them.

import sqlite3InitModule from "./sqlite/index.mjs";

// SQLite's type codes, as androidx.sqlite's SQLITE_DATA_* constants.
const SQLITE_NULL = 5;

const ready = (async () => {
    const sqlite3 = await sqlite3InitModule();
    try {
        const pool = await sqlite3.installOpfsSAHPoolVfs({ name: "lightweb" });
        return { sqlite3, pool };
    } catch (e) {
        throw new Error(`Can't open light-web's databases; is light-web open in another tab? (${e?.message ?? e})`);
    }
})();

const databases = new Map();
const statements = new Map();
let nextId = 1;

async function handle(data) {
    const { sqlite3, pool } = await ready;
    switch (data.cmd) {
        case "open": {
            const id = nextId++;
            databases.set(id, new pool.OpfsSAHPoolDb("/" + data.fileName));
            return { databaseId: id };
        }
        case "prepare": {
            const db = databases.get(data.databaseId);
            if (!db) throw new Error(`No open database ${data.databaseId}`);
            const stmt = db.prepare(data.sql);
            const id = nextId++;
            statements.set(id, stmt);
            // Not stmt.getColumnNames(): it throws for a statement with no columns (CREATE, INSERT).
            const columnNames = Array.from({ length: stmt.columnCount }, (_, i) => sqlite3.capi.sqlite3_column_name(stmt.pointer, i));
            return { statementId: id, parameterCount: stmt.parameterCount, columnNames };
        }
        case "step": {
            const stmt = statements.get(data.statementId);
            if (!stmt) throw new Error(`No prepared statement ${data.statementId}`);
            // Each step runs the statement from the start: the driver steps once and pages
            // through the rows it gets back.
            stmt.reset(true);
            // The driver sets bindings by index, so unset ones are holes: SQL NULL, as on Android.
            const bindings = Array.from(data.bindings ?? [], (v) => (v === undefined ? null : v));
            if (bindings.length > 0) stmt.bind(bindings);
            const rows = [];
            const columnCount = stmt.columnCount;
            const columnTypes = new Array(columnCount).fill(SQLITE_NULL);
            try {
                while (stmt.step()) {
                    const row = stmt.get([]);
                    for (let i = 0; i < columnCount; i++) {
                        // The driver reads integers as JS numbers.
                        if (typeof row[i] === "bigint") row[i] = Number(row[i]);
                        // Each column's type is its first non-null value's.
                        if (columnTypes[i] === SQLITE_NULL) columnTypes[i] = sqlite3.capi.sqlite3_column_type(stmt.pointer, i);
                    }
                    rows.push(row);
                }
            } finally {
                stmt.reset();
            }
            return { rows, columnTypes };
        }
        case "close": {
            if (data.statementId != null) {
                statements.get(data.statementId)?.finalize();
                statements.delete(data.statementId);
            }
            if (data.databaseId != null) {
                databases.get(data.databaseId)?.close();
                databases.delete(data.databaseId);
            }
            return undefined;
        }
        default:
            throw new Error(`Unknown command ${data.cmd}`);
    }
}

// Messages are handled one at a time, in the order they came: each waits for SQLite to load,
// and a later one mustn't overtake an earlier one once it has.
let queue = Promise.resolve();
self.onmessage = (event) => {
    const { id, data } = event.data;
    queue = queue.then(async () => {
        try {
            const result = await handle(data);
            if (data.cmd !== "close") self.postMessage({ id, data: result });
        } catch (e) {
            if (data.cmd !== "close") self.postMessage({ id, error: String(e?.message ?? e) });
            else console.error("sqlite-worker: close failed", e);
        }
    });
};
