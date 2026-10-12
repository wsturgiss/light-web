import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("com.google.devtools.ksp") version "2.3.12"
}

// The SDK every tool compiles against: a light-sdk checkout at its latest release tag, the
// only SDK light-web builds tools for (see checkSdkVersions and docs/HANDOFF.md).
val lightSdk = file(providers.gradleProperty("lightSdk.dir").get())
val lightKeyboard = file(providers.gradleProperty("lightKeyboard.dir").get())
val generated = layout.buildDirectory.dir("generated/lightweb")

/**
 * A set of Android resources and assets to serve to the page, and the `R` class Kotlin code
 * reaches them through. [name] is the URL prefix: `assets/<name>/res/...`, `assets/<name>/assets/...`.
 *
 * A pack with a [tool] is a tool: the module holding its `lighttool.toml`. Its sources are synced
 * into `upstream/<name>` and it's registered on the home screen (see generateToolRegistry), in
 * this order.
 */
data class Pack(val name: String, val rPackage: String, val res: File?, val assets: File?, val tool: File? = null)

val packs = listOf(
    Pack("sdk-ui", "com.thelightphone.sdk.ui", lightSdk.resolve("sdk/ui/src/main/res"), null),
    Pack("keyboard", "com.thelightphone.lp3Keyboard.ui", lightKeyboard.resolve("ui/src/main/res"), null),
) + providers.gradleProperty("tools").get().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { toolPack(file(it)) }
val toolPacks = packs.filter { it.tool != null }

/**
 * A tool's pack, from its module directory alone, as the SDK's Gradle plugin reads it: the
 * plugin makes `tool.id` in lighttool.toml the namespace, so it's the R package too. The pack is
 * named after the id's last part (com.wsturgiss.dnd -> dnd).
 */
