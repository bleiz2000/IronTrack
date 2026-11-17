PRAGMA foreign_keys = ON;

CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE exercises (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    defaultIncrementKg REAL NOT NULL,
    muscleGroup TEXT,
    isBodyweight INTEGER NOT NULL DEFAULT 0,
    isCustom INTEGER NOT NULL DEFAULT 0,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE workouts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dateEpochDay INTEGER NOT NULL,
    locationId INTEGER REFERENCES locations(id) ON DELETE SET NULL,
    readinessScore INTEGER CHECK (readinessScore IS NULL OR readinessScore BETWEEN 0 AND 100),
    note TEXT
);
CREATE INDEX index_workouts_locationId ON workouts(locationId);
CREATE INDEX index_workouts_dateEpochDay ON workouts(dateEpochDay);

CREATE TABLE sets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    workoutId INTEGER NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exerciseId INTEGER NOT NULL REFERENCES exercises(id) ON DELETE RESTRICT,
    setNumber INTEGER NOT NULL CHECK (setNumber > 0),
    weightKg REAL NOT NULL CHECK (weightKg >= 0),
    reps INTEGER NOT NULL CHECK (reps > 0),
    rpe REAL CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    equipment TEXT,
    UNIQUE(workoutId, exerciseId, setNumber)
);
CREATE INDEX index_sets_workoutId ON sets(workoutId);
CREATE INDEX index_sets_exerciseId ON sets(exerciseId);

CREATE TABLE health_snapshots (
    dateEpochDay INTEGER PRIMARY KEY,
    sleepMinutes INTEGER,
    deepSleepMinutes INTEGER,
    remSleepMinutes INTEGER,
    sleepStartMillis INTEGER,
    sleepEndMillis INTEGER,
    restingHr INTEGER,
    baselineRestingHr INTEGER,
    avgHr INTEGER,
    steps INTEGER,
    activeCalories INTEGER,
    vo2max REAL,
    hrvRmssd REAL,
    updatedAt INTEGER NOT NULL
);
