# light-web

Light Phone III tools, running in a web browser. Not a video of an emulator: the tool's own
Kotlin and Compose code compiled to WebAssembly, drawn in an LP3-sized screen by a small
stand-in for LightOS.

Three tools so far, each compiled from its checkout **unchanged**: [light-dnd](../light-dnd),
[light-training](../light-training) and [light-reader](../light-reader). So is
the LP3 keyboard ([lightphone/light-keyboard](https://github.com/lightphone/light-keyboard),
checked out at `../light-keyboard`, tag `v0.0.19`).

## Running it

```bash
export JAVA_HOME=/home/will/.jdks/jbr-21.0.11
./gradlew wasmJsBrowserDevelopmentRun        # dev server with reload, http://localhost:8080
./gradlew wasmJsBrowserDistribution          # static site in build/dist/wasmJs/productionExecutable
node tool/serve.mjs                          # serves it at http://localhost:8765, with the network proxy
```

The production build is a plain folder of static files: serve it from anywhere. Served by
`tool/serve.mjs`, tools reach any site through its proxy; served anywhere else, only sites that
allow cross-origin requests. `serve.mjs` takes the folder and port to serve
(`node tool/serve.mjs build/dist/wasmJs/developmentExecutable 8767` for readable stacks), and
`--allow-host <name>` for a machine on your own network a tool should reach (see below).
`serve.mjs` is for one person on one machine: open light-web at `localhost`, as it refuses any
other host name. It needs a browser with WasmGC: Chrome/Edge 119+, Firefox 120+,
Safari 18.2+.

In the page: click to tap, drag to scroll a list or spin a number wheel (in LP3 emulator
mode; the mouse wheel does both in either mode), **Esc** is the system back
(it leaves the current screen, as on the phone), arrow keys and Page Up/Down reach a tool as
D-pad and volume keys. Below the screen:

- **mode** switches between **LP3 emulator** (text through the real on-screen LP3 keyboard;
  the computer's keys type on it too, as an external keyboard does on the phone) and
  **computer** (no on-screen keyboard; text fields take the computer's keyboard directly,
  and the mouse is a mouse). Remembered per browser.
- **greyscale** toggles LightOS's monochrome filter.

## How a tool gets to the browser

```
tools= in gradle.properties          ┐
../light-dnd/sdk/ui/src/main/kotlin  ├─ synced as-is (a few SDK UI files excluded) ─┐
../light-dnd/sdk/shared/...          ┘                                              │
src/compat   stand-ins for the android.* / java.* / androidx Android-only APIs ─────┤
src/sdk      the SDK's client side, for the web (screens, host, assets) ────────────┼─> one Kotlin/Wasm
src/shell    LightOS's part: the screen, the tool list, launching ─────────────────┤    module
src/tools    per-tool web code: the crash test tool ───────────────────────────────┘
```

Room's KSP processor runs over the lot and adds each tool's database code. All of it is
`commonMain` in Gradle, though wasm is the only target, because Kotlin won't take an `expect`
and its `actual` in one source set: the tool declares (through `toRoom3`) the `expect object`
Room needs, and KSP writes the `actual` into `wasmJsMain`.

- **JVM names used without an import** (`Math`, `"%d".format`, `Foo::class.java`,
  `Dispatchers.IO`) resolve off the JVM only if declared in the package itself, so the build
  writes `src/templates/JvmBuiltins.kt.template` into every upstream package. A name the package
  declares itself is left out. A tool needing another such name: add it to the template.
- **Upstream code isn't edited.** `build.gradle.kts` syncs it into `build/generated/lightweb/upstream`
  and compiles it there. Where a stand-in can't reach, `rewrites` makes a few exact text edits
  to the copy, and the build fails if one stops matching. Today: the SDK's keyboard-backed
  `LightTextInputEditor` is renamed `LightTextInputEditorLp3` (so the web one can choose by
  mode), and `LightIcons.allEntries`, which upstream builds with JVM reflection, becomes a list.
- **Android APIs a tool imports are provided under their Android names** (`src/compat`):
  `android.util.Log`, `android.view.KeyEvent`, `java.io.File`, `BitmapFactory`, `LocalContext`,
  `LocalConfiguration`, `painterResource(R.drawable.x)`, `Font(R.font.x)` and so on. Each does
  only as much as tools use. Where a real multiplatform library has a wasm build, the stand-in
  is built on it, or is it: DataStore Preferences is androidx DataStore 1.3 itself (alpha),
  `java.time.LocalDate` and `DateTimeFormatter` are kotlinx-datetime underneath, and
  `android.util.Log` is Kermit.
- **Resources.** Each pack's `res/` and `assets/` are served under `assets/<pack>/`, and an `R`
  class with Android's shape is generated per pack, so `R.font.mek_sans` compiles unchanged.
  Android reads assets synchronously and tools rely on it, so a tool's whole pack is fetched
  when it's opened, before its first screen.
- **Storage.** A tool's files (`java.io.File`) are bytes in IndexedDB, read into memory when the
  tool opens and written back as they change (`WebFiles`): Android reads files synchronously, and
  a book is bigger than localStorage's few MB. The SDK's `dataStore` is DataStore's own
  `WebLocalStorage`, at `lightweb:prefs:<tool id>`. Both survive a reload (per browser, per
  origin), and what earlier light-web versions saved is moved over on first open.
- **Network.** `HttpURLConnection` is a synchronous XMLHttpRequest, since tools fetch with a
  blocking call and a page has no thread to block: the page waits for the response (about half a
  second for a 700 KB book). Served by `tool/serve.mjs`, requests go through its local `/proxy`,
  so sites that don't allow cross-origin requests work, redirects and failures reach the tool as
  on the phone, and the tool's User-Agent is sent. The proxy serves only light-web's own pages
  (it wants an `X-Lightweb-Proxy` header, which other sites open in the browser can't send, and
  answers only at `localhost`), and won't fetch from private addresses (your router, NAS, other
  local servers) unless the host is named with `--allow-host`. Served any other way, requests go straight to
  the site and only work where the site allows it (Gutenberg's feeds do; its downloads and
  Standard Ebooks don't).
- **Room databases are Room 3**, Room's multiplatform line, over SQLite's own wasm build. The
  tool's DAOs, SQL and schema are its own: Room's KSP processor generates the database code from
  them, as on Android, and its exported schema (`assets/schemas`) drives auto-migrations. The
  tools are written for Room 2, so the build translates their sources (`toRoom3`: the
  `androidx.room3` package, `@Relation`'s array parameters, and the `@ConstructedBy` Room needs
  off Android). SQL runs in a web worker (`src/web/sqlite-worker.js`, androidx.sqlite's
  `WebWorkerSQLiteDriver` protocol) on files in the origin-private file system, so it works from
  static hosting with no special headers. SQLite (about 580 KB gzipped) is fetched when a tool
  first opens a database. Only `suspend` and `Flow` DAO functions work: the browser has no
  blocking SQLite. One browser tab per origin can hold the databases at a time (all light-web needs). Rows saved by earlier
  light-web versions (JSON in localStorage) are imported on first open.
- **Files replaced on the web** (excluded from the sync, rewritten in `src/sdk`):
  the SDK's `LightHapticFeedback` (`navigator.vibrate`) and `LightQrCodeScanner` (not
  supported), the keyboard's `HardwareKeyboardInput` (browser key events instead of Android
  input devices) and `Lp3KeyboardView` (an Android View; not needed).
