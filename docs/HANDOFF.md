# light-web: spec and handoff

## Why this exists

Will builds Light Phone III tools (light-dnd, light-training, light-amp...). Today the only way to
see one running is on the phone or in an Android emulator. The goal is to run those tools **in a
web browser**, from their real source code, not by streaming video of an emulator. That gives:

- a way to show and share tools with anyone who has a browser,
- an "LP3 emulator" that loads in a tab instead of a multi-GB Android image,
- a way to *use* a tool from a computer (bigger keyboard, no phone needed).

Two modes, switchable under the screen and remembered per browser:

- **LP3 emulator**: as close to the phone as possible, including Light's on-screen keyboard.
- **Computer**: same tool, but text fields take the computer's keyboard directly.

## Approach, and why this one

Light tools are Kotlin + Jetpack Compose on top of the Light SDK. Kotlin compiles to
WebAssembly, and JetBrains' Compose Multiplatform runs Compose in a browser (drawn to a
`<canvas>` with Skia). What doesn't exist in a browser is Android: the tools call Android and
Java APIs (files, logging, fonts, databases, dates).

Options considered:

1. **Emulate Android in wasm** (QEMU compiled to wasm). Possible in principle, unusably slow
   and huge. Rejected.
2. **Port each tool to multiplatform libraries.** The "proper" route, but means editing every
   tool, and maintaining two versions until Light's SDK goes multiplatform. Deferred.
3. **Compile tools unchanged, and supply the Android/Java APIs they call under the same names,
   built on browser features** (polyfills for Android). **Chosen.** Tools stay the source of
   truth; light-web never edits them.

Guiding rule: **upstream repos are read-only.** Fix things with a stand-in in `src/compat`, a
per-tool shim in `src/tools/<tool>`, or by excluding an upstream file and writing a web version
in `src/sdk`. A few exact text rewrites of the *copied* sources are allowed (`rewrites` in
`build.gradle.kts`); each fails the build if it stops matching.

## Current state (commits on `main`)

| Commit | What |
|---|---|
| `a276a96` | light-dnd runs in the browser. Shell, tool host, Android stand-ins, asset loading. |
| `f78a3a2` | Light's real LP3 keyboard (lightphone/light-keyboard v0.0.19), compiled unchanged. Mode switch. |
| `d00f511` | light-training runs. Room/DataStore/java.time/UUID/SHA-256 stand-ins; LightScreen + view models; hand-written database. Esc and back fixes. |
| (next) | Crash isolation: a tool's uncaught exception reloads the page to the home screen with "<tool> stopped". `?crashtest` lists a tool that throws on purpose. |

Verified by hand in Chrome (no automated tests): dnd home, dice roll, rules search, save
survives reload; LP3 keyboard tapping + computer keys + Backspace/Return; computer mode;
training: start workout, add exercise, log a set, reload, settings unit toggle, backup export
file written. **Not verified:** training cardio/timer, editing/copying/deleting sessions,
muscle-group/exercise editors.

## Layout

- `build.gradle.kts`: syncs sources from `../light-dnd` (tool + its vendored SDK `ui`/`shared`),
  `../light-training/tool`, `../light-keyboard/ui`; applies `rewrites`; generates an Android-style
  `R` class per resource pack and a resource table; copies assets to `assets/<pack>/`.
- `src/compat`: stand-ins under real names (`android.util.Log`, `java.io.File` on localStorage,
  `java.time.*`, `androidx.room` annotations, DataStore Preferences, `painterResource(R.drawable)`,
  `Font(R.font)`, `SystemFonts`, `Resources.getIdentifier`, ...).
- `src/sdk`: the SDK client rewritten for the web: `LightToolHost` (replaces LightActivity:
  back stack + lifecycle), `SimpleLightScreen`/`LightScreen`, `SealedLightContext`,
  `buildDatabase`, `Assets` loader, `DeviceMode`, web keyboard input, mode-switching
  `LightTextInputEditor`.
- `src/shell`: LightOS stand-in: LP3-sized screen (360×413 dp, whole-pixel scaling), tool list,
  greyscale toggle, page-level key listener (Esc = system back).
- `src/tools/<tool>`: registration (`LightTool`), per-package JVM-name shims, and for
  light-training the hand-written `WebTrainingDatabase`.

## Build and run

```bash
cd ~/Projects/light-web
JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew wasmJsBrowserDistribution
cd build/dist/wasmJs/productionExecutable && python3 -m http.server 8765
```

Full rebuild ≈ 1 min. Output ≈ 4.7 MB gzipped plus each tool's assets.

## Decisions to keep, and why

- **One SDK copy for all tools** (light-dnd's vendored v0.2.0). light-training's is identical.
  A tool on a different SDK version needs a decision first.
- **System back pops the screen directly**, like LightActivity's back callback. A view model's
  `onBackPressed` only applies when the tool calls `goBack()` itself.
- **Keys are heard on the page, not through Compose focus.** Focus is lost whenever the focused
  element leaves the screen.
- **Assets are fetched whole when a tool opens.** Android reads assets synchronously.
- **Akkurat is not bundled** (licence). SDK text uses the default font. A licensed copy in a
  pack's `res/font` is picked up automatically.
- **A tool crash reloads the page** rather than returning to the home screen in place: an
  exception while composing leaves Compose dead (measured: nothing redraws, Esc ignored), and
  `ComposeViewport` can't be torn down and remade. Exceptions in clicks and coroutines leave the
  page alive but the tool half-done, so they're treated the same, as Android would.
- **Room databases are hand-written per tool for now.** Will chose to let a general solution
  "earn its way in" (see next steps).

## Known issues and gotchas

- Kotlin 2.3.20's wasm compiler intermittently crashes reading its own previous output.
  `compileKotlinWasmJs` deletes that output first, and incremental compilation is off. If it
  recurs: `rm -rf build/kotlin build/classes`.
- A crash reloads the whole page, so a tool's in-memory state (and any other open tool's) goes
  with it, as a process death would on Android. Saved data survives.
- `WebTrainingDatabase` mirrors light-training's schema v11 by hand and won't notice changes.
- Browser automation in this setup may attach to the wrong browser profile; check before
  driving it. The Chrome extension's tab is also often in the background (`hidden`), where
  nothing renders and screenshots time out. Headless Chromium over CDP (`chromium --headless=new
  --remote-debugging-port=...`, driven from Node's built-in WebSocket) works without touching
  Will's browser.

## Next steps, in order

1. ~~Crash isolation~~: done. A tool's exception reloads the page to the home screen.
2. **Generate per-package JVM shims** (`Math`, `String.format`, `::class.java`, `Dispatchers.IO`)
   in the build instead of copying files per package.
3. **Generate tool registration** from `@InitialScreen` / `@EntryPoint`.
4. **Build stand-ins on real multiplatform libraries** where possible: DataStore 1.3 (alpha) has
   a wasm build with Android's own package, so it can replace our DataStore stand-in outright;
   kotlinx-datetime under `java.time`; Kermit under `Log`.
5. **Room 3 (alpha, has wasm + SQLite)** when a second tool needs a database: removes the
   hand-written database code.
6. **light-amp**: much harder. Browsers block most music servers (CORS, http from https) and its
   local stream proxy needs raw sockets. Realistically a demo library or a proxy server.
7. Longer term: if Light's SDK moved to multiplatform libraries, tools would build for web
   without most of `src/compat`.

## Open questions for Will

- GitHub remote for light-web (not created yet).
- Whether and where to host a public build (Akkurat stays out either way).
