import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.compose") version "1.12.1"
}

val lightDnd = file(providers.gradleProperty("lightDnd.dir").get())
val generated = layout.buildDirectory.dir("generated/lightweb")

/**
 * A set of Android resources and assets to serve to the page, and the `R` class Kotlin code
 * reaches them through. [name] is the URL prefix: `assets/<name>/res/...`, `assets/<name>/assets/...`.
 */
data class Pack(val name: String, val rPackage: String, val res: File?, val assets: File?)

val packs = listOf(
    Pack("sdk-ui", "com.thelightphone.sdk.ui", lightDnd.resolve("sdk/ui/src/main/res"), null),
    Pack("dnd", "com.wsturgiss.dnd", lightDnd.resolve("tool/src/main/res"), lightDnd.resolve("tool/src/main/assets")),
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
            "**/LightFont.kt",            // Akkurat from the system font list
            "**/LightHapticFeedback.kt",  // Vibrator
            "**/LightQrCodeScanner.kt",   // CameraX + ML Kit
            "**/LightTextInputEditor.kt", // the LP3 keyboard; see src/sdk
            "**/keyboard/**",
        )
    }
    from(lightDnd.resolve("tool/src/main/kotlin")) { into("dnd") }
    into(generated.map { it.dir("upstream") })

    // LightIcons.allEntries finds the icons by JVM reflection; list them instead.
    doLast {
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
            resources.srcDir("src/web")
            resources.srcDir(syncWebAssets)

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
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
