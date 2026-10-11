# light-web

Light Phone III tools, running in a web browser. Not a video of an emulator: the tool's own
Kotlin and Compose code compiled to WebAssembly, drawn in an LP3-sized screen by a small
stand-in for LightOS.

Two tools so far, each compiled from its checkout **unchanged**: [light-dnd](../light-dnd)
and [light-training](../light-training). So is
the LP3 keyboard ([lightphone/light-keyboard](https://github.com/lightphone/light-keyboard),
checked out at `../light-keyboard`, tag `v0.0.19`).

## Running it

```bash
export JAVA_HOME=/home/will/.jdks/jbr-21.0.11
./gradlew wasmJsBrowserDevelopmentRun        # dev server with reload, http://localhost:8080
./gradlew wasmJsBrowserDistribution          # static site in build/dist/wasmJs/productionExecutable
```

The production build is a plain folder of static files: serve it from anywhere (`python3 -m
http.server` will do). It needs a browser with WasmGC: Chrome/Edge 119+, Firefox 120+,
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
../light-dnd/tool/src/main/kotlin    ┐
../light-dnd/sdk/ui/src/main/kotlin  ├─ synced as-is (a few SDK UI files excluded) ─┐
../light-dnd/sdk/shared/...          ┘                                              │
src/compat   stand-ins for the android.* / java.* / androidx Android-only APIs ─────┤
src/sdk      the SDK's client side, for the web (screens, host, assets) ────────────┼─> one Kotlin/Wasm
src/shell    LightOS's part: the screen, the tool list, launching ─────────────────┤    module
src/tools    per-tool web code: light-training's database, the crash test tool ────┘
```

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
- **Storage.** `java.io.File` and the SDK's `dataStore` are backed by localStorage, so a tool's
  data survives a reload (per browser, per origin). The `dataStore` is DataStore's own
  `WebLocalStorage`, at `lightweb:prefs:<tool id>`; what light-web's earlier stand-in saved is
  migrated on first open.
- **Room databases are the one thing written per tool.** Room has no wasm build, so a tool's
  database is a hand-written implementation of its own DAO interfaces over JSON tables
  (`src/tools/training/WebTrainingDatabase.kt`), marked `@WebDatabase(TrainingDatabase::class)`. It has
  to track the tool's schema by hand. Room 3 (stable since 3.0) has a wasm build over SQLite; if more
  tools need a database, that's the way to stop writing these.
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

1. Add its checkout to `gradle.properties` and its pack to `packs` in `build.gradle.kts`, with
   `tool` set to the module holding its `lighttool.toml`. That's the registration: the build
   syncs its sources and writes its `LightTool` (`generateToolRegistry`): id and label from
   `lighttool.toml`, entry point and first screen from its `@EntryPoint` and `@InitialScreen`
   classes, as KSP finds them on Android. Every tool builds against one copy of the SDK
   (light-dnd's today), so its vendored SDK must match.
2. If it uses Room, write its database in `src/tools/<pack>/`, marked `@WebDatabase(Foo::class)`
   (see `WebTrainingDatabase.kt`).
3. Build. Compile errors are the list of what else it uses that isn't here yet; add stand-ins to
   `src/compat` under the real Android/Java names.

## When a tool throws

As on Android, an uncaught exception ends the tool: the page reloads (the nearest thing to a new
process) and the home screen says "<tool> stopped" with the error. The full stack is printed to
the console again after the reload. A reload rather than a return to the home screen because an
exception while composing leaves Compose unable to draw anything again. Saved data survives:
every store writes to localStorage synchronously. See `src/shell/lightweb/shell/CrashGuard.kt`.

Open the page with `?crashtest` to list a Crash tool that throws on purpose: while composing,
in a click, or in a coroutine.

## Not yet

- **Tools that need the network.** light-amp is next, and harder: browsers stop a page talking
  to most music servers (CORS, http on https), and its local stream proxy needs sockets.
- **More of the SDK client:** `LightWork`, audio, push, NFC, file sharing. Neither tool uses them.
- **The SDK pinned per tool.** Today every tool builds against light-dnd's vendored SDK.

## Size

Production, gzipped: about 1.5 MB of app wasm and 3.3 MB of Skia (Compose's renderer), plus
each tool's assets when it's opened (light-dnd: 6.4 MB, mostly dice art; light-training: none).
