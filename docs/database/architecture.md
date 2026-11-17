# Android data and calculation foundation

## Database (schema v6)

- `locations`: gym/location dictionary; workout location is nullable.
- `exercises`: canonical names, increment, optional muscle group, `isBodyweight`, `isCustom`, `isArchived`. The working pool is seeded from `ExerciseCatalog`; old imported names stay in the database for history but are hidden from the picker.
- `workouts`: one session per local date and gym, optional readiness score.
- `sets`: one set with required workout/exercise keys, kg load, reps, optional RPE, equipment. Unique set numbering per workout/exercise.
- `health_snapshots`: one row per day with sleep, phases, resting HR, baseline resting HR, average HR, steps, calories, VO2max and HRV from Health Connect.

All stored loads are kilograms. The importer converts pounds explicitly. Historical exercise names are grouped through `ExerciseCatalog` aliases for statistics, so "Бицепс", "Гантели на бицепс" and "Бицепс изол" count as one exercise.

## Progression

`core/Progression.kt` is pure Kotlin. Epley is used for 2–30 reps; a single rep returns the lifted weight itself. Readiness combines sleep duration, resting heart rate versus a 7-day baseline, and hours since the previous workout. A missing score is `null`, never a fabricated 100. The load recommendation holds when readiness is unknown.

## Health Connect

`health/HealthRepository.kt` reads sleep sessions and phases, resting heart rate, heart-rate samples, steps, active calories, VO2max and HRV. Mi Fitness is not called directly; the user enables its Health Connect sync. Training Load has no standard Health Connect record, so the app reports its own 7-day tonnage and VO2max when present.
