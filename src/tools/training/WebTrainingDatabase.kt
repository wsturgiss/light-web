@file:Suppress("PackageDirectoryMismatch")

package com.thelightphone.training.model

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import lightweb.WebDatabase

/**
 * light-training's TrainingDatabase, for the web: the tool's own DAO interfaces
 * (model/TrainingDao.kt), implemented over in-memory tables saved to localStorage as JSON.
 *
 * Room has no wasm target, so this stands in for the code Room generates. Each method does
 * what its @Query says, including ordering (COLLATE NOCASE is a lowercase sort). When the
 * tool's schema or queries change, this has to change with it: compare against TrainingDao.kt
 * and TrainingEntities.kt (schema version 11).
 */
@WebDatabase(TrainingDatabase::class)
internal class WebTrainingDatabase(name: String) : TrainingDatabase() {

    private data class Tables(
        val muscleGroups: List<MuscleGroupEntity> = emptyList(),
        val exercises: List<ExerciseEntity> = emptyList(),
        val sessions: List<WorkoutSessionEntity> = emptyList(),
        val loggedExercises: List<LoggedWeightExerciseEntity> = emptyList(),
        val sets: List<WeightSetEntity> = emptyList(),
        val intervalPresets: List<IntervalPresetEntity> = emptyList(),
        val cardioSessions: List<CardioSessionEntity> = emptyList(),
        val nextLoggedExerciseId: Long = 1,
        val nextSetId: Long = 1,
    )

    private val storageKey = "lightweb:room:com.thelightphone.training/$name"
    private val tables = MutableStateFlow(load())

    private inline fun write(change: (Tables) -> Tables) {
        tables.value = change(tables.value)
        runCatching { localStorage.setItem(storageKey, encode(tables.value).toString()) }
    }

    private fun duplicate(table: String, id: Any): Nothing =
        throw IllegalStateException("UNIQUE constraint failed: $table.id ($id)")

    // ---- DAOs -------------------------------------------------------------------------------

    private val muscleGroups = object : MuscleGroupDao {
        override fun observeAll(): Flow<List<MuscleGroupEntity>> =
            tables.map { t -> t.muscleGroups.sortedBy { it.name.lowercase() } }
        override suspend fun insert(group: MuscleGroupEntity) = write { t ->
            if (t.muscleGroups.any { it.id == group.id }) duplicate("muscle_groups", group.id)
            t.copy(muscleGroups = t.muscleGroups + group)
        }
        override suspend fun update(group: MuscleGroupEntity) = write { t ->
            t.copy(muscleGroups = t.muscleGroups.map { if (it.id == group.id) group else it })
        }
        override suspend fun deleteById(id: String) = write { t -> t.copy(muscleGroups = t.muscleGroups.filter { it.id != id }) }
        override suspend fun count(): Int = tables.value.muscleGroups.size
        override suspend fun getAll(): List<MuscleGroupEntity> = tables.value.muscleGroups
    }

    private val exercises = object : ExerciseDao {
        override fun observeAll(): Flow<List<ExerciseEntity>> =
            tables.map { t -> t.exercises.sortedBy { it.name.lowercase() } }
        override suspend fun insert(exercise: ExerciseEntity) = write { t ->
            if (t.exercises.any { it.id == exercise.id }) duplicate("exercises", exercise.id)
            t.copy(exercises = t.exercises + exercise)
        }
        override suspend fun update(exercise: ExerciseEntity) = write { t ->
            t.copy(exercises = t.exercises.map { if (it.id == exercise.id) exercise else it })
        }
        override suspend fun deleteById(id: String) = write { t -> t.copy(exercises = t.exercises.filter { it.id != id }) }
        override suspend fun count(): Int = tables.value.exercises.size
        override suspend fun getByIds(ids: List<String>): List<ExerciseEntity> =
            tables.value.exercises.filter { it.id in ids }
    }

    private val intervalPresets = object : IntervalPresetDao {
        override fun observeAll(): Flow<List<IntervalPresetEntity>> =
            tables.map { t -> t.intervalPresets.sortedBy { it.name.lowercase() } }
        override suspend fun insert(preset: IntervalPresetEntity) = write { t ->
            if (t.intervalPresets.any { it.id == preset.id }) duplicate("interval_presets", preset.id)
            t.copy(intervalPresets = t.intervalPresets + preset)
        }
        override suspend fun update(preset: IntervalPresetEntity) = write { t ->
            t.copy(intervalPresets = t.intervalPresets.map { if (it.id == preset.id) preset else it })
        }
        override suspend fun deleteById(id: String) = write { t -> t.copy(intervalPresets = t.intervalPresets.filter { it.id != id }) }
        override suspend fun count(): Int = tables.value.intervalPresets.size
    }