fun toolPack(dir: File): Pack {
    val toml = dir.resolve("lighttool.toml")
    check(toml.isFile) { "$dir has no lighttool.toml; `tools` in gradle.properties lists tool modules" }
    val id = Regex("""(?m)^\s*id\s*=\s*"([^"]+)"""").find(toml.readText())?.groupValues?.get(1)
        ?: error("$toml has no tool.id")
    val main = dir.resolve("src/main")
    return Pack(id.substringAfterLast('.'), id, main.resolve("res").takeIf { it.isDirectory }, main.resolve("assets").takeIf { it.isDirectory }, dir)
}

/**
 * Small, exact edits to upstream files, for the few things a stand-in can't reach. Each must
 * still match, or the build fails: that's how we find out upstream moved.
 */
val rewrites = mapOf(
    // Java's platform types: on the JVM `uri.host` and `attrs.getValue(name)` are `String!`, which
    // Kotlin lets a tool use as nullable in one place and non-null in another. A stand-in has to
    // pick; ours keep Java's real nullability, so a tool's non-null uses get the `!!` the JVM
    // applies implicitly (it would throw there too, or store a null where Kotlin promises none).
    "reader/com/yarosz/reader/CatalogueList.kt" to listOf(
        "append(uri.host.lowercase())" to "append(uri.host!!.lowercase())",
    ),
    "reader/com/yarosz/reader/Network.kt" to listOf(
        "uri.toASCIIString().substring(uri.scheme.length)" to "uri.toASCIIString().substring(uri.scheme!!.length)",
    ),
    "reader/com/yarosz/reader/Epub.kt" to listOf(
        "spine += attrs.getValue(\"idref\") to" to "spine += attrs.getValue(\"idref\")!! to",
    ),
    // Kotlin/Wasm's LinkedHashMap is final and has no access order; light-amp's loopback proxy
    // subclasses java.util's for an LRU table (see AccessOrderLinkedHashMap).
    "amp/com/sublunar/amp/data/StreamProxy.kt" to listOf(
        "object : LinkedHashMap<String, String>(256, 0.75f, true) {" to
            "object : lightweb.compat.AccessOrderLinkedHashMap<String, String>(256, 0.75f, true) {",
    ),
    // The SDK's text editor with the embedded LP3 keyboard becomes LightTextInputEditorLp3; the
    // public LightTextInputEditor (src/sdk) picks it or a plain text field by DeviceMode.
    "ui/com/thelightphone/sdk/ui/LightTextInputEditor.kt" to listOf(
        "LightTextInputEditor(" to "LightTextInputEditorLp3(",
        // ViewModelProvider.Factory takes a KClass and CreationExtras off Android.
        "override fun <T : ViewModel> create(modelClass: Class<T>): T {" to
            "override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {",
    ),
)

/**
 * A tool's Room 2 source, rewritten for Room 3: Room's multiplatform line, the one with a wasm
 * build. Its API is Room 2's under a new package, apart from what's translated here:
 *
 * - imports: `androidx.room.` -> `androidx.room3.`. Every use must be an import; a fully
 *   qualified one fails the build rather than resolving to nothing.
 * - `@Relation(parentColumn = "a", entityColumn = "b")` takes arrays: `parentColumns = ["a"]`.
 * - A `@Database` class needs `@ConstructedBy` and an `expect object` off Android, which Room's
 *   KSP processor fills in with the `actual`; both are added here.
 */
fun toRoom3(source: String, path: String): String {
    var src = source.replace(Regex("""(?m)^import androidx\.room\."""), "import androidx.room3.")
    check("androidx.room." !in src) { "$path names androidx.room outside an import; extend toRoom3 in build.gradle.kts" }
    src = src.replace(Regex("""\b(parent|entity)Column\s*=\s*("(?:[^"\\]|\\.)*")""")) { "${it.groupValues[1]}Columns = [${it.groupValues[2]}]" }
    if (Regex("""(?m)^@Database\b""").containsMatchIn(src) && "@ConstructedBy" !in src) {
        val db = Regex("""(?m)^((?:(?:public|internal)\s+)?)abstract class (\w+)\s*:\s*RoomDatabase\(\)""").findAll(src).toList()
        check(db.size == 1) { "$path: toRoom3 expects one `abstract class X : RoomDatabase()` beside @Database; found ${db.size}" }
        val (visibility, name) = db[0].destructured
        src = src.replaceRange(db[0].range, "@androidx.room3.ConstructedBy(${name}Constructor::class)\n${db[0].value}") +
            "\n// light-web: Room's KSP processor writes the actual.\n" +
            "${visibility}expect object ${name}Constructor : androidx.room3.RoomDatabaseConstructor<$name>\n"
    }
    return src
}

/**
 * Upstream sources, compiled as they are. What's excluded here is replaced by a web version
 * under src/ (see README.md, "How a tool gets to the browser").
 */
val syncUpstreamSources by tasks.registering(Sync::class) {
    dependsOn(checkSdkVersions)
    from(lightSdk.resolve("sdk/shared/src/main/kotlin")) { into("shared") }
    from(lightSdk.resolve("sdk/ui/src/main/kotlin")) {
        into("ui")
        exclude(
            "**/LightHapticFeedback.kt",  // Vibrator
            "**/LightQrCodeScanner.kt",   // CameraX + ML Kit
        )
    }
    from(lightKeyboard.resolve("ui/src/main/java")) {
        into("keyboard")
        exclude(
            "**/Lp3KeyboardView.kt",       // an Android View, for IMEs
            "**/HardwareKeyboardInput.kt", // reads Android InputDevices
        )
    }
    for (p in toolPacks) from(p.tool!!.resolve("src/main/kotlin")) { into(p.name) }
    into(generated.map { it.dir("upstream") })

    doLast {
        // The tools are on Room 2; the browser build compiles them against Room 3 (see toRoom3).
        for (p in toolPacks) destinationDir.resolve(p.name).walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            if ("androidx.room." in src) f.writeText(toRoom3(src, f.relativeTo(destinationDir).path))
        }

        for ((path, edits) in rewrites) {
            val f = destinationDir.resolve(path)
            var src = f.readText()
            for ((from, to) in edits) {
                check(from in src) { "$path no longer contains `$from`; update `rewrites`" }
                src = src.replace(from, to)
            }
            f.writeText(src)
        }

        // LightIcons.allEntries finds the icons by JVM reflection; list them instead.
        val f = destinationDir.resolve("ui/com/thelightphone/sdk/ui/LightIcons.kt")
        val src = f.readText()
        val names = Regex("""object (\w+) : LightIconConfiguration""").findAll(src).map { it.groupValues[1] }.sorted()
        val cut = src.indexOf("    val allEntries")
        check(cut > 0) { "LightIcons.kt no longer has allEntries where light-web expects it" }
        f.writeText(
            src.substring(0, cut) +
                "    // light-web: listed at build time; upstream finds these by reflection.\n" +
                "    val allEntries: List<Pair<String, LightIconConfiguration>> by lazy {\n" +
                "        listOf(\n" + names.joinToString("") { "            \"$it\" to $it,\n" } + "        )\n    }\n}\n"
        )
    }
}

