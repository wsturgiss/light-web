package lightweb.shell

import kotlinx.browser.window
import lightweb.LightTool
import lightweb.generated.generatedTools
import lightweb.tools.CrashTestTool

/** Every tool in this build, in the order the home screen lists them (see `packs` in build.gradle.kts). */
private val allTools = generatedTools + listOfNotNull(
    CrashTestTool.takeIf { "crashtest" in window.location.search },
)

/**
 * The one tool the page's path names, for a link to just that tool: its asset pack's name
 * (`/dnd`, `/training`), optionally after its repo's (`/light-dnd`). Null on the root, or for a
 * path naming no tool, which list them all.
 */
val onlyTool: LightTool? = window.location.pathname.trim('/').lowercase().removePrefix("light-")
    .takeIf { it.isNotEmpty() }
    ?.let { slug -> allTools.firstOrNull { it.assetPack == slug } }

/** The tools the home screen lists: just [onlyTool] on its link, else every tool. */
val tools = onlyTool?.let(::listOf) ?: allTools
