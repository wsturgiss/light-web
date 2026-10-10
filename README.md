# light-web

Light Phone III tools, running in a web browser. Not a video of an emulator: the tool's own
Kotlin and Compose code compiled to WebAssembly, drawn in an LP3-sized screen by a small
stand-in for LightOS.

The first tool is [light-dnd](../light-dnd), compiled from its checkout **unchanged**, as is
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

In the page: click to tap, **Esc** is back, arrow keys and Page Up/Down reach a tool as
D-pad and volume keys. Below the screen:

- **mode** switches between **LP3 emulator** (text through the real on-screen LP3 keyboard;
  the computer's keys type on it too, as an external keyboard does on the phone) and
  **computer** (no on-screen keyboard; text fields take the computer's keyboard directly).
  Remembered per browser.
- **greyscale** toggles LightOS's monochrome filter.

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
  and compiles it there. Where a stand-in can't reach, `rewrites` makes a few exact text edits
  to the copy, and the build fails if one stops matching. Today: the SDK's keyboard-backed
  `LightTextInputEditor` is renamed `LightTextInputEditorLp3` (so the web one can choose by
  mode), and `LightIcons.allEntries`, which upstream builds with JVM reflection, becomes a list.
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

1. Add its pack to `packs` in `build.gradle.kts`, and its sources to `syncUpstreamSources`.
2. Register it in `src/tools/<tool>/` (a `LightTool` naming its initial screen and entry point,
   which on Android KSP finds), and list it in `src/shell/lightweb/shell/Tools.kt`.
3. Build. Compile errors are the list of what it uses that isn't here yet: add a stand-in to
   `src/compat` if it's an Android API, or to `src/tools/<tool>/JvmBuiltins*.kt` if it's a JVM
   name used without an import (`Math`, `String.format`).

## Not yet

- **Tools that need the network.** light-amp is next, and harder: browsers stop a page talking
  to most music servers (CORS, http on https), and its local stream proxy needs sockets.
- **More of the SDK client:** `LightScreen` with view models, `dataStore`, `LightWork`, audio.
  None is used by light-dnd.
- **The SDK pinned per tool.** Today every tool builds against light-dnd's vendored SDK.

## Size

Production, gzipped: about 1.3 MB of app wasm and 3.3 MB of Skia (Compose's renderer), plus
each tool's assets when it's opened (light-dnd: 6.4 MB, mostly dice art).
