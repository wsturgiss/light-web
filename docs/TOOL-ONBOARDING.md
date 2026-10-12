# Tool onboarding log

What each tool needed from light-web when it was added, so we can see whether adding a tool gets
cheaper. The useful number is **distinct missing names**, not compile errors: one missing class
makes an error at every line that uses it (light-reader: 310 errors, 48 names).

## The routine, for every new tool

Before writing any stand-in:

1. `node tool/sdk-version.mjs <tool module>`: it must be on the latest light-sdk release, or
   light-web doesn't take it (see the handoff). Record a rejection here and stop building (the
   scanner can still run, for planning).
2. `node tool/api-scan/scan.mjs <tool module>`: the scanner's prediction, from the tool's
   Android build. Record its count and names.
3. Add the module to `tools=` in `gradle.properties` and compile, keeping the log:
   `./gradlew compileKotlinWasmJs > first-compile.log 2>&1`.
4. `node tool/unresolved-names.mjs first-compile.log <pack>` and record its counts and names
   below, with which were new and which earlier tools had already brought in, and what the
   scanner missed or flagged wrongly.
5. Afterwards, add what only running the tool found (compile-clean gaps) and anything that needed
   a decision rather than code.

How to read the names: "members of a value" are calls on something whose type the log doesn't
give, almost always a missing class's members (`connection.connect()`), so they follow from a
class already listed. A first compile doesn't show everything: errors that only appear once the
first ones are fixed, and gaps that compile but behave wrongly, show up later (see each tool's
"after the first compile").

## light-dnd (approximate, reconstructed from `a276a96`)