    private val cardioSessions = object : CardioSessionDao {
        override fun observeAll(): Flow<List<CardioSessionEntity>> =
            tables.map { t -> t.cardioSessions.sortedWith(newestFirst({ it.date }, { it.createdAt }, { it.id })) }
        override suspend fun insert(session: CardioSessionEntity) = write { t ->
            if (t.cardioSessions.any { it.id == session.id }) duplicate("cardio_sessions", session.id)
            t.copy(cardioSessions = t.cardioSessions + session)
        }
        override suspend fun update(session: CardioSessionEntity) = write { t ->
            t.copy(cardioSessions = t.cardioSessions.map { if (it.id == session.id) session else it })
        }
        override suspend fun getById(id: String): CardioSessionEntity? = tables.value.cardioSessions.find { it.id == id }
        override suspend fun getAll(): List<CardioSessionEntity> = tables.value.cardioSessions
        override suspend fun deleteById(id: String) = write { t -> t.copy(cardioSessions = t.cardioSessions.filter { it.id != id }) }
        override suspend fun countByExerciseId(exerciseId: String): Int =
            tables.value.cardioSessions.count { it.exerciseId == exerciseId }
    }

    private val workoutSessions = object : WorkoutSessionDao {
        override suspend fun insertSession(session: WorkoutSessionEntity) = write { t ->
            if (t.sessions.any { it.id == session.id }) duplicate("workout_sessions", session.id)
            t.copy(sessions = t.sessions + session)
        }

        override suspend fun insertLoggedWeightExercise(exercise: LoggedWeightExerciseEntity): Long {
            var id = 0L
            write { t ->
                id = if (exercise.id == 0L) t.nextLoggedExerciseId else exercise.id
                if (t.loggedExercises.any { it.id == id }) duplicate("logged_exercises", id)
                t.copy(
                    loggedExercises = t.loggedExercises + exercise.copy(id = id),
                    nextLoggedExerciseId = maxOf(t.nextLoggedExerciseId, id + 1),
                )
            }
            return id
        }

        override suspend fun insertSets(sets: List<WeightSetEntity>) = write { t ->
            var next = t.nextSetId
            val rows = sets.map { s ->
                val id = if (s.id == 0L) next else s.id
                if (t.sets.any { it.id == id }) duplicate("exercise_sets", id)
                next = maxOf(next, id + 1)
                s.copy(id = id)
            }
            t.copy(sets = t.sets + rows, nextSetId = next)
        }

        override suspend fun listSessionsWithExercises(): List<WorkoutSessionWithExercises> {
            val t = tables.value
            return t.sessions.sortedWith(newestFirst({ it.date }, { it.createdAt }, { it.id })).map { withExercises(t, it) }
        }

        override suspend fun getSessionWithExercises(id: String): WorkoutSessionWithExercises? {
            val t = tables.value
            return t.sessions.find { it.id == id }?.let { withExercises(t, it) }
        }

        override suspend fun count(): Int = tables.value.sessions.size

        override suspend fun countByExerciseId(exerciseId: String): Int =
            tables.value.loggedExercises.count { it.exerciseId == exerciseId }

        override suspend fun deleteSetsForSession(sessionId: String) = write { t ->
            val logged = t.loggedExercises.filter { it.sessionId == sessionId }.map { it.id }.toSet()
            t.copy(sets = t.sets.filter { it.loggedWeightExerciseId !in logged })
        }

        override suspend fun deleteExercisesForSession(sessionId: String) = write { t ->
            t.copy(loggedExercises = t.loggedExercises.filter { it.sessionId != sessionId })
        }

        override suspend fun deleteSessionById(sessionId: String) = write { t ->
            t.copy(sessions = t.sessions.filter { it.id != sessionId })
        }
        // insertFullSession / replaceFullSession are the interface's own default methods. The
        // page is single-threaded and nothing here suspends mid-write, so they're atomic enough.
    }

    private fun withExercises(t: Tables, s: WorkoutSessionEntity) = WorkoutSessionWithExercises(
        session = s,
        exercises = t.loggedExercises.filter { it.sessionId == s.id }.map { e ->
            LoggedWeightExerciseWithSets(e, t.sets.filter { it.loggedWeightExerciseId == e.id })
        },
    )

    override fun muscleGroupDao(): MuscleGroupDao = muscleGroups
    override fun exerciseDao(): ExerciseDao = exercises
    override fun workoutSessionDao(): WorkoutSessionDao = workoutSessions
    override fun intervalPresetDao(): IntervalPresetDao = intervalPresets
    override fun cardioSessionDao(): CardioSessionDao = cardioSessions

    // ---- Storage ----------------------------------------------------------------------------

