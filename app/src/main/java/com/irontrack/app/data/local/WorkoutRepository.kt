package com.irontrack.app.data.local

import androidx.room.withTransaction
import com.irontrack.app.core.ExerciseCatalog
import java.time.LocalDate

class WorkoutRepository(private val database: WorkoutDatabase) {
    private val dao = database.workoutDao()

    suspend fun ensureExercise(name: String, incrementKg: Double = 2.5, muscleGroup: String? = null, bodyweight: Boolean = false): ExerciseEntity =
        database.withTransaction {
            require(name.isNotBlank() && incrementKg > 0 && incrementKg.isFinite())
            dao.exerciseByName(name) ?: run {
                dao.insertExercise(ExerciseEntity(name = name, defaultIncrementKg = incrementKg, muscleGroup = muscleGroup, isBodyweight = bodyweight))
                checkNotNull(dao.exerciseByName(name))
            }
        }

    /** Ensure the working pool exists, even after a backup import. */
    suspend fun seedDefaults() = database.withTransaction {
        ExerciseCatalog.groups.forEach { group ->
            if (dao.exerciseByName(group.name) == null) {
                dao.insertExercise(ExerciseEntity(
                    name = group.name,
                    defaultIncrementKg = group.incrementKg,
                    muscleGroup = group.muscleGroup,
                ))
            }
        }
    }

    suspend fun addCustomExercise(name: String, incrementKg: Double): ExerciseEntity =
        database.withTransaction {
            val canonical = ExerciseCatalog.canonicalName(name)
            require(canonical.isNotBlank() && incrementKg > 0 && incrementKg.isFinite())
            val existing = dao.exerciseByName(canonical)
            if (existing != null) {
                if (!existing.isCustom && ExerciseCatalog.groupForName(existing.name) == null) {
                    dao.markCustom(existing.id)
                }
                checkNotNull(dao.exerciseByName(canonical))
            } else {
                dao.insertExercise(ExerciseEntity(
                    name = canonical,
                    defaultIncrementKg = incrementKg,
                    muscleGroup = null,
                    isCustom = true,
                ))
                checkNotNull(dao.exerciseByName(canonical))
            }
        }

    suspend fun hasHistory(): Boolean = dao.allSets().isNotEmpty()

    suspend fun upsertHealth(snapshot: HealthSnapshotEntity) = dao.upsertHealth(snapshot)

    suspend fun latestHealth(): HealthSnapshotEntity? = dao.latestHealth()

    suspend fun setExerciseArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    /**
     * Removes an exercise with no recorded sets. If it already has history the
     * row stays, but the exercise is hidden from the picker so stats survive.
     * Returns true when the row was actually deleted.
     */
    suspend fun deleteOrHideExercise(id: Long): Boolean = database.withTransaction {
        if (dao.setCountForExercise(id) > 0) {
            dao.setArchived(id, true)
            false
        } else {
            dao.deleteExercise(id)
            true
        }
    }

    /** Removes the exercise and every recorded set that belongs to it. */
    suspend fun deleteExerciseWithHistory(id: Long) = database.withTransaction {
        dao.deleteSetsForExercise(id)
        dao.deleteExercise(id)
    }

    suspend fun updateSet(id: Long, weightKg: Double, reps: Int, rpe: Double?) {
        require(weightKg >= 0 && weightKg.isFinite())
        require(reps > 0)
        require(rpe == null || rpe in 1.0..10.0)
        dao.updateSet(id, weightKg, reps, rpe)
    }

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun startWorkout(date: LocalDate = LocalDate.now(), locationName: String? = null, readiness: Int? = null): Long =
        database.withTransaction {
            require(readiness == null || readiness in 0..100)
            val locationId = locationName?.let { name ->
                dao.locationByName(name)?.id ?: run {
                    dao.insertLocation(LocationEntity(name = name))
                    checkNotNull(dao.locationByName(name)).id
                }
            }
            val existing = dao.workoutForDate(date.toEpochDay())
            if (existing != null && (locationName == null || existing.locationId == locationId)) {
                return@withTransaction existing.id
            }
            dao.insertWorkout(WorkoutEntity(
                dateEpochDay = date.toEpochDay(),
                locationId = locationId,
                readinessScore = readiness,
            ))
        }

    suspend fun updateReadiness(workoutId: Long, readiness: Int) {
        require(readiness in 0..100)
        dao.updateReadiness(workoutId, readiness)
    }

    suspend fun recordSet(
        workoutId: Long,
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        equipment: String? = null,
    ): Long = database.withTransaction {
        require(weightKg >= 0 && weightKg.isFinite())
        require(reps > 0)
        require(rpe == null || rpe in 1.0..10.0)
        val nextNumber = dao.maxSetNumber(workoutId, exerciseId) + 1
        dao.insertSet(SetEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setNumber = nextNumber,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            equipment = equipment,
        ))
    }

    suspend fun recentSets(exerciseId: Long, excludingWorkoutId: Long, limit: Int = 10) =
        dao.recentSets(exerciseId, excludingWorkoutId, limit)

    suspend fun setsForWorkoutExercise(workoutId: Long, exerciseId: Long) =
        dao.setsForWorkoutExercise(workoutId, exerciseId)

    suspend fun workouts() = dao.allWorkouts()
    suspend fun exercises() = dao.allExercises()
    suspend fun locations() = dao.allLocations()
    suspend fun allSets() = dao.allSets()
    suspend fun allSetsWithDates() = dao.allSetsWithDates()
    suspend fun setsWithDates(exerciseId: Long, limit: Int = 40) = dao.setsWithDates(exerciseId, limit)
    suspend fun setsForWorkout(workoutId: Long) = dao.setsForWorkout(workoutId)

    suspend fun replaceAllFromImport(
        locations: List<LocationEntity>,
        exercises: List<ExerciseEntity>,
        workouts: List<WorkoutEntity>,
        sets: List<SetEntity>,
    ) = database.withTransaction {
        dao.clearSets()
        dao.clearWorkouts()
        dao.clearExercises()
        dao.clearLocations()
        locations.forEach { dao.insertLocation(it) }
        exercises.forEach { dao.insertExercise(it) }
        workouts.forEach { dao.insertWorkout(it) }
        sets.forEach { dao.insertSet(it) }
    }
}
