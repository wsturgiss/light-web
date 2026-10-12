package lightweb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * What a tool starts with on the hosted demo, so it doesn't open empty: given the tool's own
 * preferences, [seed] writes what it needs, in the tool's own format. Seeds live with the tools'
 * shims (src/tools) and are listed in lightweb.tools.demoSeeds.
 *
 * The demo's page is marked by tool/stage-deploy.mjs; a local build never is, so a seed never
 * touches a real setup. A seed should write only what the tool has never saved, so that removing
 * what was seeded keeps it removed.
 */
class DemoSeed(val toolId: String, val seed: suspend (DataStore<Preferences>) -> Unit)

/** Runs [toolId]'s demo seed, if the page is the hosted demo and the tool has one. */
suspend fun seedForDemo(toolId: String, seeds: List<DemoSeed>) {
    if (!isDemo()) return
    seeds.filter { it.toolId == toolId }.forEach { it.seed(toolDataStore(toolId)) }
}

private fun isDemo(): Boolean = js("globalThis.lightwebDemo === true")
