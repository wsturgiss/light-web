# light-web

Light Phone tools compiled to Kotlin/Wasm and run in a browser. Read README.md first: the
layout (upstream sources synced unchanged, Android API stand-ins in `src/compat`) is the
whole idea.

## Building

```bash
JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew wasmJsBrowserDistribution
```

System Java 26 is too new for Gradle 9.0. Incremental wasm compilation is off
(gradle.properties): with it on, Kotlin 2.3.20 crashes on rebuilds with
`ArrayIndexOutOfBoundsException` in `WasmIrFileMetadata`. If that ever reappears,
`rm -rf build/kotlin build/classes`.

## Rules

- **Never edit the upstream checkouts** (`../light-dnd`, `../light-keyboard` and others) to make something build
  here. Add a stand-in under `src/compat`, a per-tool shim under `src/tools`, or exclude the
  file in `syncUpstreamSources` and write a web version in `src/sdk`. Say why in the file.
- `build/generated/lightweb/upstream` is a copy. Don't edit it; it's overwritten every build.
- Stand-ins implement only what tools use, under the real Android/JVM names and signatures,
  so upstream code resolves to them unchanged.
- Check changes in a browser, not just the compiler: `python3 -m http.server` in
  `build/dist/wasmJs/productionExecutable`, then open the tool and use it.
