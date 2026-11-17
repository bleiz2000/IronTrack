package com.irontrack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: SetEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Query("SELECT s.* FROM sets s JOIN workouts w ON s.workoutId = w.id WHERE s.exerciseId = :exerciseId AND s.workoutId != :excludingWorkoutId ORDER BY w.dateEpochDay DESC, w.id DESC, s.setNumber DESC LIMIT :limit")
    suspend fun recentSets(exerciseId: Long, excludingWorkoutId: Long, limit: Int = 10): List<SetEntity>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId AND exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun setsForWorkoutExercise(workoutId: Long, exerciseId: Long): List<SetEntity>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    suspend fun setsForWorkout(workoutId: Long): List<SetEntity>

    @Query("UPDATE workouts SET readinessScore = :readiness WHERE id = :workoutId")
    suspend fun updateReadiness(workoutId: Long, readiness: Int)

    @Query("UPDATE sets SET weightKg = :weightKg, reps = :reps, rpe = :rpe WHERE id = :id")
    suspend fun updateSet(id: Long, weightKg: Double, reps: Int, rpe: Double?)

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE dateEpochDay = :epochDay ORDER BY id DESC LIMIT 1")
    suspend fun workoutForDate(epochDay: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC, id DESC")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun exerciseByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM locations WHERE name = :name LIMIT 1")
    suspend fun locationByName(name: String): LocationEntity?

    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    suspend fun allLocations(): List<LocationEntity>

    @Query("SELECT * FROM sets")
    suspend fun allSets(): List<SetEntity>

    @Query("SELECT s.*, w.dateEpochDay AS dateEpochDay FROM sets s JOIN workouts w ON w.id = s.workoutId WHERE s.exerciseId = :exerciseId ORDER BY w.dateEpochDay DESC, s.setNumber DESC LIMIT :limit")
    suspend fun setsWithDates(exerciseId: Long, limit: Int): List<SetWithDate>

    @Query("SELECT s.*, w.dateEpochDay AS dateEpochDay FROM sets s JOIN workouts w ON w.id = s.workoutId ORDER BY w.dateEpochDay DESC, s.setNumber DESC")
    suspend fun allSetsWithDates(): List<SetWithDate>

    @Query("UPDATE exercises SET isCustom = 1 WHERE id = :id")
    suspend fun markCustom(id: Long)

    @Query("UPDATE exercises SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COUNT(*) FROM sets WHERE exerciseId = :exerciseId")
    suspend fun setCountForExercise(exerciseId: Long): Int

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("DELETE FROM sets WHERE exerciseId = :id")
    suspend fun deleteSetsForExercise(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHealth(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots ORDER BY dateEpochDay DESC LIMIT 1")
    suspend fun latestHealth(): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots ORDER BY dateEpochDay")
    suspend fun allHealth(): List<HealthSnapshotEntity>

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM sets WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun maxSetNumber(workoutId: Long, exerciseId: Long): Int

    @Query("DELETE FROM sets") suspend fun clearSets()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM locations") suspend fun clearLocations()
    @Query("DELETE FROM health_snapshots") suspend fun clearHealth()
}
