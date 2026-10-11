package lightweb.shell

import kotlinx.browser.window
import lightweb.generated.generatedTools
import lightweb.tools.CrashTestTool

/** Every tool in this build, in the order the home screen lists them (see `packs` in build.gradle.kts). */
val tools = generatedTools + listOfNotNull(
    CrashTestTool.takeIf { "crashtest" in window.location.search },
)
