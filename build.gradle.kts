import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.1"
}

val lightDnd = file(providers.gradleProperty("lightDnd.dir").get())
val lightTraining = file(providers.gradleProperty("lightTraining.dir").get())
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
    Pack("sdk-ui", "com.thelightphone.sdk.ui", lightDnd.resolve("sdk/ui/src/main/res"), null),
    Pack("keyboard", "com.thelightphone.lp3Keyboard.ui", lightKeyboard.resolve("ui/src/main/res"), null),
    Pack("dnd", "com.wsturgiss.dnd", lightDnd.resolve("tool/src/main/res"), lightDnd.resolve("tool/src/main/assets"), lightDnd.resolve("tool")),
    Pack("training", "com.thelightphone.training", null, lightTraining.resolve("tool/src/main/assets"), lightTraining.resolve("tool")),
)
val toolPacks = packs.filter { it.tool != null }

/**
 * Small, exact edits to upstream files, for the few things a stand-in can't reach. Each must
 * still match, or the build fails: that's how we find out upstream moved.
 */
val rewrites = mapOf(
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
 * Upstream sources, compiled as they are. What's excluded here is replaced by a web version
 * under src/ (see README.md, "How a tool gets to the browser").
 */
val syncUpstreamSources by tasks.registering(Sync::class) {
    from(lightDnd.resolve("sdk/shared/src/main/kotlin")) { into("shared") }
    from(lightDnd.resolve("sdk/ui/src/main/kotlin")) {
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
 * Android); its Room databases are the classes marked @WebDatabase under src/tools/<pack>.
 */
abstract class GenerateToolRegistry : DefaultTask() {
    @get:Input abstract val packNames: ListProperty<String>
    @get:InputFiles abstract val manifests: ConfigurableFileCollection
    @get:InputDirectory abstract val upstreamDir: DirectoryProperty
    @get:InputDirectory abstract val ownToolsDir: DirectoryProperty
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
            val databases = find(ownToolsDir.get().asFile.resolve(pack), "WebDatabase").map { (impl, args) ->
                // The database class as the annotation names it, resolved through the file's imports.
                val of = Regex("""\(\s*(?:of\s*=\s*)?([\w.]+)::class\s*\)""").find(args)?.groupValues?.get(1)
                    ?: error("${impl.file}: @WebDatabase needs the database class, as @WebDatabase(Foo::class)")
                val imported = Regex("""^import\s+([\w.]+\.$of)\s*$""", RegexOption.MULTILINE).find(impl.file.readText())?.groupValues?.get(1)
                val dbClass = when {
                    '.' in of -> of
                    imported != null -> imported
                    else -> "${impl.pkg}.$of"
                }
                "$dbClass::class to { name -> ${impl.qualified()}(name) }"
            }
            buildString {
                appendLine("    LightTool(")
                appendLine("        id = ${kotlinString(tomlString(toml, "id"))},")
                appendLine("        name = ${kotlinString(tomlString(toml, "label"))},")
                appendLine("        assetPack = ${kotlinString(pack)},")
                appendLine("        entryPoint = ${entryPoints.singleOrNull()?.qualified() ?: "null"},")
                appendLine("        initialScreen = { ${screen.qualified()}(it) },")
                if (databases.isNotEmpty()) appendLine("        databases = mapOf(${databases.joinToString()}),")
                appendLine("    ),")
            }
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
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
    ownToolsDir.set(file("src/tools"))
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
        wasmJsMain {
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
