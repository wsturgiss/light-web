# API coverage target

What the known tools use that light-web doesn't provide yet, from their Android bytecode, by the
API scanner (`tool/api-scan/scan.mjs`). This is the list of stand-ins to write, ranked by how many
tools need each. Regenerate it when light-web or the corpus changes.

Scanned 2026-10-11 against light-web `6f48e3f` plus the scanner commit:

```bash
node tool/api-scan/scan.mjs --json corpus.json ../light-dnd/tool ../light-training/tool \
  ../light-reader/tool ../light-amp-fork/tool ../light-sdk/examples/{ui-demo,weather,authenticator,audio-demo,tool-manager-demo}
```

- **Corpus:** the local tools and the SDK's five examples (light-sdk at v0.2.0). light-herdr is
  left out (unfinished). light-amp-fork is scanned for planning but rejected for building: its SDK
  isn't a release (see the handoff's SDK policy). The public tools in `TOOL-CATALOG.md` are next.
- **Reading it:** a name is a class, a member (`Class.member`) or a package-level function. A
  library light-web doesn't supply counts once, as the library, however much of it a tool calls.
  Room's generated database code counts once, as "needs a web database". light-dnd, light-reader
  and the weather example's 0 or 1 is the scanner agreeing with reality: they run, or (weather)
  need only Ktor.
- **How good is it?** Checked against light-reader's real first compile: it found 45 of the 48
  names the compile hit and 1 false positive, and it reports 0 for light-reader now that it runs.
  It misses what isn't in bytecode the same way: Kotlin-only annotations (`@JvmInline`), overloads
  that resolve differently on the web (`format(Locale, …)`), and a tool's explicit
  `java.util.Arrays` (Kotlin's own `copyOf` compiles to the same thing, so it's ignored). Details in
  `docs/TOOL-ONBOARDING.md`.

## By tool

| Tool | Missing names |
|---|---|
## By tool

| Tool | Missing names |
|---|---|
| light-dnd | 0 |
| light-training | 1 |
| light-reader | 0 |
| light-amp-fork | 108 |
| example: ui-demo | 7 |
| example: weather | 1 |
| example: authenticator | 19 |
| example: audio-demo | 32 |
| example: tool-manager-demo | 11 |

## Ranked: what more than one tool needs

| Tools | Name | Used by |
|---|---|---|
| 3 | `androidx.room (the database code Room generates: needs a web database)` | light-training, light-amp-fork, authenticator |
| 3 | `com.thelightphone.sdk.checkPermission` | light-amp-fork, ui-demo, audio-demo |
| 3 | `com.thelightphone.sdk.LightQrCodeScanner` | light-amp-fork, ui-demo, authenticator |
| 3 | `com.thelightphone.sdk.rememberPermissionRequestLauncher` | light-amp-fork, ui-demo, audio-demo |
| 3 | `com.thelightphone.sdk.PermissionRequestLauncher` | light-amp-fork, ui-demo, audio-demo |
| 3 | `com.thelightphone.sdk.shared.LightServiceMethod$GetPermission$Result.Granted` | light-amp-fork, ui-demo, audio-demo |
| 2 | `android.media.MediaMetadataRetriever` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.DefaultLightAudio` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudio` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioItem` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioPlayback` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioPlayer` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioSource` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioSource$FileSource` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioSource$UrlSource` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightAudioUsage` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.audio.LightMediaMetadata` | light-amp-fork, audio-demo |
| 2 | `com.thelightphone.sdk.LightJobResult` | light-amp-fork, tool-manager-demo |
| 2 | `com.thelightphone.sdk.LightJobResult$Success` | light-amp-fork, tool-manager-demo |
| 2 | `io.ktor (library: light-web doesn't supply it)` | light-amp-fork, weather |
| 2 | `java.security.Key` | light-amp-fork, authenticator |
| 2 | `java.security.KeyStore` | light-amp-fork, authenticator |
| 2 | `java.security.spec.AlgorithmParameterSpec` | light-amp-fork, authenticator |

## Everything, by package

