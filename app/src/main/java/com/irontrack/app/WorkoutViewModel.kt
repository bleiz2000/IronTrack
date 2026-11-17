package com.irontrack.app

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.irontrack.app.core.LoadRecommendation
import com.irontrack.app.core.Progression
import com.irontrack.app.core.ExerciseCatalog
import com.irontrack.app.data.local.ExerciseEntity
import com.irontrack.app.data.local.HealthSnapshotEntity
import com.irontrack.app.data.local.SetEntity
import com.irontrack.app.data.local.SetWithDate
import com.irontrack.app.data.local.WorkoutRepository
import com.irontrack.app.health.HealthRepository
import com.irontrack.app.health.HealthSyncScheduler
import com.irontrack.app.health.HealthStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExerciseOption(
    val id: Long,
    val name: String,
    val incrementKg: Double,
    val bodyweight: Boolean,
    val isCustom: Boolean = false,
    val archived: Boolean = false,
    val setCount: Int = 0,
)

data class ProgressPoint(val date: LocalDate, val e1rm: Double)

data class ExerciseTrend(
    val key: String,
    val name: String,
    val firstE1rm: Double,
    val lastE1rm: Double,
    val lastDate: LocalDate,
    val workoutCount: Int,
    val peakWeightKg: Double,
    val peakE1rm: Double,
    val points: List<ProgressPoint>,
) {
    val changeKg: Double get() = lastE1rm - firstE1rm
}

data class StatsUi(
    val totalWorkouts: Int = 0,
    val last30Days: Int = 0,
    val lastWorkout: LocalDate? = null,
    val tonnage7d: Double = 0.0,
    val trends: List<ExerciseTrend> = emptyList(),
)

data class HealthUi(
    val available: Boolean = false,
    val connected: Boolean = false,
    val providerUpdateRequired: Boolean = false,
    val grantedCount: Int = 0,
    val requiredCount: Int = 0,
    val backgroundGranted: Boolean = false,
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
    val updatedAt: Long? = null,
    val error: String? = null,
)