/**
 * Refuses, before anything compiles, a tool not built on the latest light-sdk release, and a
 * light-sdk checkout not at that release: light-web builds tools against the latest release
 * only. The check is tool/sdk-version.mjs, which the API scanner uses too.
 */
val checkSdkVersions by tasks.registering(Exec::class) {
    val script = file("tool/sdk-version.mjs")
    inputs.file(script)
    commandLine(listOf("node", script.path, "--sdk", lightSdk.path, "--require-latest", "--check-sdk-checkout") + toolPacks.map { it.tool!!.path })
}

/** Every tool's exported Room schemas (`assets/schemas/<database class>/<version>.json`), in one place for KSP. */
val syncRoomSchemas by tasks.registering(Sync::class) {
    for (p in toolPacks) p.assets?.resolve("schemas")?.let { from(it) }
    into(generated.map { it.dir("room-schemas") })
}

/** SQLite's wasm build, from the npm package, to `sqlite/` beside the page (see sqlite-worker.js). */
val syncSqliteWasm by tasks.registering(Sync::class) {
    dependsOn("kotlinWasmNpmInstall")
    from(layout.buildDirectory.dir("wasm/node_modules/@sqlite.org/sqlite-wasm/dist")) {
        include("index.mjs", "sqlite3.wasm")
        into("sqlite")
    }
    into(generated.map { it.dir("sqlite-web") })
}

/** Copies every pack's files to where the page serves them from. */
val syncWebAssets by tasks.registering(Sync::class) {
    for (p in packs) {
        p.res?.let { from(it) { into("assets/${p.name}/res") } }
        p.assets?.let { from(it) { into("assets/${p.name}/assets") } }
    }
    into(generated.map { it.dir("web") })
}

/**
 * Writes an `R` per pack, with Android's shape (`R.drawable.ic_add_white`, `R.font.mek_sans`) so
 * upstream code compiles unchanged, and the table that resolves those ids to served paths.
 */
abstract class GenerateResources : DefaultTask() {
    @get:Input abstract val packNames: ListProperty<String>
    @get:Input abstract val rPackages: ListProperty<String>
    @get:Input abstract val resDirs: ListProperty<String>
    @get:Input abstract val assetDirs: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        var nextId = 0x7f000001
        val idToPath = mutableListOf<Pair<Int, String>>()
        val nameToId = mutableListOf<Pair<String, Int>>()
        val packFiles = mutableListOf<Pair<String, List<String>>>()

        for (i in packNames.get().indices) {
            val name = packNames.get()[i]
            val res = resDirs.get()[i].takeIf { it.isNotEmpty() }?.let(::File)
            val assets = assetDirs.get()[i].takeIf { it.isNotEmpty() }?.let(::File)
            val files = mutableListOf<String>()
            val types = sortedMapOf<String, MutableList<Pair<String, Int>>>()

            res?.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { typeDir ->
                typeDir.listFiles()!!.filter { it.isFile }.sortedBy { it.name }.forEach { f ->
                    val path = "assets/$name/res/${typeDir.name}/${f.name}"
                    val id = nextId++
                    types.getOrPut(typeDir.name) { mutableListOf() } += f.nameWithoutExtension to id
                    idToPath += id to path
                    nameToId += "${typeDir.name}/${f.nameWithoutExtension}" to id
                    files += path
                }
            }
            assets?.walkTopDown()?.filter { it.isFile }?.sortedBy { it.path }?.forEach { f ->
                files += "assets/$name/assets/${f.relativeTo(assets).invariantSeparatorsPath}"
            }
            packFiles += name to files

            val pkg = rPackages.get()[i]
            val r = buildString {
                appendLine("// Generated by light-web from $name's res/. Do not edit.")
                appendLine("package $pkg")
                appendLine()
                appendLine("object R {")
                for ((type, entries) in types) {
                    appendLine("    object $type {")
                    for ((n, id) in entries) appendLine("        const val $n = 0x${id.toString(16)}")
                    appendLine("    }")
                }
                appendLine("}")
            }
            out.resolve(pkg.replace('.', '/')).apply { mkdirs() }.resolve("R.kt").writeText(r)
        }

