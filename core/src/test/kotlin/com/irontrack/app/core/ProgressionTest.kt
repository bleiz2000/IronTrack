package com.irontrack.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionTest {
    @Test fun epleyEstimate() {
        assertEquals(80.0, Progression.epley1Rm(60.0, 10), 0.0001)
    }

    @Test fun singleIsItsOwnOneRepMax() {
        assertEquals(80.0, Progression.epley1Rm(80.0, 1), 0.0001)
    }

    @Test fun readinessUsesSleepAndRestingHeartRate() {
        val result = Progression.readinessFromHealth(
            sleepMinutes = 480,
            deepSleepMinutes = 90,
            restingHr = 55,
            baselineRestingHr = 55,
            hoursSinceLastWorkout = 48.0,
        )
        assertEquals(100, result.score)
        assertEquals("Восстановление в норме", result.reason)
    }

    @Test fun poorSleepAndHighPulseLowerReadiness() {
        val result = Progression.readinessFromHealth(
            sleepMinutes = 240,
            deepSleepMinutes = 30,
            restingHr = 70,
            baselineRestingHr = 55,
            hoursSinceLastWorkout = 12.0,
        )
        assertEquals(true, result.score != null && result.score!! < 50)
    }

    @Test fun noHealthDataIsNotGuessed() {
        val result = Progression.readinessFromHealth(
            sleepMinutes = null,
            deepSleepMinutes = null,
            restingHr = null,
            baselineRestingHr = null,
            hoursSinceLastWorkout = 48.0,
        )
        assertNull(result.score)
    }

    @Test fun goodSetProgressesLoad() {
        val result = Progression.recommend(60.0, 8, 8, 7.5, 90, 2.5)
        assertEquals(62.5, result.weightKg)
        assertEquals(RecommendationReason.TARGET_MET_WITH_RESERVE, result.reason)
    }

    @Test fun lowReadinessReducesLoad() {
        val result = Progression.recommend(60.0, 8, 8, 7.0, 30, 2.5)
        assertEquals(57.5, result.weightKg)
        assertEquals(RecommendationReason.LOW_READINESS_REDUCE, result.reason)
    }

    @Test fun noReadinessHoldsLoad() {
        val result = Progression.recommend(60.0, 8, 8, 7.0, null, 2.5)
        assertEquals(60.0, result.weightKg)
        assertEquals(RecommendationReason.NO_RECOVERY_DATA, result.reason)
    }

    @Test fun reserveBelowTargetAddsRepRatherThanWeight() {
        val result = Progression.recommend(60.0, 7, 8, 8.0, 85, 2.5)
        assertEquals(60.0, result.weightKg)
        assertEquals(8, result.targetReps)
        assertEquals(RecommendationReason.ADD_REPETITION, result.reason)
    }
}
