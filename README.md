# light-web

Light Phone III tools, running in a web browser. Not a video of an emulator: the tool's own
Kotlin and Compose code compiled to WebAssembly, drawn in an LP3-sized screen by a small
stand-in for LightOS.

The first tool is [light-dnd](../light-dnd), compiled from its checkout **unchanged**.

## Running it

```bash
export JAVA_HOME=/home/will/.jdks/jbr-21.0.11
./gradlew wasmJsBrowserDevelopmentRun        # dev server with reload, http://localhost:8080
./gradlew wasmJsBrowserDistribution          # static site in build/dist/wasmJs/productionExecutable
```

The production build is a plain folder of static files: serve it from anywhere (`python3 -m
http.server` will do). It needs a browser with WasmGC: Chrome/Edge 119+, Firefox 120+,
Safari 18.2+.

In the page: click to tap, **Esc** is back, arrow keys and Page Up/Down reach a tool as
D-pad and volume keys. Text fields take the computer's (or a phone's own) keyboard. Below the
screen, `greyscale` toggles LightOS's monochrome filter.

## How a tool gets to the browser

```
../light-dnd/tool/src/main/kotlin    ┐
../light-dnd/sdk/ui/src/main/kotlin  ├─ synced as-is (a few SDK UI files excluded) ─┐
../light-dnd/sdk/shared/...          ┘                                              │
src/compat   stand-ins for the android.* / java.* / androidx Android-only APIs ─────┤
src/sdk      the SDK's client side, for the web (screens, host, assets) ────────────┼─> one Kotlin/Wasm
src/shell    LightOS's part: the screen, the tool list, launching ─────────────────┤    module
src/tools    per-tool registration and JVM-name shims ─────────────────────────────┘
```

- **Upstream code isn't edited.** `build.gradle.kts` syncs it into `build/generated/lightweb/upstream`
  and compiles it there. The only rewrite is `LightIcons.allEntries`, which upstream builds with
  JVM reflection; the sync lists the icons instead.
- **Android APIs a tool imports are provided under their Android names** (`src/compat`):
  `android.util.Log`, `android.view.KeyEvent`, `java.io.File`, `BitmapFactory`, `LocalContext`,
  `LocalConfiguration`, `painterResource(R.drawable.x)`, `Font(R.font.x)` and so on. Each does
  only as much as tools use.
- **Resources.** Each pack's `res/` and `assets/` are served under `assets/<pack>/`, and an `R`
  class with Android's shape is generated per pack, so `R.font.mek_sans` compiles unchanged.
  Android reads assets synchronously and tools rely on it, so a tool's whole pack is fetched
  when it's opened, before its first screen.
- **Files.** `java.io.File` is backed by localStorage, so a tool's save file survives a reload
  (per browser, per origin).
- **SDK files replaced on the web** (excluded from the sync, rewritten in `src/sdk`):
  `LightFont` (Akkurat can't be served publicly, so SDK text is the browser's sans),
  `LightHapticFeedback` (`navigator.vibrate`), `LightTextInputEditor` (the LP3 keyboard library
  is Android-only for now), `LightQrCodeScanner` (not supported).
- **The SDK client is reimplemented, not compiled** (`src/sdk/com/thelightphone/sdk`): the
  Android one is Activities and Binder IPC. `LightToolHost` takes LightActivity's place and keeps
  its back stack and lifecycle order.

## Adding a tool

1. Add its pack to `packs` in `build.gradle.kts`, and its sources to `syncUpstreamSources`.
2. Register it in `src/tools/<tool>/` (a `LightTool` naming its initial screen and entry point,
   which on Android KSP finds), and list it in `src/shell/lightweb/shell/Tools.kt`.
3. Build. Compile errors are the list of what it uses that isn't here yet: add a stand-in to
   `src/compat` if it's an Android API, or to `src/tools/<tool>/JvmBuiltins*.kt` if it's a JVM
   name used without an import (`Math`, `String.format`).

## Not yet

- **The LP3 keyboard.** [lightphone/light-keyboard](https://github.com/lightphone/light-keyboard)
  is public Compose; compiling it for wasm would put the real keyboard on screen.
- **Tools that need the network.** light-amp is next, and harder: browsers stop a page talking
  to most music servers (CORS, http on https), and its local stream proxy needs sockets.
- **More of the SDK client:** `LightScreen` with view models, `dataStore`, `LightWork`, audio.
  None is used by light-dnd.
- **The SDK pinned per tool.** Today every tool builds against light-dnd's vendored SDK.

## Size

Production, gzipped: about 1.3 MB of app wasm and 3.3 MB of Skia (Compose's renderer), plus
each tool's assets when it's opened (light-dnd: 6.4 MB, mostly dice art).