        val table = buildString {
            appendLine("// Generated by light-web. Do not edit.")
            appendLine("package lightweb.generated")
            appendLine()
            appendLine("/** Android resource id → the path the page serves it at. */")
            appendLine("val resourcePaths: Map<Int, String> = mapOf(")
            for ((id, path) in idToPath) appendLine("    0x${id.toString(16)} to \"$path\",")
            appendLine(")")
            appendLine()
            appendLine("/** `type/name` → id, for Resources.getIdentifier. Names can repeat across packs; the last wins. */")
            appendLine("val resourceIds: Map<String, Int> = mapOf(")
            for ((name, id) in nameToId) appendLine("    \"$name\" to 0x${id.toString(16)},")
            appendLine(")")
            appendLine()
            appendLine("/** Every file in each pack, so a pack can be loaded before anything reads from it. */")
            appendLine("val packFiles: Map<String, List<String>> = mapOf(")
            for ((name, files) in packFiles) {
                appendLine("    \"$name\" to listOf(")
                for (f in files) appendLine("        \"$f\",")
                appendLine("    ),")
            }
            appendLine(")")
        }
        out.resolve("lightweb/generated").apply { mkdirs() }.resolve("Resources.kt").writeText(table)
    }
}

val generateResources by tasks.registering(GenerateResources::class) {
    packNames.set(packs.map { it.name })
    rPackages.set(packs.map { it.rPackage })
    resDirs.set(packs.map { it.res?.path ?: "" })
    assetDirs.set(packs.map { it.assets?.path ?: "" })
    // The file lists are inputs too: re-run when a pack gains or loses a file.
    inputs.files(packs.flatMap { listOfNotNull(it.res, it.assets) }).withPropertyName("packFiles")
    outputDir.set(generated.map { it.dir("kotlin") })
}

/**
 * Writes src/templates/JvmBuiltins.kt.template into every package the upstream sources declare:
 * the JVM names (`Math`, `"%d".format`, `Foo::class.java`, `Dispatchers.IO`...) that Android code
 * uses unqualified, which off the JVM resolve only if declared in the package itself.
 *
 * The template is blank-line-separated blocks, one declaration each. A block is left out of a
 * package that declares the same name itself (light-dnd's model has its own `Character`), where
 * the two would clash; and out of a package that star-imports one declaring it, where ours would
 * quietly win (a package's own names beat star imports).
 */
abstract class GenerateJvmBuiltins : DefaultTask() {
    @get:InputDirectory abstract val upstreamDir: DirectoryProperty
    /** Our own sources, which can declare top-level names into upstream packages too. */
    @get:InputFiles abstract val ownSources: ConfigurableFileCollection
    @get:InputFile abstract val template: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val packageLine = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        // An unindented declaration's name: `class Character`, `fun String.format(`,
        // `val <T : Any> KClass<T>.java`.
        val declaration = Regex(
            """^(?:(?:internal|private|public|data|sealed|enum|abstract|open|inline|annotation|value|const)\s+)*""" +
                """(?:class|object|interface|typealias|fun|val|var)\s+(?:<.*?>\s+)?(?:[\w<>?, *]+\.)?(\w+)""",
            RegexOption.MULTILINE,
        )
        val starImport = Regex("""^import\s+([\w.]+)\.\*""", RegexOption.MULTILINE)
        class Source(val pkg: String, val declares: Set<String>, val starImports: Set<String>)
        fun scan(files: Sequence<File>) = files.filter { it.isFile && it.extension == "kt" }.mapNotNull { f ->
            val text = f.readText()
            val pkg = packageLine.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
            Source(pkg, declaration.findAll(text).map { it.groupValues[1] }.toSet(), starImport.findAll(text).map { it.groupValues[1] }.toSet())
        }
        val upstream = scan(upstreamDir.get().asFile.walkTopDown()).toList()
        val byPackage = (upstream + scan(ownSources.asFileTree.files.asSequence())).groupBy { it.pkg }
        val declared = byPackage.mapValues { (_, s) -> s.flatMap { it.declares }.toSet() }
        val starImported = byPackage.mapValues { (_, s) -> s.flatMap { it.starImports }.toSet() }

        val text = template.get().asFile.readText()
        check("__PACKAGE__" in text) { "JvmBuiltins.kt.template has lost its __PACKAGE__ placeholder" }
        val blocks = text.split(Regex("""\n\s*\n""")).map { block -> block to declaration.find(block)?.groupValues?.get(1) }
        for (pkg in upstream.map { it.pkg }.toSortedSet()) {
            val own = (starImported[pkg].orEmpty() + pkg).flatMap { declared[it].orEmpty() }.toSet()
            val kept = blocks.filter { (_, name) -> name == null || name !in own }.joinToString("\n\n") { it.first }
            out.resolve(pkg.replace('.', '/')).apply { mkdirs() }
                .resolve("JvmBuiltins.kt").writeText(kept.replace("__PACKAGE__", pkg).trimEnd() + "\n")
        }
    }
}