data class WorkoutUiState(
    val exercises: List<ExerciseOption> = emptyList(),
    val locations: List<String> = emptyList(),
    val selectedLocation: String? = null,
    val workoutId: Long? = null,
    val exerciseId: Long? = null,
    val exerciseName: String = "Жим лёжа",
    val incrementKg: Double = 2.5,
    val isBodyweight: Boolean = false,
    val sets: List<SetEntity> = emptyList(),
    val history: List<SetEntity> = emptyList(),
    val weightText: String = "60",
    val repsText: String = "8",
    val rpeText: String = "8",
    val readiness: Int? = null,
    val readinessReason: String = "Нет данных о восстановлении",
    val health: HealthUi = HealthUi(),
    val recommendation: LoadRecommendation? = null,
    val stats: StatsUi = StatsUi(),
    val statsExerciseKey: String? = null,
    val statsSets: List<SetWithDate> = emptyList(),
    val lastSaved: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class WorkoutViewModel(
    private val appContext: Context,
    private val repository: WorkoutRepository,
    private val healthRepository: HealthRepository,
    private val bundledImporter: suspend () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    init { initialize() }

    private fun initialize() = viewModelScope.launch {
        runCatching {
            runCatching { bundledImporter() }
            repository.seedDefaults()
            val exercises = mainExercises()
            val locations = repository.locations().map { it.name }
            val active = exercises.filterNot { it.archived }
            val selected = active.firstOrNull { it.name == "Жим лёжа" } ?: active.firstOrNull()
            _state.value = _state.value.copy(exercises = exercises, locations = locations)
            loadHealth()
            if (selected != null) selectExerciseInternal(selected)
            reloadStats()
            refreshRecommendation()
        }.onFailure { update { copy(error = it.message ?: "Не удалось открыть тренировку") } }
    }

    private suspend fun mainExercises(): List<ExerciseOption> {
        val all = repository.exercises()
        val counts = repository.allSets().groupingBy { it.exerciseId }.eachCount()
        val byName = all.associateBy { it.name }
        val defaults = ExerciseCatalog.groups.mapNotNull { group ->
            byName[group.name]?.toOption()?.copy(setCount = counts[byName[group.name]!!.id] ?: 0)
        }
        val custom = all.filter { it.isCustom && ExerciseCatalog.groupForName(it.name) == null }
            .sortedBy { it.name }
            .map { it.toOption().copy(setCount = counts[it.id] ?: 0) }
        return defaults + custom
    }

    fun setExerciseArchived(exerciseId: Long, archived: Boolean) = viewModelScope.launch {
        runCatching {
            repository.setExerciseArchived(exerciseId, archived)
            applyExerciseList()
            if (archived && _state.value.exerciseId == exerciseId) {
                selectFallbackExercise()
            }
            if (!archived) _state.value.exercises.firstOrNull { it.id == exerciseId }?.let {
                selectExerciseInternal(it)
            }
            showMessage(if (archived) "Упражнение скрыто" else "Упражнение вернулось в список")
        }.onFailure { report(it) }
    }

    fun deleteExerciseWithHistory(exerciseId: Long) = viewModelScope.launch {
        runCatching {
            repository.deleteExerciseWithHistory(exerciseId)
            applyExerciseList()
            if (_state.value.exerciseId == exerciseId) selectFallbackExercise()
            reloadStats()
            showMessage("Упражнение и его история удалены")
        }.onFailure { report(it) }
    }

    fun deleteExercise(exerciseId: Long) = viewModelScope.launch {
        runCatching {
            val deleted = repository.deleteOrHideExercise(exerciseId)
            applyExerciseList()
            if (_state.value.exerciseId == exerciseId) selectFallbackExercise()
            showMessage(if (deleted) "Упражнение удалено" else "Упражнение скрыто: в истории есть подходы")
        }.onFailure { report(it) }
    }

    private suspend fun applyExerciseList() {
        _state.value = _state.value.copy(exercises = mainExercises())
    }

    private suspend fun selectFallbackExercise() {
        val options = _state.value.exercises.filterNot { it.archived }
        val fallback = options.firstOrNull { it.name == "Жим лёжа" } ?: options.firstOrNull()
        if (fallback == null) {
            _state.value = _state.value.copy(exerciseId = null, sets = emptyList(), history = emptyList())
        } else {
            selectExerciseInternal(fallback)
        }
    }

    fun addCustomExercise(name: String, incrementText: String) = viewModelScope.launch {
        val increment = incrementText.replace(',', '.').toDoubleOrNull() ?: 2.5
        if (name.isBlank() || increment <= 0 || !increment.isFinite()) {
            showError("Укажите название и шаг веса больше нуля")
            return@launch
        }
        runCatching {
            val exercise = repository.addCustomExercise(name, increment)
            repository.seedDefaults()
            val options = mainExercises()
            _state.value = _state.value.copy(exercises = options)
            val option = options.firstOrNull { it.id == exercise.id } ?: exercise.toOption()
            selectExerciseInternal(option)
            showMessage("Упражнение добавлено")
        }.onFailure { report(it) }
    }

    fun selectExercise(exerciseId: Long) {
        val option = _state.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        viewModelScope.launch { runCatching { selectExerciseInternal(option) }.onFailure { report(it) } }
    }

    fun selectLocation(location: String?) {
        viewModelScope.launch {
            runCatching {
                val readiness = _state.value.readiness
                val workoutId = repository.startWorkout(
                    LocalDate.now(),
                    locationName = location,
                    readiness = readiness,
                )
                readiness?.let { repository.updateReadiness(workoutId, it) }
                _state.value = _state.value.copy(
                    selectedLocation = location,
                    workoutId = workoutId,
                    locations = repository.locations().map { it.name },
                )
                val option = currentOption()
                if (option != null) loadExerciseContext(option)
            }.onFailure { report(it) }
        }
    }

    fun selectStatsExercise(key: String) {
        _state.value = _state.value.copy(statsExerciseKey = key)
        viewModelScope.launch { runCatching { loadStatsSetsFor(key) }.onFailure { report(it) } }
    }

    private suspend fun selectExerciseInternal(option: ExerciseOption) {
        val readiness = _state.value.readiness
        val workoutId = _state.value.workoutId
            ?: repository.startWorkout(LocalDate.now(), readiness = readiness)
        readiness?.let { repository.updateReadiness(workoutId, it) }
        _state.value = _state.value.copy(workoutId = workoutId)
        loadExerciseContext(option)
    }

    private suspend fun loadExerciseContext(option: ExerciseOption) {
        val workoutId = _state.value.workoutId ?: return
        val history = repository.recentSets(option.id, excludingWorkoutId = workoutId)
        val sets = repository.setsForWorkoutExercise(workoutId, option.id)
        val last = sets.lastOrNull() ?: history.firstOrNull()
        _state.value = _state.value.copy(
            exerciseId = option.id,
            exerciseName = option.name,
            incrementKg = option.incrementKg,
            isBodyweight = option.bodyweight,
            history = history,
            sets = sets,
            weightText = last?.weightKg?.let { formatWeight(it) } ?: "0",
            repsText = last?.reps?.toString() ?: "8",
            lastSaved = false,
        )
        refreshRecommendation()
    }

    private fun currentOption(): ExerciseOption? =
        _state.value.exercises.firstOrNull { it.id == _state.value.exerciseId }

    fun setWeight(value: String) = update { copy(weightText = value, lastSaved = false) }
    fun setReps(value: String) = update { copy(repsText = value, lastSaved = false) }
    fun setRpe(value: String) = update { copy(rpeText = value, lastSaved = false) }

    fun syncHealth() = viewModelScope.launch {
        runCatching {
            val status = healthRepository.status()
            if (!status.available) {
                updateHealth {
                    copy(
                        available = false,
                        connected = false,
                        providerUpdateRequired = status.providerUpdateRequired,
                        grantedCount = status.grantedCount,
                        requiredCount = status.requiredCount,
                        error = if (status.providerUpdateRequired) {
                            "Health Connect требует обновления"
                        } else {
                            "Health Connect недоступен на телефоне"
                        },
                    )
                }
                showError("Health Connect недоступен — данные Mi Fitness прочитать нельзя")
                return@launch
            }
            if (!status.connected) {
                // Android may report an empty grant set on some ROMs while the
                // data itself is still readable, so a real read decides.
                val probe = runCatching { healthRepository.read() }
                val snapshot = probe.getOrNull()
                if (snapshot != null) {
                    repository.upsertHealth(snapshot)
                    applyHealth(status.copy(connected = true), snapshot)
                    updateHealth { copy(connected = true, error = null) }
                    showMessage(healthMessage(snapshot))
                    return@launch
                }
                updateHealth {
                    copy(
                        available = true,
                        connected = false,
                        grantedCount = status.grantedCount,
                        requiredCount = status.requiredCount,
                        error = probe.exceptionOrNull()?.message
                            ?: "Выдано ${status.grantedCount} из ${status.requiredCount} разрешений",
                    )
                }
                showError("Нет доступа к данным Health Connect")
                return@launch
            }
            val snapshot = healthRepository.read()
            repository.upsertHealth(snapshot)
            applyHealth(status, snapshot)
            updateHealth { copy(error = null) }
            showMessage(healthMessage(snapshot))
        }.onFailure {
            updateHealth { copy(error = it.message ?: "Не удалось прочитать Health Connect") }
            showError(it.message ?: "Не удалось прочитать Health Connect")
        }
    }

    private fun healthMessage(snapshot: HealthSnapshotEntity): String {
        val hasData = snapshot.sleepMinutes != null || snapshot.restingHr != null ||
            snapshot.steps != null || snapshot.activeCalories != null
        return if (hasData) "Данные здоровья обновлены"
        else "Health Connect доступен, но свежих данных нет"
    }

    /** Enables or cancels the hourly background read of Health Connect. */
    fun setBackgroundSync(enabled: Boolean) {
        if (enabled) {
            if (!healthRepository.hasBackgroundPermission()) {
                showError("Сначала разрешите фоновое чтение в Health Connect")
                return
            }
            HealthSyncScheduler.schedule(appContext)
            updateHealth { copy(backgroundGranted = true) }
            showMessage("Фоновое обновление: раз в час")
        } else {
            HealthSyncScheduler.cancel(appContext)
            updateHealth { copy(backgroundGranted = false) }
            showMessage("Фоновое обновление выключено")
        }
    }

    /** Called by the permission dialog result; gives the button visible feedback. */
    fun onHealthPermissionsResult(granted: Set<String>) {
        val missing = HealthRepository.corePermissions - granted
        if (missing.isEmpty()) {
            syncHealth()
        } else {
            updateHealth {
                copy(
                    available = true,
                    connected = false,
                    grantedCount = HealthRepository.corePermissions.intersect(granted).size,
                    requiredCount = HealthRepository.corePermissions.size,
                    error = "Разрешения не выданы",
                )
            }
            showError("Разрешения не выданы")
        }
    }

    private suspend fun loadHealth() {
        if (healthRepository.hasBackgroundPermission()) {
            HealthSyncScheduler.schedule(appContext)
        }
        val status = runCatching { healthRepository.status() }.getOrElse {
            HealthStatus(available = false, providerUpdateRequired = false, connected = false)
        }
        val snapshot = runCatching { repository.latestHealth() }.getOrNull()
        applyHealth(status, snapshot)
        if (status.available) {
            runCatching {
                val fresh = healthRepository.read()
                repository.upsertHealth(fresh)
                applyHealth(status.copy(connected = true), fresh)
            }.onFailure {
                updateHealth { copy(error = it.message ?: "Не удалось прочитать Health Connect") }
            }
        }
    }

    private suspend fun applyHealth(status: HealthStatus, snapshot: HealthSnapshotEntity?) {
        val hours = hoursSincePreviousWorkout()
        val readiness = Progression.readinessFromHealth(
            sleepMinutes = snapshot?.sleepMinutes,
            deepSleepMinutes = snapshot?.deepSleepMinutes,
            restingHr = snapshot?.restingHr,
            baselineRestingHr = snapshot?.baselineRestingHr,
            hoursSinceLastWorkout = hours,
        )
        _state.value = _state.value.copy(
            health = HealthUi(
                available = status.available,
                connected = status.connected,
                providerUpdateRequired = status.providerUpdateRequired,
                grantedCount = status.grantedCount,
                requiredCount = status.requiredCount,
                backgroundGranted = status.backgroundGranted,
                sleepMinutes = snapshot?.sleepMinutes,
                deepSleepMinutes = snapshot?.deepSleepMinutes,
                remSleepMinutes = snapshot?.remSleepMinutes,
                sleepStartMillis = snapshot?.sleepStartMillis,
                sleepEndMillis = snapshot?.sleepEndMillis,
                restingHr = snapshot?.restingHr,
                baselineRestingHr = snapshot?.baselineRestingHr,
                avgHr = snapshot?.avgHr,
                steps = snapshot?.steps,
                activeCalories = snapshot?.activeCalories,
                vo2max = snapshot?.vo2max,
                hrvRmssd = snapshot?.hrvRmssd,
                updatedAt = snapshot?.updatedAt,
            ),
            readiness = readiness.score,
            readinessReason = readiness.reason,
        )
        refreshRecommendation()
        persistReadiness()
    }

    private suspend fun hoursSincePreviousWorkout(): Double {
        val today = LocalDate.now()
        val previous = repository.workouts()
            .map { LocalDate.ofEpochDay(it.dateEpochDay) }
            .filter { it < today }
            .maxOrNull()
            ?: return 72.0
        return Duration.between(previous.atStartOfDay(), LocalDateTime.now()).toHours()
            .toDouble()
            .coerceAtLeast(0.0)
    }

    private fun updateHealth(transform: HealthUi.() -> HealthUi) {
        _state.value = _state.value.copy(health = _state.value.health.transform())
    }

    private fun persistReadiness() = viewModelScope.launch {
        val workoutId = _state.value.workoutId ?: return@launch
        val readiness = _state.value.readiness ?: return@launch
        runCatching { repository.updateReadiness(workoutId, readiness) }
    }

    fun adjustWeight(delta: Double) {
        val current = _state.value.weightText.replace(',', '.').toDoubleOrNull() ?: 0.0
        setWeight(formatWeight((current + delta).coerceAtLeast(0.0)))
    }

    fun applyRecommendation() {
        val target = _state.value.recommendation ?: return
        setWeight(formatWeight(target.weightKg))
        setReps(target.targetReps.toString())
    }

    fun saveSet() = viewModelScope.launch {
        val current = _state.value
        val workoutId = current.workoutId
        val exerciseId = current.exerciseId
        val weight = current.weightText.replace(',', '.').toDoubleOrNull()
        val reps = current.repsText.toIntOrNull()
        val rpe = current.rpeText.replace(',', '.').toDoubleOrNull()
        if (workoutId == null || exerciseId == null || weight == null || reps == null ||
            weight < 0 || reps <= 0 || rpe == null || rpe !in 1.0..10.0
        ) {
            update { copy(error = "Проверьте вес, повторы и RPE", lastSaved = false) }
            return@launch
        }
        runCatching {
            repository.recordSet(workoutId, exerciseId, weight, reps, rpe)
            val history = repository.recentSets(exerciseId, excludingWorkoutId = workoutId)
            val sets = repository.setsForWorkoutExercise(workoutId, exerciseId)
            update {
                copy(
                    history = history,
                    sets = sets,
                    weightText = formatWeight(weight),
                    repsText = reps.toString(),
                    lastSaved = true,
                    error = null,
                )
            }
            reloadStats()
        }.onFailure { update { copy(error = it.message ?: "Не удалось сохранить подход", lastSaved = false) } }
    }

    fun updateSet(setId: Long, weightText: String, repsText: String, rpeText: String) =
        viewModelScope.launch {
            val weight = weightText.replace(',', '.').toDoubleOrNull()
            val reps = repsText.trim().toIntOrNull()
            val rpe = rpeText.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
            val invalidRpe = rpeText.trim().isNotEmpty() && (rpe == null || rpe !in 1.0..10.0)
            if (weight == null || reps == null || weight < 0 || reps <= 0 || invalidRpe) {
                showError("Проверьте вес, повторы и RPE")
                return@launch
            }
            runCatching {
                repository.updateSet(setId, weight, reps, rpe)
                refreshAfterSetChange()
                showMessage("Подход обновлён")
            }.onFailure { report(it) }
        }

    fun deleteSet(setId: Long) = viewModelScope.launch {
        runCatching {
            repository.deleteSet(setId)
            refreshAfterSetChange()
            showMessage("Подход удалён")
        }.onFailure { report(it) }
    }

    private suspend fun refreshAfterSetChange() {
        val current = _state.value
        val workoutId = current.workoutId ?: return
        val exerciseId = current.exerciseId ?: return
        val history = repository.recentSets(exerciseId, excludingWorkoutId = workoutId)
        val sets = repository.setsForWorkoutExercise(workoutId, exerciseId)
        _state.value = _state.value.copy(history = history, sets = sets, lastSaved = false)
        refreshRecommendation()
        reloadStats()
    }

    fun reloadStats() = viewModelScope.launch {
        runCatching {
            val stats = computeStats()
            val selected = stats.trends.firstOrNull { it.key == _state.value.statsExerciseKey }?.key
                ?: stats.trends.firstOrNull()?.key
            _state.value = _state.value.copy(stats = stats, statsExerciseKey = selected)
            loadStatsSetsFor(selected)
        }
    }

    private suspend fun loadStatsSetsFor(key: String?) {
        if (key == null) {
            _state.value = _state.value.copy(statsSets = emptyList())
            return
        }
        val exercises = repository.exercises().associateBy { it.id }
        val sets = repository.allSetsWithDates().filter { item ->
            val exercise = exercises[item.set.exerciseId] ?: return@filter false
            ExerciseCatalog.keyFor(exercise.name, exercise.isCustom, exercise.id) == key
        }.take(40)
        _state.value = _state.value.copy(statsSets = sets)
    }

    /** Call after a backup import so the UI reflects the restored data. */
    fun reloadAll() = viewModelScope.launch {
        runCatching {
            repository.seedDefaults()
            loadHealth()
            val exercises = mainExercises()
            val locations = repository.locations().map { it.name }
            val active = exercises.filterNot { it.archived }
            val selected = exercises.firstOrNull { it.id == _state.value.exerciseId && !it.archived }
                ?: active.firstOrNull { it.name == "Жим лёжа" }
                ?: active.firstOrNull()
            val readiness = _state.value.readiness
            val workoutId = repository.startWorkout(
                LocalDate.now(),
                locationName = _state.value.selectedLocation,
                readiness = readiness,
            )
            readiness?.let { repository.updateReadiness(workoutId, it) }
            _state.value = _state.value.copy(exercises = exercises, locations = locations, workoutId = workoutId)
            if (selected != null) loadExerciseContext(selected) else {
                _state.value = _state.value.copy(exerciseId = null, sets = emptyList(), history = emptyList())
            }
            reloadStats()
        }.onFailure { report(it) }
    }

    fun showMessage(text: String) = update { copy(message = text, error = null) }

    fun showError(text: String) = update { copy(error = text, message = null) }

    private suspend fun computeStats(): StatsUi {
        val workouts = repository.workouts()
        val sets = repository.allSetsWithDates()
        val exercises = repository.exercises().associateBy { it.id }
        val dateByWorkout = workouts.associate { it.id to LocalDate.ofEpochDay(it.dateEpochDay) }
        val today = LocalDate.now()
        val workoutIdsWithSets = sets.map { it.set.workoutId }.toSet()
        val doneWorkouts = workouts.filter { it.id in workoutIdsWithSets }

        val grouped = linkedMapOf<String, MutableList<SetWithDate>>()
        sets.forEach { item ->
            val exercise = exercises[item.set.exerciseId] ?: return@forEach
            val key = ExerciseCatalog.keyFor(exercise.name, exercise.isCustom, exercise.id)
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        val order = ExerciseCatalog.groups.mapIndexed { index, group -> group.key to index }.toMap()
        val trends = grouped.mapNotNull { (key, items) ->
            // Old diary fragments such as "55 кг ×" are not real exercise names.
            if (key.startsWith("legacy:") && items.size < 4) return@mapNotNull null
            val weighted = items.filter { it.set.weightKg > 0 }
            if (weighted.isEmpty()) return@mapNotNull null
            val points = weighted
                .groupBy { it.set.workoutId }
                .mapNotNull { (workoutId, workoutSets) ->
                    val date = dateByWorkout[workoutId] ?: return@mapNotNull null
                    ProgressPoint(date, workoutSets.maxOf { estimate1Rm(it.set) })
                }
                .sortedBy { it.date }
            if (points.isEmpty()) return@mapNotNull null
            val sampleExercise = exercises[items.first().set.exerciseId]
            val displayName = ExerciseCatalog.groups.firstOrNull { it.key == key }?.name
                ?: items.groupingBy { exercises[it.set.exerciseId]?.name.orEmpty() }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?.takeIf { it.isNotBlank() }
                ?: sampleExercise?.name
                ?: key
            ExerciseTrend(
                key = key,
                name = displayName,
                firstE1rm = points.first().e1rm,
                lastE1rm = points.last().e1rm,
                lastDate = points.last().date,
                workoutCount = points.size,
                peakWeightKg = weighted.maxOf { it.set.weightKg },
                peakE1rm = weighted.maxOf { estimate1Rm(it.set) },
                points = points,
            )
        }.sortedWith(compareBy({ order[it.key] ?: Int.MAX_VALUE }, { it.name }))

        val lastWorkout = doneWorkouts.maxOfOrNull { it.dateEpochDay }?.let(LocalDate::ofEpochDay)
        val since7 = today.minusDays(7)
        val tonnage7d = sets.filter { LocalDate.ofEpochDay(it.dateEpochDay) >= since7 }
            .sumOf { it.set.weightKg * it.set.reps }
        return StatsUi(
            totalWorkouts = doneWorkouts.size,
            last30Days = doneWorkouts.count {
                ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it.dateEpochDay), today) in 0..30
            },
            lastWorkout = lastWorkout,
            tonnage7d = tonnage7d,
            trends = trends,
        )
    }

    private fun estimate1Rm(set: SetEntity): Double =
        if (set.reps in 1..30) Progression.epley1Rm(set.weightKg.coerceAtLeast(0.01), set.reps) else set.weightKg

    private fun refreshRecommendation() {
        val current = _state.value
        val last = current.history.firstOrNull()
        val recommendation = if (last != null) {
            runCatching {
                Progression.recommend(
                    previousWeightKg = last.weightKg.coerceAtLeast(0.01),
                    previousReps = last.reps,
                    targetReps = last.reps,
                    rpe = last.rpe,
                    readinessScore = current.readiness,
                    incrementKg = current.incrementKg,
                )
            }.getOrNull()
        } else null
        _state.value = current.copy(recommendation = recommendation)
    }

    private fun ExerciseEntity.toOption() = ExerciseOption(
        id = id,
        name = name,
        incrementKg = defaultIncrementKg,
        bodyweight = isBodyweight,
        isCustom = isCustom,
        archived = isArchived,
    )

    private fun formatWeight(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun report(error: Throwable) {
        update { copy(error = error.message ?: "Произошла ошибка") }
    }

    private inline fun update(transform: WorkoutUiState.() -> WorkoutUiState) {
        _state.value = _state.value.transform()
        refreshRecommendation()
    }

    companion object {
        fun factory(
            context: Context,
            repository: WorkoutRepository,
            healthRepository: HealthRepository,
            bundledImporter: suspend () -> Unit = {},
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutViewModel(context.applicationContext, repository, healthRepository, bundledImporter) as T
        }
    }
}
