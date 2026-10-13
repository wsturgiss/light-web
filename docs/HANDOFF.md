# light-web: spec and handoff

## Why this exists

Will builds Light Phone III tools (light-dnd, light-training, light-amp...). The goal is to run
those tools **in a web browser**, from their real source code, not a video of an emulator:

- a way to show and share tools with anyone who has a browser,
- an "LP3 emulator" that loads in a tab instead of a multi-GB Android image,
- a way to *use* a tool from a computer (bigger keyboard, no phone needed).

Two modes, switchable under the screen and remembered per browser: **LP3 emulator** (as close to
the phone as possible, including Light's on-screen keyboard) and **Computer** (text fields take
the computer's keyboard).

## Approach

Light tools are Kotlin + Jetpack Compose on the Light SDK. Kotlin compiles to WebAssembly and
Compose Multiplatform draws Compose to a `<canvas>`. What a browser lacks is Android, so light-web
**compiles tools unchanged and supplies the Android/Java APIs they call, under the same names,
built on browser features.** (Rejected: emulating Android in wasm, too slow; porting each tool to
multiplatform, means editing every tool.)

Guiding rule: **upstream repos are read-only.** Fix things with a stand-in in `src/compat`, a
per-tool shim in `src/tools/<tool>`, or by excluding an upstream file and writing a web version
in `src/sdk`. A few exact text rewrites of the *copied* sources are allowed (`rewrites` in
`build.gradle.kts`); each fails the build if it stops matching.

## Where things stand (2026-10-12)

- **Tools:** light-dnd, light-training, light-reader and light-amp all run, unchanged, from
  `main`. Source repo is public: https://github.com/wsturgiss/light-web.
- **Public demo:** https://light-web-9hi.pages.dev (Cloudflare Pages, with a relay; see
  "Hosting"). Links to one tool: `/dnd`, `/training`, `/reader`, `/amp` (or `/light-dnd`...).
