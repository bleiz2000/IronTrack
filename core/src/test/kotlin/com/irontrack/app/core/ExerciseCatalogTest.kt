package com.irontrack.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseCatalogTest {
    @Test fun aliasesShareOneStatisticsKey() {
        val bench = ExerciseCatalog.keyFor("Жим лёжа", isCustom = false, exerciseId = 4)
        assertEquals("bench", bench)
        assertEquals(bench, ExerciseCatalog.keyFor("жим лежа", isCustom = false, exerciseId = 41))
        assertEquals(bench, ExerciseCatalog.keyFor("Жым лёжа", isCustom = false, exerciseId = 77))
    }

    @Test fun customExerciseKeepsItsOwnKey() {
        val key = ExerciseCatalog.keyFor("Тяга в кроссовере", isCustom = true, exerciseId = 120)
        assertEquals("custom:120", key)
    }

    @Test fun unknownHistoricalNameIsNotDropped() {
        val key = ExerciseCatalog.keyFor("Армейский жим", isCustom = false, exerciseId = 9)
        assertEquals("legacy:армейский жим", key)
    }

    @Test fun canonicalNameCleansKnownAliases() {
        assertEquals("Тяга верхнего блока", ExerciseCatalog.canonicalName("жим верхнего блока"))
        assertEquals("Сгибания на бицепс", ExerciseCatalog.canonicalName("Бицепс"))
        assertNull(ExerciseCatalog.groupForName("Космический жим"))
    }

    @Test fun catalogNamesAreUnique() {
        val names = ExerciseCatalog.groups.map { ExerciseCatalog.normalizedName(it.name) }
        assertEquals(names.size, names.distinct().size)
        assertTrue(ExerciseCatalog.groups.all { it.incrementKg > 0 })
    }
}
