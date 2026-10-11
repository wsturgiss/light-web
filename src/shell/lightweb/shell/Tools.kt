package lightweb.shell

import kotlinx.browser.window
import lightweb.tools.CrashTestTool
import lightweb.tools.DndTool
import lightweb.tools.TrainingTool

/** Every tool in this build, in the order the home screen lists them. */
val tools = listOfNotNull(
    DndTool,
    TrainingTool,
    CrashTestTool.takeIf { "crashtest" in window.location.search },
)