- **light-amp:** Subsonic (Navidrome's demo) and Will's Plex work end to end, locally and on the
  demo: link, sync, artwork, playback. It's taken although its light-sdk submodule is between
  v0.1.1 and v0.2.0 (see SDK policy). light-amp-fork itself is untouched.
- **Running on Will's machine:** `node tool/serve.mjs build/dist/wasmJs/productionExecutable 8765
  --allow-host 192.168.200.124` (detached; restart it after changing `serve.mjs`). Its process is
  named `node-MainThread`, so find it by port (`ss -ltnp`), not `pgrep -x node`.

### Next, if wanted

- **Scan the public tools** in `docs/TOOL-CATALOG.md`: **waiting on Will**, since building them
  runs third-party build scripts on his machine.
- **Unit tests for the pure-logic stand-ins** (URI, inflate, the SAX parser, URLEncoder, `%x`),
  expected values from the JVM. Only light-reader running checks them today.
- **More stand-ins by the coverage list** (`docs/API-COVERAGE.md`): SDK permissions,
  `LightQrCodeScanner`, Ktor.
- **Network without freezing the page:** `HttpURLConnection` is a synchronous XHR (~0.5 s per
  700 KB).
- **Background audio:** leaving amp stops playback. Keeping it would mean changing how the shell
  tears a tool down. Will: not needed, but could be cool.
- **A gentler first minute for the demo's amp** (see Known issues).

## Hosting (2026-10-12)

One Cloudflare Pages project (`light-web`, account Will's) serves the production build and, at
`/proxy`, the relay (`deploy/functions/proxy.js`), a Pages Function. Same origin as the page, so
it's the same `/proxy` `tool/serve.mjs` offers locally and the app needs no change. Details and
setup in `deploy/README.md`. Deploy, **from `light-web/`**:

```bash
JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew wasmJsBrowserDistribution
node tool/stage-deploy.mjs      # build → deploy/dist: no source maps, LICENSES.txt, demo flag
cd deploy && npx wrangler pages deploy
```

- **Why Cloudflare, not AWS:** egress is free (the relay carries artwork and some audio), one
  Worker plus Pages is a one-file version of `serve.mjs`'s proxy. The free plan can't bill: past
  100,000 relay requests a day it stops until the next day. Per-visitor rate limits need a custom
  domain (rate-limiting rules don't apply to `*.pages.dev`).
- **The relay fetches only from an allowlist:** Gutenberg, Standard Ebooks, Navidrome's demo,
  lrclib, plex.tv, and **any Plex server, over HTTPS only** (2026-10-13, Will: let visitors use
  their own). A plex.direct name (`1-2-3-4.<id>.plex.direct`) resolves to whatever address is in
  it, for any id, so the name alone would make the relay an open proxy; the certificate is what
  proves a Plex server, since only Plex issues `*.<id>.plex.direct`. Checked on Cloudflare: a
  made-up name pointing at another machine over HTTPS gets 526 (bad certificate), over HTTP 403.
  Visitors' Plex tokens pass through the function (never logged). Other servers (Subsonic,
  Jellyfin) stay out: nothing similar proves what they are.
- **Who may use it:** the same `X-Lightweb-Proxy` header and `Sec-Fetch-Site` checks as locally.
  An `<audio>`/`<video>` element can't send the header, so a media request is taken on the
  browser's own `Sec-Fetch-Site: same-origin` + `Sec-Fetch-Dest: audio|video`, which a page can't
  forge. Media streams straight through with the site's status and range headers (no CPU time on
  the free plan's 10 ms budget); other answers pass a 64 MB cap.
- **Plex from home goes through the relay for audio too.** Will's network has no NAT loopback, so
  a browser inside it can't reach the server's public address, which is the one the relay
  reaches and amp settles on. `WebAudioEngine` retries a stream the browser can't load but the
  proxy can through `/proxy`. Away from home it plays directly. (On the phone amp tries local
  addresses first and the same device checks and plays, so it never meets this.)
- **Demo seeds** (`lightweb.DemoSeed`, listed in `src/tools/DemoSeeds.kt`): on the hosted demo
  only (`stage-deploy.mjs` sets `globalThis.lightwebDemo`), a tool's seed writes into its own
  preferences before it opens. amp's (`src/tools/amp/AmpDemoSeed.kt`) adds Navidrome's demo as
  its source, once, if amp has never saved any. It depends on amp's `pref.sources` format; if amp
  changes it, visitors land on the setup screen again.
- **Licences:** every repo built in is MIT; `LICENSES.txt` on the demo carries their notices.
  Akkurat is not bundled (its licence); SDK text uses the default font.

## Decisions, and why

### Shape of the project

- **SDK policy** (Will, 2026-10-11): tools build against the latest light-sdk release
  (`lightSdk.dir=../light-sdk`, a clone at the latest tag, v0.2.0). A tool on an older SDK the
  release contains is taken and compiled against the latest; one on an SDK the release doesn't
  contain (newer, forked, edited vendored files) is rejected. `checkSdkVersions`
  (`tool/sdk-version.mjs`) enforces it: a tool's version comes from its light-sdk submodule's tag
  or commit, its vendored `sdk/` matched file by file against each release, or a Maven version.
  light-herdr fails it but is unfinished and skipped.

### Behaving like the phone

- **The home screen is LightOS's tool picker:** names alphabetical and centred, Subtitle size,
  six to a page, swipe up/down (or mouse drag, or wheel) to page, dots down the right edge
  (filled for this page; SDK has only the ring icon, so both are drawn). Paging is a small
  gesture handler in `Home.kt`.
- **System back pops the screen directly**, like LightActivity's back callback.
- **In LP3 mode the mouse is a finger** (`MouseAsTouch`): while the left button is down, mouse
  pointer events on the canvas are re-sent as touch, so drags scroll. Computer mode is left
  alone.
- **Keys are heard on the page, not through Compose focus** (focus is lost whenever the focused
  element leaves the screen). Esc is system back.
- **Assets are fetched whole when a tool opens**, since Android reads assets synchronously.
- **A tool crash reloads the page**, as Android ends the process: an exception while composing
  leaves Compose dead, and `ComposeViewport` can't be remade. Errors in the shell itself don't
  reload, so it can't loop. `?crashtest` lists a tool that throws on purpose.
- **Screen size:** 360×413 dp. By default it grows in whole device pixels per dp (crisp pixel
  fonts); the **fill** toggle under the screen scales it as large as the window allows, keeping
  its shape. Remembered per browser.

### One tab at a time (2026-10-12)

SQLite's OPFS storage (sahpool) holds its files exclusively, and two live copies of a tool would
overwrite each other's preferences and files. So the tab using light-web's data holds a Web Lock
(`TabLock`); another tab says "light-web is open in another tab" with **Use here**, which asks the
active tab (BroadcastChannel) to let go. It reloads, freeing everything, and comes back saying
the same. A page waits up to 1.5 s for the lock, because a reload asks before the page it
replaces has let go. Rejected: releasing files while a tab is hidden (`pauseVfs`): it would
mean closing Room's connections behind its back, and two tabs' caches would quietly overwrite
each other.

### Storage and network

- **Files are bytes in IndexedDB** (`WebFiles`), read into memory when the tool opens and written
  through; reads stay synchronous as on Android. `CrashGuard` waits up to 2 s for pending writes
  before a reload.
- **Preferences are the real DataStore 1.3 (alpha)** on localStorage, behind `CachedDataStore`:
  reads and writes take effect in memory at once and save behind, because amp reads and writes
  them inside `runBlocking` while starting.
- **`runBlocking` runs its block inline**, and returns its result if it finished without waiting.
  If it has to wait (network, timers), it carries on in the background and returns null at once:
  amp's teardown (report "stopped", save its place) does that and throws both results away.
- **`HttpURLConnection` is a synchronous XHR**; tools block on it as on a background thread.
  Ktor's engine and OkHttp run on `WebHttp` (fetch).
- **The local proxy (`tool/serve.mjs`) is for one person on one machine:** listens on 127.0.0.1;
  `/proxy` wants `X-Lightweb-Proxy` (other origins can't send it without a preflight, never
  granted); cross-site requests and any `Host` but localhost are refused; private, loopback and
  link-local destinations are refused at connect time unless named with `--allow-host`; no CORS
  headers or cookies passed back. It returns redirects unfollowed and reports failures by kind,
  so tools' own handling runs as on the phone. Explainer:
  https://claude.ai/artifact/NpMGi9dRQN5o5vSNNinrDG
- **Platform types get exact `!!` rewrites** where a tool uses a Java `String!` as non-null.
- **Parsers are ours, in Kotlin:** inflate (after zlib's `puff`) and a non-validating SAX parser.

### Libraries

- **Prefer a real multiplatform library under a stand-in when one has a wasm build.**
- **DataStore 1.3.0-alpha11** (the only line with wasm); move to stable 1.3 when it ships.
- **kotlinx-datetime 0.7.1**, the version Compose Material 3 brings in. `DateFormat` stays on
  `Intl`.
- **Room 3** (3.0.3) with androidx.sqlite's web driver and SQLite's own wasm build (see below).

### Working method

- **Test in headless Chromium, not Will's browser.** `chromium --headless=new
  --remote-debugging-port=9333 ...`, driven over CDP; `tool/drive.mjs` (usage at its top)
  attaches to a Chromium left running. The canvas has no DOM: find tap targets from screenshots.
  At 1000×900 the screen is drawn at 2×; check a full screenshot before trusting coordinates.
  Each test browser needs its own `--user-data-dir`, and only one Chromium per profile.
- **Commit per step.** Commit messages say what changed, not "Handoff:".

## Room 3

Tools' DAOs and SQL run as on the phone: Room 3's KSP processor generates the database code from
the tool's own sources and it runs on real SQLite.

- **Room 2 → 3 translation** (`toRoom3` in `build.gradle.kts`): package `androidx.room` →
  `androidx.room3`, `@Relation` columns as arrays, and the `@ConstructedBy`/`expect object`
  constructor off Android. Unknown incompatibilities fail the build.
- **Everything is `commonMain`** (wasm is the only target; KSP writes its actuals to
  `wasmJsMain`).
- **Schemas:** `syncRoomSchemas` copies each tool's `assets/schemas` for auto-migrations.
- **`buildDatabase`** opens one database per tool and name for the page's life, file
  `<tool id>/<name>`, journal mode TRUNCATE (no WAL on OPFS).
- **The worker** (`src/web/sqlite-worker.js`) speaks `WebWorkerSQLiteDriver`'s protocol over
  sqlite-wasm with the `opfs-sahpool` VFS (no COOP/COEP headers needed, so a static host serves
  it). SQLite's files are copied beside the page and load only when a tool first opens a
  database (+579 KB gzipped).
- **DAO functions must be `suspend` or `Flow`** (no blocking SQLite in a browser).
- **Old data:** JSON tables from the pre-Room store are imported once, generically.
- **Races the old store hid** now show, as on the phone (light-training's seeding was one; fixed
  there).

## Layout

- `build.gradle.kts`: SDK version check; syncs each tool in `tools=` (`gradle.properties`), the
  SDK's `ui`/`shared` and light-keyboard; applies `rewrites`; generates `R` classes, the JVM-name
  shims (`generateJvmBuiltins`) and the tool registry (`generateToolRegistry`, from
  `lighttool.toml`, `@EntryPoint`, `@InitialScreen`); copies assets; Room 3 translation and
  SQLite.
- `src/compat`: stand-ins under real Android/JVM names.
- `src/sdk`: the SDK client rewritten for the web (`LightToolHost`, screens, `buildDatabase`,
  `Assets`, `DeviceMode`, keyboard input, `WebAudioEngine`, `DemoSeed`, preferences).
- `src/shell`: the LightOS stand-in: screen and scaling, tool picker (`Home.kt`), single-tool
  links (`Tools.kt`), `TabLock`, `CrashGuard`, `MouseAsTouch`, the bar under the screen.
- `src/tools`: per-tool shims: `amp/AmpDemoSeed.kt`, `DemoSeeds.kt`, and `crashtest`.
- `src/templates/JvmBuiltins.kt.template`: JVM names used without an import.
- `src/web`: the page (`<base href="/">`, so tool paths load from the root) and the SQLite worker.
- `deploy/`: the Cloudflare Pages project: `functions/proxy.js` (the relay), `wrangler.toml`,
  `README.md`. `deploy/dist` is staged by `tool/stage-deploy.mjs` and git-ignored.
- `tool/serve.mjs`: local server and proxy (serves the page for extensionless paths).
  `tool/drive.mjs`: headless Chromium driver. `tool/sdk-version.mjs`, `tool/unresolved-names.mjs`,
  `tool/api-scan/` (the API scanner; usage at its top).
- `docs/API-COVERAGE.md` (scanner output: the stand-ins to write), `docs/TOOL-ONBOARDING.md` (what
  each tool needed), `docs/TOOL-CATALOG.md` (known Light tools).

## Build and run locally

```bash
cd ~/Projects/light-web
JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew wasmJsBrowserDistribution
node tool/serve.mjs build/dist/wasmJs/productionExecutable 8765 --allow-host 192.168.200.124
```

Full rebuild ≈ 1.5 min. About 6 MB gzipped (2.4 MB app wasm with amp, 3.3 MB Skia) plus each
tool's assets, plus 0.58 MB of SQLite when a tool opens a database.

## Known issues and gotchas

- **amp on the demo pauses during its first minute.** Its first sync keeps Navidrome's shared
  demo server busy; amp's 6 s reachability check times out, it turns streaming off, then back on
  once the check answers. Steady afterwards. Same on a phone against that server.
- **Plex advertises Docker bridge addresses** (172.17/18.x) that both proxies rightly refuse.
- Kotlin 2.3.20's wasm compiler intermittently crashes reading its own previous output;
  `compileKotlinWasmJs` deletes it first. If it recurs: `rm -rf build/kotlin build/classes`.
- A Kotlin name clash in generated code can surface as an internal compiler error
  (`FirTypeAliasSymbol cannot be cast to FirClassSymbol`).
- A change only inside a `js("...")` body once didn't reach `build/dist`; the optimizer task now
  takes the compiled output as input. If a change seems not to ship, grep `build/dist/.../lightweb.js`.
- A new npm dependency stops the build until `./gradlew kotlinWasmUpgradeYarnLock` is run and the
  lock committed.
- Production stacks have no function names; build `wasmJsBrowserDevelopmentExecutableDistribution`
  for readable ones.
- Each port (and incognito vs. normal windows) is its own origin with its own storage.

## The API scanner

Reads a tool's Android build (its own Gradle; writes only to its git-ignored `build/`) and lists
every reference light-web doesn't provide, at member level, before light-web compiles anything.
It filters what the compiler writes rather than the tool (`java.lang` intrinsics, bridges,
stdlib `@PublishedApi` helpers, synthetic members, name mangling, Room's `*_Impl`). Known misses:
Kotlin-only annotations, overloads that resolve differently on the web, Java platform types.
Not built: the same diff as a lint rule for tool authors.

## Found in tools (not light-web's to fix)

- **light-training: the session list keeps the old distance unit** after it's changed in
  Settings, until the tool reopens. `HomeViewModel` reads `DISTANCE_UNIT` once in `init`. Same on
  the phone. Fix: collect it as a flow, or re-read it in `onScreenShow`.
- **light-training: system back leaves a whole screen from its inner modes** (Manage exercises,
  Add set, confirmations, Settings' pages). light-web matches the phone. Fix belongs in
  light-training: a `BackHandler` per screen.
- **light-dnd's home screen overflows** at 360×413 (the Roll/Sheet/Bag menu sits below the edge).
  Not yet checked whether the phone looks the same.

## Open questions for Will

- **Catalog scan:** may light-web clone and build the public tools in `TOOL-CATALOG.md`? All,
  some, or none?
- **Rate limits:** with any Plex server allowed, the relay is open to more traffic. Per-visitor
  limits need a custom domain on Cloudflare.
