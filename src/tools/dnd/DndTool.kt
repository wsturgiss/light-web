package lightweb.tools

import com.wsturgiss.dnd.HomeScreen
import com.wsturgiss.dnd.ToolEntryPoint
import lightweb.LightTool

/** light-dnd, compiled from ../light-dnd/tool unchanged. */
val DndTool = LightTool(
    id = "com.wsturgiss.dnd",
    name = "D&D",
    assetPack = "dnd",
    entryPoint = ToolEntryPoint,
    initialScreen = { HomeScreen(it) },
)