val generateJvmBuiltins by tasks.registering(GenerateJvmBuiltins::class) {
    upstreamDir.set(layout.dir(syncUpstreamSources.map { it.destinationDir }))
    template.set(file("src/templates/JvmBuiltins.kt.template"))
    ownSources.from("src/compat", "src/sdk", "src/shell", "src/tools")
    outputDir.set(generated.map { it.dir("builtins") })
}

/**
 * Writes `lightweb.generated.generatedTools`: a LightTool per tool pack, from what Android's SDK
 * build reads too. The id and label come from `lighttool.toml`; the entry point and first screen
 * are the classes marked @EntryPoint and @InitialScreen in the tool's sources (KSP finds them on
 * Android).
 */
abstract class GenerateToolRegistry : DefaultTask() {
    @get:Input abstract val packNames: ListProperty<String>
    @get:InputFiles abstract val manifests: ConfigurableFileCollection
    @get:InputDirectory abstract val upstreamDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private class Found(val file: File, val pkg: String, val name: String)

    /** Every `@<annotation>`-marked class or object in [dir], with what follows the annotation. */
    private fun find(dir: File, annotation: String): List<Pair<Found, String>> {
        val packageLine = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        val marked = Regex("""^@$annotation\b(\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:internal|public|data|abstract|open)\s+)*(?:class|object)\s+(\w+)""", RegexOption.MULTILINE)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            val pkg = packageLine.find(text)?.groupValues?.get(1) ?: ""
            marked.findAll(text).map { Found(f, pkg, it.groupValues[2]) to it.groupValues[1] }
        }.toList()
    }

    private fun Found.qualified() = if (pkg.isEmpty()) name else "$pkg.$name"

    private fun tomlString(toml: File, key: String): String {
        val section = toml.readText().substringAfter("[tool]", "").substringBefore("\n[")
        val value = Regex("""^$key\s*=\s*"([^"]*)"""", RegexOption.MULTILINE).find(section)?.groupValues?.get(1)
        return checkNotNull(value) { "$toml has no [tool] $key" }
    }

    private fun kotlinString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    @TaskAction
    fun generate() {
        val tools = packNames.get().zip(manifests.files.toList()).map { (pack, toml) ->
            val sources = upstreamDir.get().asFile.resolve(pack)
            val screens = find(sources, "InitialScreen").map { it.first }
            val entryPoints = find(sources, "EntryPoint").map { it.first }
            val screen = screens.singleOrNull()
                ?: error("$pack needs exactly one @InitialScreen class; found ${screens.map { it.qualified() }}")
            check(entryPoints.size <= 1) { "$pack has more than one @EntryPoint: ${entryPoints.map { it.qualified() }}" }
            buildString {
                appendLine("    LightTool(")
                appendLine("        id = ${kotlinString(tomlString(toml, "id"))},")
                appendLine("        name = ${kotlinString(tomlString(toml, "label"))},")
                appendLine("        assetPack = ${kotlinString(pack)},")
                appendLine("        entryPoint = ${entryPoints.singleOrNull()?.qualified() ?: "null"},")
                appendLine("        initialScreen = { ${screen.qualified()}(it) },")
                appendLine("    ),")
            }
        }
        // Android generates a BuildConfig in the tool's namespace (its id, as for R); tools read
        // their version from it.
        val buildConfigs = packNames.get().zip(manifests.files.toList()).map { (_, toml) ->
            val id = tomlString(toml, "id")
            val versionName = tomlString(toml, "versionName")
            val versionCode = Regex("""(?m)^versionCode\s*=\s*(\d+)""").find(toml.readText())?.groupValues?.get(1) ?: "1"
            id to buildString {
                appendLine("// Generated by light-web from lighttool.toml (generateToolRegistry). Do not edit.")
                appendLine("package $id")
                appendLine()
                appendLine("object BuildConfig {")
                appendLine("    const val APPLICATION_ID = ${kotlinString(id)}")
                appendLine("    const val VERSION_NAME = ${kotlinString(versionName)}")
                appendLine("    const val VERSION_CODE = $versionCode")
                appendLine("    const val BUILD_TYPE = \"release\"")
                appendLine("    const val DEBUG = false")
                appendLine("}")
            }
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        for ((id, text) in buildConfigs) out.resolve(id.replace('.', '/')).apply { mkdirs() }.resolve("BuildConfig.kt").writeText(text)
        out.resolve("lightweb/generated").apply { mkdirs() }.resolve("Tools.kt").writeText(buildString {
            appendLine("// Generated by light-web (generateToolRegistry in build.gradle.kts). Do not edit.")
            appendLine("package lightweb.generated")
            appendLine()
            appendLine("import lightweb.LightTool")
            appendLine()
            appendLine("/** Every tool pack in build.gradle.kts, in its order. */")
            appendLine("val generatedTools: List<LightTool> = listOf(")
            tools.forEach(::append)
            appendLine(")")
        })
    }
}

val generateToolRegistry by tasks.registering(GenerateToolRegistry::class) {
    packNames.set(toolPacks.map { it.name })
    manifests.from(toolPacks.map { it.tool!!.resolve("lighttool.toml") })
    upstreamDir.set(layout.dir(syncUpstreamSources.map { it.destinationDir }))
    outputDir.set(generated.map { it.dir("registry") })
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("lightweb")
        browser {
            commonWebpackConfig {
                outputFileName = "lightweb.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Everything is in commonMain, though there's one target: Kotlin won't take an expect and
        // its actual in one source set, and Room's KSP processor writes the actuals (see toRoom3)
        // into wasmJsMain. With wasmJs as its only target, commonMain compiles as wasm code.
        commonMain {
            kotlin.srcDir("src/compat")
            kotlin.srcDir("src/sdk")
            kotlin.srcDir("src/shell")
            kotlin.srcDir("src/tools")
            kotlin.srcDir(syncUpstreamSources)
            kotlin.srcDir(generateResources)
            kotlin.srcDir(generateJvmBuiltins)
            kotlin.srcDir(generateToolRegistry)
            resources.srcDir("src/web")
            resources.srcDir(syncWebAssets)
            resources.srcDir(syncSqliteWasm)

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.material) // the LP3 keyboard is Material 2
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
                implementation("androidx.datastore:datastore-preferences-core:1.3.0-alpha11")
                // The version Compose Material 3 already brings in.
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("co.touchlab:kermit:2.2.0")
                // Ktor, light-amp's HTTP client, with its OkHttp engine replaced (src/compat/io/ktor).
                implementation("io.ktor:ktor-client-core:3.4.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
                implementation("androidx.room3:room3-runtime:3.0.3")
                implementation("androidx.sqlite:sqlite-web:2.7.1")
                // SQLite's own wasm build, for src/web/sqlite-worker.js. Not bundled: syncSqliteWasm
                // serves its files next to the page, and the worker loads them when a tool opens a database.
                implementation(npm("@sqlite.org/sqlite-wasm", "3.53.4-build2"))
            }
        }
    }

    compilerOptions {
        // Upstream code is written for Android and trips warnings that don't matter here.
        suppressWarnings.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Kotlin 2.3.20's wasm compiler intermittently crashes reading its own previous output
// (ArrayIndexOutOfBoundsException in WasmIrFileMetadata.fromByteArray) -- with incremental
// compilation off too. Starting each compile from nothing avoids it; it costs nothing extra,
// since without incremental compilation every compile is a full one anyway.
tasks.named("compileKotlinWasmJs") {
    doFirst {
        delete(layout.buildDirectory.dir("classes/kotlin/wasmJs/main"), layout.buildDirectory.dir("kotlin/compileKotlinWasmJs"))
    }
}

// The production build's optimizer (Binaryen) is up to date whenever the .wasm is, but it also
// passes on the JS glue beside it, where every `js("...")` body lives. A change only inside one
// (Net.kt's XMLHttpRequest code, say) leaves the .wasm the same, so the old glue would ship.
// Making the whole compiled output an input reruns it on any change.
tasks.matching { it.name == "compileProductionExecutableKotlinWasmJsOptimize" }.configureEach {
    inputs.dir(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/kotlin"))
        .withPropertyName("lightwebGlue")
}

dependencies {
    add("kspWasmJs", "androidx.room3:room3-compiler:3.0.3")
}

tasks.matching { it.name == "kspKotlinWasmJs" }.configureEach { dependsOn(syncRoomSchemas) }

ksp {
    // Room reads earlier schema versions from here for auto-migrations, and writes the current
    // one: a copy, so it never writes into a tool's checkout.
    arg("room.schemaLocation", syncRoomSchemas.get().destinationDir.path)
}