- **Date:** 2026-10-10 (first tool). **SDK:** v0.2.0, vendored (`sdk/`, minus a README).
- **First compile:** not kept. light-web was built around it, so everything was new.
- **What it brought in** (from the commit's files): `android.content.Context`,
  `android.content.res.Configuration`, `android.graphics.BitmapFactory`, `android.util.Log`,
  `android.view.KeyEvent`, `java.io.File` (text in localStorage), `java.text.DateFormat`,
  `java.util.Date`; JVM names `Math`, `System`, `"…".format`, `Foo::class.java`; the whole web
  SDK client (`LightScreen`, `LightToolHost`, `EntryPoint`, fonts, assets).
- **With the LP3 keyboard** (`f78a3a2`): `android.content.res.Resources`,
  `android.graphics.fonts.SystemFonts`, `android.os.SystemClock`, `androidx.annotation`,
  `java.util.Locale`, code points.
- **Decisions:** compile tools unchanged with stand-ins under the real names; one SDK copy for
  all tools; Akkurat not bundled.

## light-training (approximate, reconstructed from `d00f511`)

- **Date:** 2026-10-10. **SDK:** v0.2.0, vendored (`sdk/`, exact).
- **First compile:** not kept.
- **New** (from the commit's files): `androidx.room` (annotations, `RoomDatabase`) and a
  hand-written database (`WebTrainingDatabase`), DataStore Preferences, `java.security.MessageDigest`
  (SHA-256), `java.time.Instant`, `LocalDate`, `DateTimeFormatter`, `java.util.UUID`; File grew;
  SDK view models and `buildDatabase`.
- **Already covered by light-dnd:** `Log`, `File`, `Date`, `DateFormat`, `Locale`, the SDK screens.
- **Decisions:** Room databases hand-written per tool, for now (Room had no wasm build then; Room 3
  does now, and Will has said go ahead).
- **Later (2026-10-11, Room 3):** the hand-written database went; the tool's own DAOs run on Room 3
  over SQLite. Needed: the Room 2 → 3 source translation (`toRoom3`: package, `@Relation`'s
  `parentColumns`/`entityColumns`, `@ConstructedBy`). It also exposed a race in the tool's
  `ensureSeeded` that the JSON tables hid, fixed in light-training (see the handoff). Room 3's exported schema matched the tool's `11.json`.

## light-reader

- **Date:** 2026-10-11. **SDK:** v0.2.0, submodule (`light-sdk`, exact).
- **First compile:** 310 errors: 202 unresolved references, 101 follow-on errors, 7 others.
  **48 distinct names**, plus 36 members called on values of missing types. All 48 were new:
  nothing an earlier tool needed was missing. (The script counts 49: it also lists
  `kotlinx.coroutines.flow.map`, which failed only because the Flow's element type,
  `NetworkStatus`, was missing.)
- **Already covered by earlier tools** (imported, compiled first time): `android.util.Log`,
  `android.view.KeyEvent`, `java.io.File`, `InputStream`, `IOException`,
  `java.security.MessageDigest`, `java.util.Date`, `java.util.Locale`,
  `java.util.concurrent.ConcurrentHashMap`, the SDK's screens and view models. (`File` still
  needed new members: `nameWithoutExtension`, `appendText`, `canonicalPath`, `createTempFile`,
  `listFiles(filter)`, `getUsableSpace`, `readText(charset)`.)

The 48, by area (all new stand-ins):

| Area | Names |
|---|---|
| `java.io` (5) | `ByteArrayOutputStream`, `Closeable`, `FileOutputStream`, `FilterInputStream`, `StringReader` |
| `java.net` (7) | `URI`, `URISyntaxException`, `URL`, `URLEncoder`, `HttpURLConnection`, `ConnectException`, `UnknownHostException` |
| `java.util.zip` (3) | `ZipFile`, `ZipEntry`, `ZipException` |
| `org.xml.sax`, `javax.xml.parsers` (8) | `Attributes`, `ContentHandler`, `EntityResolver`, `InputSource`, `SAXException`, `helpers.AttributesImpl`, `helpers.DefaultHandler`, `SAXParserFactory` |
| `java.security.cert`, `javax.net.ssl` (5) | `CertificateException`, `CertPathValidatorException`, `CertPathBuilderException`, `SSLException`, `SSLPeerUnverifiedException` |
| `java.text`, `java.util` (2) | `SimpleDateFormat`, `Arrays` |
| Light SDK (2) | `LightConnectivity`, `NetworkStatus` (and `lightContext.connectivity`) |
| JVM names, no import (16) | `Charsets`, `Thread`, `JvmInline`, `String.format(Locale, …)`, `putIfAbsent`, `computeIfAbsent`, `replaceAll`; statics `File.createTempFile`, `InputStream.nullInputStream`, `Log.getStackTraceString`; members reached through an implicit `this` (`getEntry`, `getInputStream`, `entry`, `disconnect`, `currentStatus`, `getUsableSpace`, `isNamespaceAware`, `newSAXParser`) |

Also from the 7 other errors: `String(CharArray, …)` and `String(ByteArray, …, Charset)`
(deprecated as errors off the JVM), `listFiles(FileFilter)`, `readText(Charset)`.

**The scanner's prediction** (run afterwards, against light-web at `ab472d2`, before these
stand-ins; it didn't exist yet): 59 names. Of the compile's 48 it found **45**. It missed three:
`@JvmInline` (a Kotlin annotation, not in bytecode as a reference), `String.format(Locale, …)`
(the template already had `format`; only the overload was wrong, which no compile showed either)
and `java.util.Arrays` (ignored on purpose: Kotlin's own `copyOf` compiles to it). Of its other
14, 13 are things the stand-ins really needed that the compile reported only as members or not
at all (`FileDescriptor`, `XMLReader`, `SAXParser`, `Charset`, `URLConnection`, `Reader`,
`nameWithoutExtension`…); one is a false positive (`FileInputStream`, from an inlined
`inputStream()`). It also missed `readText(Charset)`, an overload of a member that existed. Against
light-web now it reports 0.

**After the first compile:**
- Second compile, 9 errors: Java **platform types**. light-reader uses `uri.host`,
  `uri.scheme` and `attrs.getValue(name)` as non-null in one place and nullable in another, which
  `String!` allows and a Kotlin stand-in can't. Three exact `!!` rewrites; `Attributes.getValue(int)`
  became non-null. **Expect this for every tool that leans on Java APIs.**
- Running it: `"%.1f".format(Locale.ROOT, x)` **compiled against the wrong overload** (the
  Locale became the first format argument) and failed at runtime. Gaps like this don't show in
  any compile log.
- Bugs in the new stand-ins, found by running: `URI` fields shifted by one (my regex use), `%x`
  of a negative Byte (an old bug, exposed here).

**Decisions rather than code:**
- Files in IndexedDB instead of localStorage (books are binary and bigger than localStorage).
- Synchronous network (the page freezes while a book downloads: ~0.5 s for 700 KB).
- CORS: Standard Ebooks and Gutenberg's downloads refuse cross-origin requests, so the dev
  server proxies; how a hosted build reaches the network is still open.
- SDK policy (Will): only tools on the latest light-sdk release. (Widened 2026-10-11: any SDK the
  latest release contains, compiled against the latest.)

## light-amp (light-amp-fork, in progress)

- **Date:** 2026-10-11. **SDK:** v0.1.1-12-g15b1ca1, submodule, plus 11 patches of amp's own to
  the SDK's client (`light-sdk-patch/`), applied in the submodule's working tree. Taken since the
  SDK policy changed (Will, 2026-10-11): a tool whose SDK the latest release contains is compiled
  against that release. The patches all touch `sdk/client`, which light-web reimplements, so
  they become light-web's to provide (audio queue ops, `LightAudioNetworkPolicy`,
  `hasRuntimePermission`, `LightDisplayColor`, `DlnaCast`, `LightTransferService`...).
- **Size:** 97 Kotlin files, ~34,000 lines; on the branch `playlist-membership-cache`.
- **Scan (before):** 107 names (see `API-COVERAGE.md`).
- **First compile:** 1111 errors: 830 unresolved references (144 distinct names, plus 94 members
  of values whose type is missing), 231 follow-on, 50 others. By area:
  - SDK client: audio (12 names), cast (3), `LightWork`/`LightJob*`, `LightDisplayColor`,
    `LightTransferService`, `LightQrCodeScanner`, permissions, `refreshKeyboardOptions`.
  - Ktor (client, http, the OkHttp engine) and okhttp3 itself (`NetworkGate`, `StreamProxy`,
    `DownloadStore`): Ktor has a wasm build; OkHttp doesn't.
  - Sockets and TLS: `ServerSocket`, `Socket`, `DatagramSocket`, `InetAddress`,
    `NetworkInterface`, `SSLContext`/`SSLEngine`/trust managers, `KeyStore`, `KeyPairGenerator`,
    `X509Certificate` (`StreamProxy`, `LoopbackCert`, `PlexGdm`, `PlexCompanionListener`).
  - media3 (`Player`, `PlaybackException`), `MediaMetadataRetriever`, `LruCache`, `StatFs`,
    `NetworkSecurityPolicy`, `RandomAccessFile`, `BigInteger`, `Normalizer`, atomics,
    `TimeUnit`, `WeakHashMap`, `runBlocking`, `BuildConfig`.
- **Plan:** browse and play from Plex on the home network first (Will). What a browser can't do
  (amp's loopback `StreamProxy`, Plex GDM discovery over UDP, the companion listener, DLNA cast,
  background transfers) compiles against stand-ins that fail at runtime; amp already falls back
  where they fail (`StreamProxy` tests itself and hands the player the server's own URL).

**After the first compile** (2026-10-11):
- Stand-ins compiled it in four more rounds; the last errors were JVM conveniences (`copyTo`,
  `buffered`, `inputStream()`, `Enumeration.toList()`, `Map.remove(k, v)`), now in the template.
- Two exact rewrites: `StreamProxy`'s access-order `LinkedHashMap` (final on Wasm) and
  `AppSettings`' `p.remove(...)` (below).
- Running it found, in light-web: `LocalWindowInfo.containerSize` was the browser window (amp
  scales its layout from it; everything was ~2.8x too big); the home list didn't scroll past
  three tools; in LP3 mode a click didn't focus the canvas, so computer keys didn't reach text
  fields (since `MouseAsTouch`). In the stand-ins: MD5 was missing (Subsonic's login token).
- **Kotlin/Wasm unboxes discarded generic results.** DataStore's `MutablePreferences.remove`
  returns null for an unset key though declared `T`; on the JVM nobody looks, on Wasm a Boolean
  key throws "Cannot cast null to kotlin.Boolean". Expect the same wherever a Java-ish library
  returns null for a non-null `T`.
- **What works** (headless, against Navidrome's public demo, Subsonic): adding a source, library
  sync (50 albums, artwork), opening an album, playing (the browser streams straight from the
  server), pause, next. Plex: the plex.tv link code is fetched and polled; linking needs Will's
  account. Amp's loopback proxy switches itself off at start, as designed.
- **Known:** amp's reachability ping can time out during a sync (the browser's six connections
  per origin are busy; on the phone the ping has its own pool), so amp briefly calls the server
  unreachable. The app's wasm grew from 1.74 to 2.39 MB gzipped.

## Rejected

| Tool | Date | Reason |
|---|---|---|
| ~~light-amp, light-amp-fork~~ | 2026-10-11 | `light-sdk` submodule at 15b1ca1, not a release tag. Taken the same day under the changed policy (above). |

light-herdr also fails the check (vendored `sdk/` matches no release; closest v0.1.1), but it isn't
finished, so it's left out rather than recorded as rejected (Will, 2026-10-11).
