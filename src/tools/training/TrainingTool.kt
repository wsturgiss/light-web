package lightweb.tools

import com.thelightphone.training.HomeScreen
import com.thelightphone.training.ToolEntryPoint
import com.thelightphone.training.model.TrainingDatabase
import com.thelightphone.training.model.WebTrainingDatabase
import lightweb.LightTool

/** light-training, compiled from ../light-training/tool unchanged. */
val TrainingTool = LightTool(
    id = "com.thelightphone.training",
    name = "Training",
    assetPack = "training",
    entryPoint = ToolEntryPoint,
    initialScreen = { HomeScreen(it) },
    databases = mapOf(TrainingDatabase::class to { name -> WebTrainingDatabase(name) }),
)