    private fun load(): Tables {
        val text = runCatching { localStorage.getItem(storageKey) }.getOrNull() ?: return Tables()
        return runCatching { decode(Json.parseToJsonElement(text).jsonObject) }.getOrElse { Tables() }
    }

    private fun encode(t: Tables) = JsonObject(mapOf(
        "version" to JsonPrimitive(11),
        "muscle_groups" to JsonArray(t.muscleGroups.map { row("id" to it.id, "name" to it.name) }),
        "exercises" to JsonArray(t.exercises.map {
            row("id" to it.id, "name" to it.name, "primary_muscle_group_id" to it.primaryMuscleGroupId,
                "secondary_muscle_group_ids" to it.secondaryMuscleGroupIds, "tracked_fields" to it.trackedFields)
        }),
        "workout_sessions" to JsonArray(t.sessions.map {
            row("id" to it.id, "name" to it.name, "date" to it.date, "created_at" to it.createdAt, "updated_at" to it.updatedAt)
        }),
        "logged_exercises" to JsonArray(t.loggedExercises.map {
            row("id" to it.id, "session_id" to it.sessionId, "exercise_id" to it.exerciseId, "order_index" to it.orderIndex)
        }),
        "exercise_sets" to JsonArray(t.sets.map {
            row("id" to it.id, "logged_exercise_id" to it.loggedWeightExerciseId, "order_index" to it.orderIndex,
                "reps" to it.reps, "weight_kg" to it.weightKg, "status" to it.status)
        }),
        "interval_presets" to JsonArray(t.intervalPresets.map {
            row("id" to it.id, "name" to it.name, "work_seconds" to it.workSeconds, "rest_seconds" to it.restSeconds, "rounds" to it.rounds)
        }),
        "cardio_sessions" to JsonArray(t.cardioSessions.map {
            row("id" to it.id, "exercise_id" to it.exerciseId, "date" to it.date, "duration_seconds" to it.durationSeconds,
                "distance_km" to it.distanceKm, "pace" to it.pace, "created_at" to it.createdAt, "updated_at" to it.updatedAt)
        }),
        "next_logged_exercise_id" to JsonPrimitive(t.nextLoggedExerciseId),
        "next_set_id" to JsonPrimitive(t.nextSetId),
    ))

    private fun decode(o: JsonObject): Tables {
        fun rows(name: String) = o[name]?.jsonArray.orEmpty().map { it.jsonObject }
        fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
        fun JsonObject.sOrNull(k: String) = get(k)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int
        fun JsonObject.l(k: String) = getValue(k).jsonPrimitive.long
        fun JsonObject.dOrNull(k: String) = get(k)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
        return Tables(
            muscleGroups = rows("muscle_groups").map { MuscleGroupEntity(it.s("id"), it.s("name")) },
            exercises = rows("exercises").map {
                ExerciseEntity(it.s("id"), it.s("name"), it.s("primary_muscle_group_id"), it.s("secondary_muscle_group_ids"), it.s("tracked_fields"))
            },
            sessions = rows("workout_sessions").map {
                WorkoutSessionEntity(it.s("id"), it.s("name"), it.s("date"), it.l("created_at"), it.l("updated_at"))
            },
            loggedExercises = rows("logged_exercises").map {
                LoggedWeightExerciseEntity(it.l("id"), it.s("session_id"), it.s("exercise_id"), it.i("order_index"))
            },
            sets = rows("exercise_sets").map {
                WeightSetEntity(it.l("id"), it.l("logged_exercise_id"), it.i("order_index"), it.i("reps"), it.dOrNull("weight_kg"), it.s("status"))
            },
            intervalPresets = rows("interval_presets").map {
                IntervalPresetEntity(it.s("id"), it.s("name"), it.i("work_seconds"), it.i("rest_seconds"), it.i("rounds"))
            },
            cardioSessions = rows("cardio_sessions").map {
                CardioSessionEntity(it.s("id"), it.s("exercise_id"), it.s("date"), it.i("duration_seconds"),
                    it.dOrNull("distance_km"), it.sOrNull("pace"), it.l("created_at"), it.l("updated_at"))
            },
            nextLoggedExerciseId = o["next_logged_exercise_id"]?.jsonPrimitive?.long ?: 1,
            nextSetId = o["next_set_id"]?.jsonPrimitive?.long ?: 1,
        )
    }
}

private fun row(vararg cols: Pair<String, Any?>): JsonObject = JsonObject(cols.associate { (k, v) ->
    k to when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> error("Unsupported column type for $k")
    } as JsonElement
})

/** `ORDER BY date DESC, created_at DESC, id DESC`. */
private fun <T> newestFirst(date: (T) -> String, createdAt: (T) -> Long, id: (T) -> String): Comparator<T> =
    compareByDescending(date).thenByDescending(createdAt).thenByDescending(id)
