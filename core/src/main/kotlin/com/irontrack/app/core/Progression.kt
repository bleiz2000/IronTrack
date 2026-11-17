package com.irontrack.app.core

import kotlin.math.round
import kotlin.math.roundToInt

/** Pure local calculations. All lifting loads are kilograms unless otherwise stated. */
object Progression {
    /** Epley estimate. Conventionally meaningful for 2..30 reps; a single is the 1RM itself. */
    fun epley1Rm(weightKg: Double, reps: Int): Double {
        require(weightKg > 0.0 && weightKg.isFinite()) { "weightKg must be a positive finite value" }
        require(reps in 1..30) { "Epley estimate is supported for 1..30 reps" }
        if (reps == 1) return weightKg
        return weightKg * (1.0 + reps / 30.0)
    }

    data class ReadinessResult(val score: Int?, val reason: String)

    /**
     * Readiness from Health Connect / Mi Fitness data. Sleep and resting heart
     * rate are compared with the user's own sleep target and recent baseline.
     * If both are missing the score is null, never a made-up 100.
     */
    fun readinessFromHealth(
        sleepMinutes: Int?,
        deepSleepMinutes: Int?,
        restingHr: Int?,
        baselineRestingHr: Int?,
        hoursSinceLastWorkout: Double,
        targetSleepMinutes: Int = 480,
    ): ReadinessResult {
        require(targetSleepMinutes > 0)
        val recoveryScore = (hoursSinceLastWorkout / 48.0).coerceIn(0.0, 1.0)
        val sleepScore = sleepMinutes?.let { (it.toDouble() / targetSleepMinutes).coerceIn(0.0, 1.0) }
        val rhrDelta = if (restingHr != null && baselineRestingHr != null) restingHr - baselineRestingHr else null
        val rhrScore = rhrDelta?.let { (1.0 - it / 20.0).coerceIn(0.0, 1.0) }

        val score = when {
            sleepScore != null && rhrScore != null ->
                (100 * (0.55 * sleepScore + 0.25 * rhrScore + 0.20 * recoveryScore)).roundToInt()
            sleepScore != null ->
                (100 * (0.75 * sleepScore + 0.25 * recoveryScore)).roundToInt()
            rhrScore != null ->
                (100 * (0.55 * rhrScore + 0.45 * recoveryScore)).roundToInt()
            else -> null
        }

        val reason = when {
            sleepScore == null && rhrScore == null -> "Нет данных о сне и пульсе"
            sleepScore == null -> "Нет данных о сне"
            rhrScore == null -> "Нет базового пульса покоя"
            (sleepMinutes ?: targetSleepMinutes) < 330 -> "Мало сна"
            rhrDelta != null && rhrDelta > 5 -> "Пульс покоя выше нормы"
            recoveryScore < 0.5 -> "Мало времени после прошлой тренировки"
            (deepSleepMinutes ?: 0) in 1..59 -> "Мало глубокого сна"
            else -> "Восстановление в норме"
        }
        return ReadinessResult(score, reason)
    }

    /**
     * Recommends a next-session load/rep target using prior set performance.
     * “In reserve” is represented by RPE <= 8 (at least ~2 reps in reserve).
     * With no health data the app holds the load instead of guessing.
     */
    fun recommend(
        previousWeightKg: Double,
        previousReps: Int,
        targetReps: Int,
        rpe: Double?,
        readinessScore: Int?,
        incrementKg: Double,
        minIncrementKg: Double = 1.25,
        maxIncrementKg: Double = 2.5,
    ): LoadRecommendation {
        require(previousWeightKg > 0 && previousWeightKg.isFinite())
        require(previousReps > 0 && targetReps > 0)
        require(readinessScore == null || readinessScore in 0..100)
        require(incrementKg > 0 && incrementKg.isFinite())
        require(minIncrementKg > 0 && maxIncrementKg >= minIncrementKg)
        require(rpe == null || rpe in 1.0..10.0)

        val step = incrementKg.coerceIn(minIncrementKg, maxIncrementKg)
        if (readinessScore == null) {
            return LoadRecommendation(previousWeightKg, targetReps, RecommendationReason.NO_RECOVERY_DATA)
        }
        if (readinessScore < 40) {
            return LoadRecommendation(
                roundToStep((previousWeightKg - step).coerceAtLeast(0.0), step),
                targetReps,
                RecommendationReason.LOW_READINESS_REDUCE,
            )
        }
        if (readinessScore < 70) {
            return LoadRecommendation(previousWeightKg, targetReps, RecommendationReason.MODERATE_READINESS_HOLD)
        }

        val reserve = rpe != null && rpe <= 8.0
        val metTarget = previousReps >= targetReps
        if (metTarget && reserve) {
            return LoadRecommendation(previousWeightKg + step, targetReps, RecommendationReason.TARGET_MET_WITH_RESERVE)
        }
        if (previousReps < targetReps && reserve) {
            return LoadRecommendation(
                previousWeightKg,
                (previousReps + 1).coerceAtMost(targetReps),
                RecommendationReason.ADD_REPETITION,
            )
        }
        return LoadRecommendation(previousWeightKg, targetReps, RecommendationReason.HOLD_NO_RESERVE)
    }

    private fun roundToStep(value: Double, step: Double): Double = round(value / step) * step
}

data class LoadRecommendation(
    val weightKg: Double,
    val targetReps: Int,
    val reason: RecommendationReason,
)

enum class RecommendationReason {
    NO_RECOVERY_DATA,
    LOW_READINESS_REDUCE,
    MODERATE_READINESS_HOLD,
    TARGET_MET_WITH_RESERVE,
    ADD_REPETITION,
    HOLD_NO_RESERVE,
}
