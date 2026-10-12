package lightweb.tools

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import lightweb.DemoSeed

/**
 * amp on the hosted demo: Navidrome's public demo server as its music source, so the library
 * fills on first open. amp keeps its sources as one JSON list under `pref.sources`, password
 * included, and the one it shows under `pref.activeSource` (com.sublunar.amp.data.AppSettings).
 * These are the server's published demo credentials. Written only if amp has never saved any
 * sources.
 */
val AmpDemoSeed = DemoSeed("com.sublunar.amp") { prefs ->
    val sources = stringPreferencesKey("pref.sources")
    val active = stringPreferencesKey("pref.activeSource")
    prefs.edit { p ->
        if (p[sources] != null) return@edit
        p[sources] = """[{"id":"demo-navidrome","kind":"SUBSONIC","name":"Navidrome demo",""" +
            """"baseUrl":"https://demo.navidrome.org","username":"demo","password":"demo"}]"""
        p[active] = "demo-navidrome"
    }
}