- **android.graphics** (6): `Bitmap$Config.RGB_565` (light-amp-fork); `BitmapFactory$Options.inJustDecodeBounds` (light-amp-fork); `BitmapFactory$Options.inPreferredConfig` (light-amp-fork); `BitmapFactory$Options.inSampleSize` (light-amp-fork); `BitmapFactory$Options.outHeight` (light-amp-fork); `BitmapFactory$Options.outWidth` (light-amp-fork)
- **android.media** (1): `MediaMetadataRetriever` (light-amp-fork, audio-demo)
- **android.os** (1): `StatFs` (light-amp-fork)
- **android.security** (1): `NetworkSecurityPolicy` (light-amp-fork)
- **android.security.keystore** (2): `KeyGenParameterSpec` (authenticator); `KeyGenParameterSpec$Builder` (authenticator)
- **android.util** (1): `LruCache` (light-amp-fork)
- **android.view** (1): `KeyEvent.keyCodeToString` (ui-demo)
- **androidx.media3** (1): `androidx.media3 (library: light-web doesn't supply it)` (light-amp-fork)
- **androidx.room** (1): `androidx.room (the database code Room generates: needs a web database)` (light-training, light-amp-fork, authenticator)
- **com.thelightphone.sdk** (14): `checkPermission` (light-amp-fork, ui-demo, audio-demo); `LightQrCodeScanner` (light-amp-fork, ui-demo, authenticator); `rememberPermissionRequestLauncher` (light-amp-fork, ui-demo, audio-demo); `PermissionRequestLauncher` (light-amp-fork, ui-demo, audio-demo); `LightJobResult` (light-amp-fork, tool-manager-demo); `LightJobResult$Success` (light-amp-fork, tool-manager-demo); `callRemoteServiceMethod` (ui-demo); `hasRuntimePermission` (light-amp-fork); `refreshKeyboardOptions` (light-amp-fork); `LightFileShare` (tool-manager-demo); `LightFileShare$LightFile` (tool-manager-demo); `LightWork` (light-amp-fork); `SealedLightContext.getFileShare` (tool-manager-demo); `SimpleLightScreen.onKeyDown` (light-amp-fork)
- **com.thelightphone.sdk.audio** (24): `DefaultLightAudio` (light-amp-fork, audio-demo); `LightAudio` (light-amp-fork, audio-demo); `LightAudioItem` (light-amp-fork, audio-demo); `LightAudioPlayback` (light-amp-fork, audio-demo); `LightAudioPlayer` (light-amp-fork, audio-demo); `LightAudioSource` (light-amp-fork, audio-demo); `LightAudioSource$FileSource` (light-amp-fork, audio-demo); `LightAudioSource$UrlSource` (light-amp-fork, audio-demo); `LightAudioUsage` (light-amp-fork, audio-demo); `LightMediaMetadata` (light-amp-fork, audio-demo); `AudioCapabilities` (audio-demo); `CaptureConfig` (audio-demo); `LightAudioCapture` (audio-demo); `LightAudioError` (audio-demo); `LightAudioErrorKind` (audio-demo); `LightAudioException` (audio-demo); `LightAudioNetworkBlockedException` (light-amp-fork); `LightAudioNetworkPolicy` (light-amp-fork); `LightAudioPlayerAvailability` (light-amp-fork); `LightAudioRecorder` (audio-demo); `LightAudioSource$AssetSource` (audio-demo); `LightAudioVoice` (audio-demo); `MicSource` (audio-demo); `RecorderConfig` (audio-demo)
- **com.thelightphone.sdk.cast** (4): `DlnaCast` (light-amp-fork); `DlnaPosition` (light-amp-fork); `DlnaRenderer` (light-amp-fork); `DlnaState` (light-amp-fork)
- **com.thelightphone.sdk.display** (1): `LightDisplayColor` (light-amp-fork)
- **com.thelightphone.sdk.shared** (2): `LightServiceMethod$GetPermission$Result.Granted` (light-amp-fork, ui-demo, audio-demo); `LightServiceMethod$GetPermission$Result.BlockedByServer` (light-amp-fork)
- **com.thelightphone.sdk.transfer** (2): `LightTransferService` (light-amp-fork); `LightTransferService$Companion` (light-amp-fork)
- **com.thelightphone.toolmanager** (5): `ClientLeafNode` (tool-manager-demo); `ClientToolManifest` (tool-manager-demo); `FileBrowserSpec` (tool-manager-demo); `JobSpec` (tool-manager-demo); `LeafViewSpec` (tool-manager-demo)
- **io.ktor** (1): `io.ktor (library: light-web doesn't supply it)` (light-amp-fork, weather)
- **java.io** (5): `BufferedOutputStream` (light-amp-fork); `IOException.getMessage` (light-amp-fork); `OutputStreamWriter` (tool-manager-demo); `RandomAccessFile` (light-amp-fork); `Serializable` (light-amp-fork)
- **java.lang** (8): `InterruptedException` (light-amp-fork); `Runnable` (light-amp-fork); `Thread.interrupt` (light-amp-fork); `Thread.interrupted` (light-amp-fork); `Thread.join` (light-amp-fork); `Thread.setDaemon` (light-amp-fork); `Thread.sleep` (light-amp-fork); `Thread.start` (light-amp-fork)
- **java.math** (1): `BigInteger` (light-amp-fork)
- **java.net** (8): `DatagramPacket` (light-amp-fork); `DatagramSocket` (light-amp-fork); `Inet4Address` (light-amp-fork); `InetAddress` (light-amp-fork); `NetworkInterface` (light-amp-fork); `ServerSocket` (light-amp-fork); `Socket` (light-amp-fork); `URLDecoder` (authenticator)
- **java.nio** (1): `ByteBuffer` (authenticator)
- **java.security** (12): `Key` (light-amp-fork, authenticator); `KeyStore` (light-amp-fork, authenticator); `KeyPair` (light-amp-fork); `KeyPairGenerator` (light-amp-fork); `KeyStore$Entry` (authenticator); `KeyStore$LoadStoreParameter` (authenticator); `KeyStore$ProtectionParameter` (authenticator); `KeyStore$SecretKeyEntry` (authenticator); `PrivateKey` (light-amp-fork); `PublicKey` (light-amp-fork); `SecureRandom` (light-amp-fork); `Signature` (light-amp-fork)
- **java.security.cert** (3): `Certificate` (light-amp-fork); `CertificateFactory` (light-amp-fork); `X509Certificate` (light-amp-fork)
- **java.security.spec** (2): `AlgorithmParameterSpec` (light-amp-fork, authenticator); `ECGenParameterSpec` (light-amp-fork)
- **java.text** (2): `Normalizer` (light-amp-fork); `Normalizer$Form` (light-amp-fork)
- **java.time** (5): `Instant.atZone` (audio-demo); `Instant.parse` (light-amp-fork); `LocalDateTime` (audio-demo); `ZonedDateTime` (audio-demo); `ZoneId` (audio-demo)
- **java.time.format** (1): `DateTimeFormatter.withZone` (audio-demo)
- **java.time.temporal** (1): `TemporalAccessor` (audio-demo)
- **java.util** (7): `Collections` (light-amp-fork); `Enumeration` (light-amp-fork); `GregorianCalendar` (light-amp-fork); `LinkedHashMap.getOrDefault` (light-amp-fork); `Random` (light-amp-fork); `TimeZone` (light-amp-fork); `WeakHashMap` (light-amp-fork)
- **java.util.concurrent** (2): `ArrayBlockingQueue` (light-amp-fork); `TimeUnit` (light-amp-fork)
- **java.util.concurrent.atomic** (3): `AtomicBoolean` (light-amp-fork); `AtomicInteger` (light-amp-fork); `AtomicLong` (light-amp-fork)
- **javax.crypto** (4): `Cipher` (authenticator); `KeyGenerator` (authenticator); `Mac` (authenticator); `SecretKey` (authenticator)
- **javax.crypto.spec** (2): `GCMParameterSpec` (authenticator); `SecretKeySpec` (authenticator)
- **javax.net** (1): `SocketFactory` (light-amp-fork)
- **javax.net.ssl** (11): `HttpsURLConnection` (light-amp-fork); `KeyManager` (light-amp-fork); `KeyManagerFactory` (light-amp-fork); `SSLContext` (light-amp-fork); `SSLEngine` (light-amp-fork); `SSLServerSocketFactory` (light-amp-fork); `SSLSocketFactory` (light-amp-fork); `TrustManager` (light-amp-fork); `TrustManagerFactory` (light-amp-fork); `X509ExtendedTrustManager` (light-amp-fork); `X509TrustManager` (light-amp-fork)
- **kotlin.io** (2): `copyTo` (light-amp-fork); `normalize` (light-amp-fork)
- **okhttp3** (1): `okhttp3 (library: light-web doesn't supply it)` (light-amp-fork)
