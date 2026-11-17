package com.irontrack.app.data.local

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Versioned, human-readable local backup file. All data stays on-device. */
object WorkoutExport {
    // Kept from the first release so old JSON backups still import.
    private const val FORMAT = "workout-tracker-backup"
    private const val VERSION = 1

    suspend fun write(context: Context, database: WorkoutDatabase, uri: Uri) {
        val dao = database.workoutDao()
        val locations = dao.allLocations()
        val exercises = dao.allExercises()
        val workouts = dao.allWorkouts()
        val allSets = workouts.flatMap { dao.setsForWorkout(it.id) }
        val health = dao.allHealth()
        val root = JSONObject()
            .put("format", FORMAT)
            .put("schema_version", VERSION)
            .put("exported_at", java.time.Instant.now().toString())
            .put("units", JSONObject().put("weight", "kg").put("date", "YYYY-MM-DD"))
            .put("locations", JSONArray().apply {
                locations.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            })
            .put("exercises", JSONArray().apply {
                exercises.forEach { put(JSONObject()
                    .put("id", it.id).put("name", it.name)
                    .put("default_increment_kg", it.defaultIncrementKg)
                    .put("muscle_group", it.muscleGroup ?: JSONObject.NULL)
                    .put("is_bodyweight", it.isBodyweight)
                    .put("is_custom", it.isCustom)
                    .put("is_archived", it.isArchived)) }
            })
            .put("workouts", JSONArray().apply {
                workouts.forEach { workout -> put(JSONObject()
                    .put("id", workout.id)
                    .put("date", LocalDate.ofEpochDay(workout.dateEpochDay).toString())
                    .put("location_id", workout.locationId ?: JSONObject.NULL)
                    .put("readiness_score", workout.readinessScore ?: JSONObject.NULL)
                    .put("note", workout.note ?: JSONObject.NULL)) }
            })
            .put("sets", JSONArray().apply {
                allSets.forEach { set -> put(JSONObject()
                    .put("id", set.id).put("workout_id", set.workoutId)
                    .put("exercise_id", set.exerciseId).put("set_number", set.setNumber)
                    .put("weight_kg", set.weightKg).put("reps", set.reps)
                    .put("rpe", set.rpe ?: JSONObject.NULL)
                    .put("equipment", set.equipment ?: JSONObject.NULL)) }
            })
            .put("health", JSONArray().apply {
                health.forEach { day -> put(JSONObject()
                    .put("date", LocalDate.ofEpochDay(day.dateEpochDay).toString())
                    .put("sleep_minutes", day.sleepMinutes ?: JSONObject.NULL)
                    .put("deep_sleep_minutes", day.deepSleepMinutes ?: JSONObject.NULL)
                    .put("rem_sleep_minutes", day.remSleepMinutes ?: JSONObject.NULL)
                    .put("sleep_start_millis", day.sleepStartMillis ?: JSONObject.NULL)
                    .put("sleep_end_millis", day.sleepEndMillis ?: JSONObject.NULL)
                    .put("resting_hr", day.restingHr ?: JSONObject.NULL)
                    .put("baseline_resting_hr", day.baselineRestingHr ?: JSONObject.NULL)
                    .put("avg_hr", day.avgHr ?: JSONObject.NULL)
                    .put("steps", day.steps ?: JSONObject.NULL)
                    .put("active_calories", day.activeCalories ?: JSONObject.NULL)
                    .put("vo2max", day.vo2max ?: JSONObject.NULL)
                    .put("hrv_rmssd", day.hrvRmssd ?: JSONObject.NULL)
                    .put("updated_at", day.updatedAt)) }
            })
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(root.toString(2))
            it.newLine()
        } ?: error("Не удалось открыть файл для записи")
    }

    suspend fun readAndReplace(context: Context, database: WorkoutDatabase, uri: Uri) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Не удалось открыть файл")
        replaceFromText(database, raw)
    }

    /**
     * Merges the bundled diary history into the current database. Existing
     * records stay untouched; exercises and locations are matched by name.
     */
    suspend fun readBundledHistory(context: Context, database: WorkoutDatabase, assetName: String = "history.json") {
        val raw = context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        mergeBackup(database, parseBackup(raw))
    }

    private suspend fun replaceFromText(database: WorkoutDatabase, raw: String) {
        val backup = parseBackup(raw)
        database.withTransaction {
            val dao = database.workoutDao()
            dao.clearSets(); dao.clearWorkouts(); dao.clearExercises(); dao.clearLocations()
            backup.locations.forEach { dao.insertLocation(it) }
            backup.exercises.forEach { dao.insertExercise(it) }
            backup.workouts.forEach { dao.insertWorkout(it) }
            backup.sets.forEach { dao.insertSet(it) }
            mergeHealth(dao, backup.health)
        }
    }

    /** Health rows are keyed by day, so keep the freshest version of each day. */
    private suspend fun mergeHealth(dao: WorkoutDao, incoming: List<HealthSnapshotEntity>) {
        if (incoming.isEmpty()) return
        val existing = dao.allHealth().associateBy { it.dateEpochDay }
        incoming.forEach { day ->
            val current = existing[day.dateEpochDay]
            if (current == null || current.updatedAt <= day.updatedAt) dao.upsertHealth(day)
        }
    }

    private suspend fun mergeBackup(database: WorkoutDatabase, backup: Backup) = database.withTransaction {
        val dao = database.workoutDao()
        val locationIds = mutableMapOf<Long, Long>()
        backup.locations.forEach { location ->
            val existing = dao.locationByName(location.name)
            locationIds[location.id] = existing?.id ?: run {
                dao.insertLocation(LocationEntity(name = location.name))
                checkNotNull(dao.locationByName(location.name)).id
            }
        }
        val exerciseIds = mutableMapOf<Long, Long>()
        backup.exercises.forEach { exercise ->
            val existing = dao.exerciseByName(exercise.name)
            exerciseIds[exercise.id] = existing?.id ?: run {
                dao.insertExercise(ExerciseEntity(
                    name = exercise.name,
                    defaultIncrementKg = exercise.defaultIncrementKg,
                    muscleGroup = exercise.muscleGroup,
                    isBodyweight = exercise.isBodyweight,
                    isCustom = exercise.isCustom,
                    isArchived = exercise.isArchived,
                ))
                checkNotNull(dao.exerciseByName(exercise.name)).id
            }
        }
        backup.workouts.forEach { workout ->
            val locationId = workout.locationId?.let { locationIds[it] }
            val existing = dao.workoutForDate(workout.dateEpochDay)?.takeIf { it.locationId == locationId }
            val workoutId = existing?.id ?: dao.insertWorkout(WorkoutEntity(
                dateEpochDay = workout.dateEpochDay,
                locationId = locationId,
                readinessScore = workout.readinessScore,
                note = workout.note,
            ))
            backup.sets.filter { it.workoutId == workout.id }.forEach { set ->
                val exerciseId = exerciseIds[set.exerciseId] ?: return@forEach
                val nextNumber = dao.maxSetNumber(workoutId, exerciseId) + 1
                dao.insertSet(SetEntity(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    setNumber = nextNumber,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = set.rpe,
                    equipment = set.equipment,
                ))
            }
        }
        mergeHealth(dao, backup.health)
    }

    private data class Backup(
        val locations: List<LocationEntity>,
        val exercises: List<ExerciseEntity>,
        val workouts: List<WorkoutEntity>,
        val sets: List<SetEntity>,
        val health: List<HealthSnapshotEntity>,
    )

    private fun parseBackup(raw: String): Backup {
        require(raw.length <= 20_000_000) { "Файл слишком большой" }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Это не резервная копия IronTrack" }
        require(root.optInt("schema_version", -1) == VERSION) { "Версия JSON не поддерживается" }

        val locations = root.getJSONArray("locations").objects().map { o ->
            LocationEntity(id = o.getLong("id"), name = o.getString("name")).also { require(it.name.isNotBlank()) }
        }
        val exercises = root.getJSONArray("exercises").objects().map { o ->
            ExerciseEntity(
                id = o.getLong("id"), name = o.getString("name"),
                defaultIncrementKg = o.getDouble("default_increment_kg"),
                muscleGroup = o.nullableString("muscle_group"),
                isBodyweight = o.optBoolean("is_bodyweight", false),
                isCustom = o.optBoolean("is_custom", false),
                isArchived = o.optBoolean("is_archived", false),
            ).also { require(it.name.isNotBlank() && it.defaultIncrementKg > 0 && it.defaultIncrementKg.isFinite()) }
        }
        val workouts = root.getJSONArray("workouts").objects().map { o ->
            val day = LocalDate.parse(o.getString("date"))
            val readiness = o.nullableInt("readiness_score")
            require(readiness == null || readiness in 0..100)
            WorkoutEntity(
                id = o.getLong("id"), dateEpochDay = day.toEpochDay(),
                locationId = o.nullableLong("location_id"), readinessScore = readiness,
                note = o.nullableString("note"),
            )
        }
        val sets = root.getJSONArray("sets").objects().map { o ->
            val weight = o.getDouble("weight_kg")
            val reps = o.getInt("reps")
            val rpe = o.nullableDouble("rpe")
            require(weight >= 0 && weight.isFinite() && reps > 0 && (rpe == null || rpe in 1.0..10.0))
            SetEntity(
                id = o.getLong("id"), workoutId = o.getLong("workout_id"),
                exerciseId = o.getLong("exercise_id"), setNumber = o.getInt("set_number"),
                weightKg = weight, reps = reps, rpe = rpe,
                equipment = o.nullableString("equipment"),
            ).also { require(it.setNumber > 0) }
        }
        val health = root.optJSONArray("health")?.objects()?.map { o ->
            HealthSnapshotEntity(
                dateEpochDay = LocalDate.parse(o.getString("date")).toEpochDay(),
                sleepMinutes = o.nullableInt("sleep_minutes"),
                deepSleepMinutes = o.nullableInt("deep_sleep_minutes"),
                remSleepMinutes = o.nullableInt("rem_sleep_minutes"),
                sleepStartMillis = o.nullableLong("sleep_start_millis"),
                sleepEndMillis = o.nullableLong("sleep_end_millis"),
                restingHr = o.nullableInt("resting_hr"),
                baselineRestingHr = o.nullableInt("baseline_resting_hr"),
                avgHr = o.nullableInt("avg_hr"),
                steps = o.nullableInt("steps"),
                activeCalories = o.nullableInt("active_calories"),
                vo2max = o.nullableDouble("vo2max"),
                hrvRmssd = o.nullableDouble("hrv_rmssd"),
                updatedAt = o.optLong("updated_at", 0L),
            )
        }.orEmpty()
        require(locations.map { it.id }.distinct().size == locations.size) { "Повторяются ID залов" }
        require(exercises.map { it.id }.distinct().size == exercises.size) { "Повторяются ID упражнений" }
        require(workouts.map { it.id }.distinct().size == workouts.size) { "Повторяются ID тренировок" }
        require(sets.map { it.id }.distinct().size == sets.size) { "Повторяются ID подходов" }
        val locationIds = locations.map { it.id }.toSet()
        val exerciseIds = exercises.map { it.id }.toSet()
        val workoutIds = workouts.map { it.id }.toSet()
        require(workouts.all { it.locationId == null || it.locationId in locationIds }) { "Ссылка на отсутствующий зал" }
        require(sets.all { it.workoutId in workoutIds && it.exerciseId in exerciseIds }) { "Подход ссылается на отсутствующую тренировку или упражнение" }
        require(sets.groupBy { Triple(it.workoutId, it.exerciseId, it.setNumber) }.values.all { it.size == 1 }) { "Повторяется номер подхода" }
        require(health.map { it.dateEpochDay }.distinct().size == health.size) { "Повторяются дни здоровья" }
        require(health.all { (it.sleepMinutes ?: 0) >= 0 && (it.steps ?: 0) >= 0 }) { "Некорректные данные здоровья" }
        return Backup(locations, exercises, workouts, sets, health)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
