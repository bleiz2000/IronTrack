package com.irontrack.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "locations", indices = [Index(value = ["name"], unique = true)])
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Default load step in kg; equipment-specific overrides can be added later. */
    val defaultIncrementKg: Double,
    val muscleGroup: String? = null,
    val isBodyweight: Boolean = false,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "workouts",
    foreignKeys = [ForeignKey(
        entity = LocationEntity::class,
        parentColumns = ["id"], childColumns = ["locationId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("locationId"), Index("dateEpochDay")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local-date epoch day avoids timezone shifting historical diary dates. */
    val dateEpochDay: Long,
    val locationId: Long? = null,
    val readinessScore: Int? = null,
    val note: String? = null,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workoutId"), Index("exerciseId"), Index(value = ["workoutId", "exerciseId", "setNumber"], unique = true)],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val equipment: String? = null,
)

data class SetWithDate(
    @Embedded val set: SetEntity,
    val dateEpochDay: Long,
)

@Entity(tableName = "health_snapshots")
data class HealthSnapshotEntity(
    @PrimaryKey val dateEpochDay: Long,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val sleepStartMillis: Long? = null,
    val sleepEndMillis: Long? = null,
    val restingHr: Int? = null,
    val baselineRestingHr: Int? = null,
    val avgHr: Int? = null,
    val steps: Int? = null,
    val activeCalories: Int? = null,
    val vo2max: Double? = null,
    val hrvRmssd: Double? = null,
    val updatedAt: Long,
)
