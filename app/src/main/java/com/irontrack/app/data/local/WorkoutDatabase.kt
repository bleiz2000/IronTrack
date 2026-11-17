package com.irontrack.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocationEntity::class, ExerciseEntity::class, WorkoutEntity::class, SetEntity::class, HealthSnapshotEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "workout-tracker.db"

        val ALL_MIGRATIONS: Array<Migration> by lazy {
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        }

        /** Single place that builds the database, shared with the background sync job. */
        fun create(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context.applicationContext, WorkoutDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isBodyweight INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN sleepStartMillis INTEGER")
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN sleepEndMillis INTEGER")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_snapshots (
                        dateEpochDay INTEGER NOT NULL,
                        sleepMinutes INTEGER,
                        deepSleepMinutes INTEGER,
                        remSleepMinutes INTEGER,
                        restingHr INTEGER,
                        baselineRestingHr INTEGER,
                        avgHr INTEGER,
                        steps INTEGER,
                        activeCalories INTEGER,
                        vo2max REAL,
                        hrvRmssd REAL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(dateEpochDay)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