- **Akkurat.** LP3s have it as a system font; a public page can't serve it under its licence, so
  SDK and keyboard text fall back to the default font. A licensed copy dropped into a pack's
  `res/font` as `akkuratll_regular.ttf` etc. is picked up, as the SDK does on Android.
- **The SDK client is reimplemented, not compiled** (`src/sdk/com/thelightphone/sdk`): the
  Android one is Activities and Binder IPC. `LightToolHost` takes LightActivity's place and keeps
  its back stack and lifecycle order.

## Adding a tool

1. Add the module holding its `lighttool.toml` to `tools=` in `gradle.properties`. That's the
   registration: the build syncs its sources, serves its `res/` and `assets/`, generates its `R`
   (in the package `tool.id` names, as the SDK's Gradle plugin does) and writes its `LightTool`
   (`generateToolRegistry`): id and label from `lighttool.toml`, entry point and first screen
   from its `@EntryPoint` and `@InitialScreen` classes, as KSP finds them on Android. Every tool
   builds against one SDK, a light-sdk checkout at the latest release (`lightSdk.dir`), and the
   build rejects a tool on any other version before compiling (`tool/sdk-version.mjs`).
   Record the tool's first compile in `docs/TOOL-ONBOARDING.md` before adding stand-ins. A Room
   database needs nothing more, as long as its DAO functions are all `suspend` or `Flow`.
2. Build. Compile errors are the list of what else it uses that isn't here yet; add stand-ins to
   `src/compat` under the real Android/Java names.
3. Java's platform types are the one thing a stand-in can't match: on the JVM `uri.host` is
   `String!`, usable as nullable or non-null. Stand-ins keep Java's real nullability, so a tool
   that uses one as non-null gets an exact `!!` rewrite in `rewrites` (see light-reader's).

## When a tool throws

As on Android, an uncaught exception ends the tool: the page reloads (the nearest thing to a new
process) and the home screen says "<tool> stopped" with the error. The full stack is printed to
the console again after the reload. A reload rather than a return to the home screen because an
exception while composing leaves Compose unable to draw anything again. Before the reload, the
tool's default uncaught exception handler hears of it (as on Android), and file writes on their
way to IndexedDB are waited for, so saved data survives. See `src/shell/lightweb/shell/CrashGuard.kt`.

Open the page with `?crashtest` to list a Crash tool that throws on purpose: while composing,
in a click, or in a coroutine.

## Not yet

- **A hosted build's network.** `tool/serve.mjs`'s proxy is for local use only; a public build
  would need one somewhere (a small worker, with an allowlist of sites and rate limits) or tools
  limited to sites that allow cross-origin requests.
- **light-amp:** its local stream proxy needs sockets, and music servers are often plain http.
- **More of the SDK client:** `LightWork`, audio, push, NFC, file sharing. Neither tool uses them.
- **Tools on older SDKs.** By policy they're rejected, not built (latest release only).

## Size

Production, gzipped: about 1.5 MB of app wasm and 3.3 MB of Skia (Compose's renderer), plus
each tool's assets when it's opened (light-dnd: 6.4 MB, mostly dice art; light-training: none).
