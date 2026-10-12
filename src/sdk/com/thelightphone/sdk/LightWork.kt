package com.thelightphone.sdk

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The SDK's background jobs (WorkManager on the phone), as the v0.2.0 API. A tab can't run work
// once it's closed, so nothing is scheduled: enqueue returns false, as it does on the phone for
// a job it doesn't know, and every job reads as NotScheduled.

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class LightJob(val key: String)

const val LIGHT_FAIL_REASON = "LIGHT_FAIL_REASON"
const val LIGHT_SUCCESS_MESSAGE = "LIGHT_SUCCESS_MESSAGE"
const val LIGHT_SUCCESS_OUTPUT_FILE = "LIGHT_SUCCESS_OUTPUT_FILE"

sealed interface LightJobResult {
    // Defaults for outputFilePath and message: v0.1.x had them, and tools on it call Success().
    class Success(val outputData: Map<String, String> = emptyMap(), val outputFilePath: String? = null, val message: String? = null) : LightJobResult
    object Retry : LightJobResult
    class Error(val reason: String, val outputData: Map<String, String> = emptyMap()) : LightJobResult
}

typealias LightJobHandler = suspend (SealedLightContext, Map<String, String>) -> LightJobResult

sealed interface LightJobState {
    object Enqueued : LightJobState
    object Running : LightJobState
    class Succeeded(val outputData: Map<String, String>) : LightJobState
    class Failed(val outputData: Map<String, String>) : LightJobState
    object Cancelled : LightJobState
    object NotScheduled : LightJobState
}

object LightWork {
    fun enqueue(
        lightContext: SealedLightContext,
        jobKey: String,
        inputData: Map<String, String> = emptyMap(),
        tag: String? = null,
    ): Boolean = false

    fun enqueuePeriodic(
        lightContext: SealedLightContext,
        jobKey: String,
        repeatInterval: Duration,
        inputData: Map<String, String> = emptyMap(),
        tag: String? = null,
    ): Boolean = false

    fun cancel(lightContext: SealedLightContext, jobKeyOrTag: String) {}

    fun observe(lightContext: SealedLightContext, jobKeyOrTag: String): Flow<LightJobState> = flowOf(LightJobState.NotScheduled)

    suspend fun getState(lightContext: SealedLightContext, jobKeyOrTag: String): LightJobState = LightJobState.NotScheduled

    suspend fun awaitCompletion(lightContext: SealedLightContext, jobKeyOrTag: String): LightJobState = LightJobState.NotScheduled
}
