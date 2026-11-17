package com.irontrack.app.core

/**
 * The working exercise pool and the aliases found in the old diary.
 * Historical variants are merged into one group for statistics, while the
 * picker shows one clean entry per group.
 */
data class ExerciseGroup(
    val key: String,
    val name: String,
    val incrementKg: Double,
    val muscleGroup: String?,
    val aliases: Set<String>,
)

object ExerciseCatalog {
    private fun normalize(value: String): String =
        value.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^a-zа-я0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    val groups: List<ExerciseGroup> = listOf(
        ExerciseGroup(
            key = "bench",
            name = "Жим лёжа",
            incrementKg = 2.5,
            muscleGroup = "chest",
            aliases = setOf("жим лежа", "жим штанги лежа", "жым лежа", "жыс лежа", "дым лежа", "жим штанги лёжа"),
        ),
        ExerciseGroup(
            key = "incline_db",
            name = "Жим гантелей на наклонной",
            incrementKg = 2.5,
            muscleGroup = "chest",
            aliases = setOf("гантели на наклонной", "жим гантелей лёжа на наклонной скамье", "жим гантелей наклон", "жим гантелей на наклонной скамье", "жим в наклоне", "дым лёжа наклон", "жым гантелей наклон"),
        ),
        ExerciseGroup(
            key = "pec_deck",
            name = "Бабочка",
            incrementKg = 2.5,
            muscleGroup = "chest",
            aliases = setOf("бабочка", "крылья", "крыялья", "разводка", "разводка плеч"),
        ),
        ExerciseGroup(
            key = "lat_pulldown",
            name = "Тяга верхнего блока",
            incrementKg = 2.5,
            muscleGroup = "back",
            aliases = setOf("жим верхнего блока", "тяга военного блока", "верхний блок", "тяга блока"),
        ),
        ExerciseGroup(
            key = "barbell_row",
            name = "Тяга штанги к поясу",
            incrementKg = 2.5,
            muscleGroup = "back",
            aliases = setOf("тяга штанги по поясу стоя", "тяга к поясу", "тяга штанги к поясу в смите", "тяга к поясу смить", "тяга штанги в спине"),
        ),
        ExerciseGroup(
            key = "db_row",
            name = "Тяга гантели в наклоне",
            incrementKg = 2.5,
            muscleGroup = "back",
            aliases = setOf("гантели в наклоне", "тяга гантели", "тяга гантелей в наклоне"),
        ),
        ExerciseGroup(
            key = "biceps",
            name = "Сгибания на бицепс",
            incrementKg = 1.25,
            muscleGroup = "biceps",
            aliases = setOf("бицепс", "бицеп", "гантели на бицепс", "бицепс изол", "бицепс тяга", "бицепс штанга", "бицепс скамя", "бицепс на скаме", "бицепс тренажёр"),
        ),
        ExerciseGroup(
            key = "hammer",
            name = "Молотки",
            incrementKg = 1.25,
            muscleGroup = "biceps",
            aliases = setOf("молоток", "малаток"),
        ),
        ExerciseGroup(
            key = "preacher",
            name = "Скамья Скотта",
            incrementKg = 1.25,
            muscleGroup = "biceps",
            aliases = setOf("скамья скотта", "тренажёр скотта", "тренажер скотта", "тренажёр скота", "тренажер скота", "скамья скота", "скотт", "скот"),
        ),
        ExerciseGroup(
            key = "triceps",
            name = "Разгибания на трицепс",
            incrementKg = 2.5,
            muscleGroup = "triceps",
            aliases = setOf("трицепс", "трицеп", "трицепс тяга", "тяга трицепс", "трицепс изол", "трицепс одной рукой", "трицепс тяга одной рукой", "гантели на трицепс", "тяга на трицепс", "трицепс тяга вниз"),
        ),
        ExerciseGroup(
            key = "french",
            name = "Французский жим",
            incrementKg = 1.25,
            muscleGroup = "triceps",
            aliases = setOf("французский", "французский жим"),
        ),
        ExerciseGroup(
            key = "lateral",
            name = "Махи гантелями в стороны",
            incrementKg = 1.25,
            muscleGroup = "shoulders",
            aliases = setOf("махи", "махи гантелями", "махи в стороны", "разведения гантелей", "махи на дельты"),
        ),
    )

    private val index: Map<String, ExerciseGroup> = buildMap {
        groups.forEach { group ->
            put(normalize(group.name), group)
            group.aliases.forEach { alias -> put(normalize(alias), group) }
        }
    }

    fun groupForName(name: String): ExerciseGroup? = index[normalize(name)]

    fun canonicalName(name: String): String = groupForName(name)?.name ?: name.trim()

    fun normalizedName(name: String): String = normalize(name)

    /**
     * Stable statistics key. Catalog exercises share one key across historical
     * aliases, custom exercises are keyed by id, and any remaining historical
     * name keeps its own group so old training is not silently dropped.
     */
    fun keyFor(name: String, isCustom: Boolean, exerciseId: Long): String =
        groupForName(name)?.key
            ?: if (isCustom) "custom:$exerciseId" else "legacy:${normalize(name)}"
}
