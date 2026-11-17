package com.irontrack.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irontrack.app.ExerciseOption
import com.irontrack.app.WorkoutUiState
import com.irontrack.app.WorkoutViewModel
import com.irontrack.app.core.Progression
import com.irontrack.app.core.ExerciseCatalog
import com.irontrack.app.data.local.SetEntity
import com.irontrack.app.data.local.SetWithDate
import com.irontrack.app.data.local.WorkoutDatabase
import com.irontrack.app.data.local.WorkoutExport
import com.irontrack.app.health.HealthRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(10.dp)

@Composable
fun ActiveWorkoutScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, onCue: () -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    var locationOpen by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetEntity?>(null) }
    var addExerciseOpen by remember { mutableStateOf(false) }
    var actionExercise by remember { mutableStateOf<ExerciseOption?>(null) }
    var wipeExercise by remember { mutableStateOf<ExerciseOption?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("IronTrack", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(formatDateLong(LocalDate.now()), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "готовность ${state.readiness?.toString() ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).clickable { pickerOpen = true }) {
                Text(state.exerciseName, style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("список упражнений", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                Row(
                    Modifier
                        .clip(ControlShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), ControlShape)
                        .clickable { locationOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.selectedLocation ?: "зал не указан",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                DropdownMenu(expanded = locationOpen, onDismissRequest = { locationOpen = false }) {
                    DropdownMenuItem(text = { Text("Без зала") }, onClick = {
                        locationOpen = false; viewModel.selectLocation(null)
                    })
                    state.locations.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            locationOpen = false; viewModel.selectLocation(name)
                        })
                    }
                }
            }
        }

        NextSetCard(state, viewModel)

        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onCue() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Подсказка перед подходом", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text("прошлый подход, 1RM и готовность", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (state.recommendation != null) "Открыть" else "Нет истории",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.recommendation != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Hairline()

        if (state.sets.isNotEmpty()) {
            SectionHeader("Сегодня", "${state.sets.size} ${plural(state.sets.size, "подход", "подхода", "подходов")}")
            Column {
                state.sets.forEachIndexed { index, set ->
                    SetRow(set, onClick = { editingSet = set })
                    if (index != state.sets.lastIndex) Hairline()
                }
            }
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(8.dp))
    }

    editingSet?.let { set ->
        EditSetDialog(
            set = set,
            onDismiss = { editingSet = null },
            onSave = { weight, reps, rpe ->
                editingSet = null
                viewModel.updateSet(set.id, weight, reps, rpe)
            },
            onDelete = {
                editingSet = null
                viewModel.deleteSet(set.id)
            },
        )
    }

    if (addExerciseOpen) {
        AddExerciseDialog(
            onDismiss = { addExerciseOpen = false },
            onSave = { name, increment ->
                addExerciseOpen = false
                viewModel.addCustomExercise(name, increment)
            },
        )
    }

    if (pickerOpen) {
        ExercisePickerSheet(
            options = state.exercises,
            selectedId = state.exerciseId,
            onDismiss = { pickerOpen = false },
            onSelect = { option ->
                pickerOpen = false
                viewModel.selectExercise(option.id)
            },
            onAdd = {
                pickerOpen = false
                addExerciseOpen = true
            },
            onLongPress = { option -> actionExercise = option },
        )
    }

    actionExercise?.let { option ->
        AlertDialog(
            onDismissRequest = { actionExercise = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(option.name, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (option.archived) {
                        "Вернуть упражнение в список?"
                    } else {
                        "Убрать из списка? История подходов сохранится, упражнение можно будет вернуть в разделе «Скрытые»."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                if (option.archived) {
                    TextButton(onClick = {
                        actionExercise = null
                        viewModel.setExerciseArchived(option.id, false)
                    }) { Text("Вернуть") }
                } else {
                    TextButton(onClick = {
                        actionExercise = null
                        viewModel.setExerciseArchived(option.id, true)
                    }) { Text("Скрыть") }
                }
            },
            dismissButton = {
                Row {
                    if (option.setCount == 0) {
                        TextButton(onClick = {
                            actionExercise = null
                            viewModel.deleteExercise(option.id)
                        }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = {
                            actionExercise = null
                            wipeExercise = option
                        }) {
                            Text("С историей…", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { actionExercise = null }) { Text("Отмена") }
                }
            },
        )
    }

    wipeExercise?.let { option ->
        AlertDialog(
            onDismissRequest = { wipeExercise = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить с историей?") },
            text = {
                Text(
                    "«${option.name}» и ${option.setCount} " +
                        plural(option.setCount, "подход", "подхода", "подходов") +
                        " будут удалены безвозвратно. Статистика и график по этому упражнению исчезнут.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    wipeExercise = null
                    viewModel.deleteExerciseWithHistory(option.id)
                }) { Text("Удалить всё", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { wipeExercise = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    options: List<ExerciseOption>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (ExerciseOption) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (ExerciseOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = options.filterNot { it.archived }
    val archived = options.filter { it.archived }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Упражнения", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onAdd) { Text("Добавить") }
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(active, key = { it.id }) { option ->
                    ExercisePickerRow(
                        option = option,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option) },
                        onLongClick = { onLongPress(option) },
                    )
                    Hairline()
                }
            }

            Text(
                "Долгое нажатие — скрыть или удалить упражнение",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (archived.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Скрытые",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                archived.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLongPress(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "вернуть долгим нажатием",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExercisePickerRow(
    option: ExerciseOption,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                option.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (option.isCustom) "своё · шаг ${formatWeight(option.incrementKg)} кг"
                else "шаг ${formatWeight(option.incrementKg)} кг",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text("выбрано", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var increment by remember { mutableStateOf("2.5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Новое упражнение", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = increment,
                    onValueChange = { increment = it },
                    label = { Text("Шаг веса, кг") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, increment) },
                enabled = name.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NextSetCard(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val last = state.history.firstOrNull()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Следующий подход", style = MaterialTheme.typography.labelMedium, color = muted)
                BarbellMark(Modifier.size(width = 48.dp, height = 22.dp))
            }
            state.recommendation?.let {
                Text(
                    "цель ${formatWeight(it.weightKg)} × ${it.targetReps}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.weightText,
                    onValueChange = viewModel::setWeight,
                    modifier = Modifier.weight(1f).height(84.dp),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("×", style = MaterialTheme.typography.headlineMedium, color = muted,
                    modifier = Modifier.padding(bottom = 24.dp))
                OutlinedTextField(
                    value = state.repsText,
                    onValueChange = viewModel::setReps,
                    modifier = Modifier.weight(0.7f).height(84.dp),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepButton("− ${formatWeight(state.incrementKg)}", {
                        viewModel.adjustWeight(-state.incrementKg)
                    })
                    StepButton("+ ${formatWeight(state.incrementKg)}", {
                        viewModel.adjustWeight(state.incrementKg)
                    })
                }
                Text("кг · повторы", style = MaterialTheme.typography.bodySmall, color = muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RPE", style = MaterialTheme.typography.labelMedium, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (6..10).forEach { value ->
                        val selected = state.rpeText.replace(',', '.').toDoubleOrNull() == value.toDouble()
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(ControlShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    ControlShape,
                                )
                                .clickable { viewModel.setRpe(value.toString()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                value.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::saveSet,
                enabled = state.workoutId != null && state.exerciseId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = ControlShape,
            ) {
                Text(if (state.lastSaved) "Записано" else "Записать подход",
                    style = MaterialTheme.typography.labelLarge)
            }

            last?.let {
                Text(
                    "прошлый раз ${formatWeight(it.weightKg)} × ${it.reps} · RPE ${it.rpe?.let(::formatWeight) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SetRow(set: SetEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${set.setNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Text(
            "${formatWeight(set.weightKg)} × ${set.reps}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (set.rpe != null) "RPE ${formatWeight(set.rpe)}" else "без RPE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text("изм.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EditSetDialog(
    set: SetEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var weight by remember(set.id) { mutableStateOf(formatWeight(set.weightKg)) }
    var reps by remember(set.id) { mutableStateOf(set.reps.toString()) }
    var rpe by remember(set.id) { mutableStateOf(set.rpe?.let(::formatWeight) ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Подход ${set.setNumber}", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditField("Вес, кг", weight, { weight = it }, KeyboardType.Decimal)
                EditField("Повторы", reps, { reps = it }, KeyboardType.Number)
                EditField("RPE", rpe, { rpe = it }, KeyboardType.Decimal)
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Удалить подход") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(weight, reps, rpe) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

@Composable
fun StatsScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, database: WorkoutDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by remember { mutableStateOf(false) }
    var confirmBundled by remember { mutableStateOf(false) }
    var exerciseOpen by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetWithDate?>(null) }
    val prefs = remember { context.getSharedPreferences("workout", android.content.Context.MODE_PRIVATE) }
    var bundledLoaded by remember { mutableStateOf(prefs.getBoolean("bundled_loaded", false)) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { WorkoutExport.write(context, database, uri) }
                .onSuccess { viewModel.showMessage("Копия сохранена") }
                .onFailure { viewModel.showError(it.message ?: "Не удалось сохранить копию") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { WorkoutExport.readAndReplace(context, database, uri) }
                .onSuccess {
                    viewModel.reloadAll()
                    viewModel.showMessage("Данные восстановлены")
                }
                .onFailure { viewModel.showError(it.message ?: "Не удалось прочитать JSON") }
        }
    }

    if (confirmBundled) {
        AlertDialog(
            onDismissRequest = { confirmBundled = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Добавить историю из заметок?") },
            text = { Text("45 тренировок, 3 зала, 718 подходов. История добавится к текущим записям; упражнения и залы сопоставляются по названию.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBundled = false
                    scope.launch {
                        runCatching { WorkoutExport.readBundledHistory(context, database) }
                            .onSuccess {
                                prefs.edit().putBoolean("bundled_loaded", true).apply()
                                bundledLoaded = true
                                viewModel.reloadAll()
                                viewModel.showMessage("История добавлена")
                            }
                            .onFailure { viewModel.showError(it.message ?: "Не удалось загрузить историю") }
                    }
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { confirmBundled = false }) { Text("Отмена") } },
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Заменить данные?") },
            text = { Text("Текущая история будет заменена содержимым JSON-файла. Сначала сделайте резервную копию, если она нужна.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Выбрать файл") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Отмена") } },
        )
    }

    val stats = state.stats
    val trend = stats.trends.firstOrNull { it.key == state.statsExerciseKey }
        ?: stats.trends.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text("IronTrack", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Статистика", style = MaterialTheme.typography.headlineLarge)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatFigure("${stats.totalWorkouts}", "тренировок", Modifier.weight(1f))
            StatFigure("${stats.last30Days}", "за 30 дней", Modifier.weight(1f))
            StatFigure(formatDate(stats.lastWorkout), "последняя", Modifier.weight(1f))
        }
        Text(
            "Тоннаж за 7 дней: ${formatWeight(stats.tonnage7d / 1000.0)} т",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Оценка 1RM", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Box {
                            Row(
                                Modifier.clickable { exerciseOpen = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(trend?.name ?: "Нет данных",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (stats.trends.size > 1) {
                                    Text("  ·  сменить", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(expanded = exerciseOpen, onDismissRequest = { exerciseOpen = false }) {
                                stats.trends.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.name) },
                                        onClick = {
                                            exerciseOpen = false
                                            viewModel.selectStatsExercise(item.key)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    trend?.let {
                        Text(
                            (if (it.changeKg >= 0) "+" else "") + formatWeight(it.changeKg) + " кг",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (it.changeKg >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (trend != null) {
                    ProgressChart(trend.points)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${formatWeight(trend.points.minOf { it.e1rm })} кг", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatWeight(trend.points.maxOf { it.e1rm })} кг", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${formatDate(trend.points.first().date)} — ${formatDate(trend.points.last().date)} · ${trend.workoutCount} ${plural(trend.workoutCount, "тренировка", "тренировки", "тренировок")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "лучший вес ${formatWeight(trend.peakWeightKg)} кг · лучший 1RM ${formatWeight(trend.peakE1rm)} кг",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Запишите первый подход или загрузите историю из заметок ниже.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.statsSets.isNotEmpty()) {
            SectionHeader("Последние подходы", "нажмите для правки")
            Column {
                state.statsSets.take(12).forEachIndexed { index, item ->
                    SetHistoryRow(item, onClick = { editingSet = item })
                    if (index != state.statsSets.take(12).lastIndex) Hairline()
                }
            }
            if (state.statsSets.size > 12) {
                Text(
                    "показаны 12 из ${state.statsSets.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (stats.trends.isNotEmpty()) {
            SectionHeader("Упражнения", "оценка 1RM")
            Column {
                stats.trends.forEachIndexed { index, item ->
                    TrendRow(
                        item = item,
                        selected = item.key == trend?.key,
                        onClick = { viewModel.selectStatsExercise(item.key) },
                    )
                    if (index != stats.trends.lastIndex) Hairline()
                }
            }
        }

        SectionHeader("Данные", null)
        if (bundledLoaded) {
            Text(
                "История из заметок уже добавлена",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(
                onClick = { confirmBundled = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = ControlShape,
            ) { Text("Добавить историю из заметок") }
            Text(
                "45 тренировок и 718 подходов уже в приложении",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("workout-backup-${LocalDate.now()}.json") },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = ControlShape,
            ) { Text("Экспорт JSON") }
            OutlinedButton(
                onClick = { confirmImport = true },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = ControlShape,
            ) { Text("Импорт JSON") }
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "Подсказка по весу — ориентир на основе прошлого подхода и восстановления, не медицинская рекомендация.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }

    editingSet?.let { item ->
        EditSetDialog(
            set = item.set,
            onDismiss = { editingSet = null },
            onSave = { weight, reps, rpe ->
                editingSet = null
                viewModel.updateSet(item.set.id, weight, reps, rpe)
            },
            onDelete = {
                editingSet = null
                viewModel.deleteSet(item.set.id)
            },
        )
    }
}

@Composable
private fun SetHistoryRow(item: SetWithDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDate(LocalDate.ofEpochDay(item.dateEpochDay)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            "${formatWeight(item.set.weightKg)} × ${item.set.reps}",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (item.set.rpe != null) "RPE ${formatWeight(item.set.rpe)}" else "без RPE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text("изм.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TrendRow(
    item: com.irontrack.app.ExerciseTrend,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.workoutCount} ${plural(item.workoutCount, "тренировка", "тренировки", "тренировок")} · ${formatDate(item.lastDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${formatWeight(item.lastE1rm)} кг",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                (if (item.changeKg >= 0) "+" else "") + formatWeight(item.changeKg) + " кг",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = if (item.changeKg >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SetCueScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, onBack: () -> Unit) {
    val last = state.history.firstOrNull()
    val target = state.recommendation
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onHealthPermissionsResult(granted) }

    val optionalPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.syncHealth() }

    val backgroundPermissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.contains(HealthRepository.BACKGROUND_PERMISSION)) {
            viewModel.setBackgroundSync(true)
        } else {
            viewModel.showError("Фоновое чтение не разрешено")
        }
    }

    val requestPermissions = {
        runCatching { permissionLauncher.launch(HealthRepository.corePermissions) }
            .onFailure {
                viewModel.showError("Не удалось открыть запрос разрешений: ${it.message ?: "нет ответа"}")
            }
        Unit
    }

    val openHealthConnect = {
        val settings = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        val home = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
        )
        val opened = listOf(settings, home, market, web).any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
        if (!opened) {
            viewModel.showError("Не удалось открыть Health Connect. Откройте настройки телефона вручную.")
        }
        Unit
    }
    val health = state.health

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("IronTrack", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Перед подходом", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("Назад") }
        }
        Text(state.exerciseName, style = MaterialTheme.typography.headlineLarge)

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Прошлый раз", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (last == null) {
                    Text("Нет сохранённых подходов", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${formatWeight(last.weightKg)} × ${last.reps}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            "RPE ${last.rpe?.let(::formatWeight) ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    val estimate = runCatching {
                        Progression.epley1Rm(last.weightKg.coerceAtLeast(0.01), last.reps)
                    }.getOrNull()
                    estimate?.let {
                        Text("1RM по этому подходу ≈ ${formatWeight(it)} кг",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val groupKey = ExerciseCatalog.groupForName(state.exerciseName)?.key
                    val best = state.stats.trends.firstOrNull { it.key == groupKey }
                    best?.let {
                        Text("лучший результат: ${formatWeight(it.peakWeightKg)} кг · 1RM ${formatWeight(it.peakE1rm)} кг",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Восстановление", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.readiness?.let { "$it / 100" } ?: "нет данных",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.readiness != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.readiness != null) {
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier.fillMaxWidth((state.readiness ?: 0) / 100f).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    state.readinessReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    !health.available -> {
                        Text(
                            if (health.providerUpdateRequired) "Нужно обновить Health Connect"
                            else "Health Connect не найден",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "IronTrack читает сон и пульс только через Health Connect. " +
                                "В Mi Fitness включите передачу данных в Health Connect, " +
                                "затем вернитесь сюда и нажмите «Проверить снова».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = openHealthConnect,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ControlShape,
                        ) {
                            Text(if (health.providerUpdateRequired) "Обновить Health Connect" else "Открыть Health Connect")
                        }
                        OutlinedButton(
                            onClick = viewModel::syncHealth,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ControlShape,
                        ) { Text("Проверить снова") }
                    }
                    !health.connected -> {
                        Text("Нужны разрешения", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (health.requiredCount > 0) {
                                "Выдано ${health.grantedCount} из ${health.requiredCount}. " +
                                    "Нужны сон, пульс покоя, пульс, шаги и активные калории."
                            } else {
                                "Нужны сон, пульс покоя, пульс, шаги и активные калории."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = requestPermissions,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ControlShape,
                        ) { Text("Разрешить Health Connect") }
                        OutlinedButton(
                            onClick = viewModel::syncHealth,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ControlShape,
                        ) { Text("Проверить снова") }
                    }
                    else -> {
                        HealthLine("Сон", formatSleep(health.sleepMinutes))
                        if (health.sleepMinutes == null && health.steps == null && health.restingHr == null) {
                            Text(
                                "Health Connect доступен, но свежих записей нет. " +
                                    "Проверьте, что Mi Fitness синхронизируется с Health Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (health.sleepStartMillis != null && health.sleepEndMillis != null) {
                            HealthLine(
                                "Засыпание / пробуждение",
                                "${formatClock(health.sleepStartMillis)}–${formatClock(health.sleepEndMillis)}",
                            )
                        }
                        health.deepSleepMinutes?.let { HealthLine("Глубокий сон", formatSleep(it)) }
                        health.avgHr?.let { HealthLine("Пульс за сутки", "$it") }
                        HealthLine(
                            "Пульс покоя",
                            health.restingHr?.let { bpm ->
                                if (health.baselineRestingHr != null) "$bpm · норма ${health.baselineRestingHr}"
                                else "$bpm"
                            } ?: "—",
                        )
                        HealthLine("Шаги", health.steps?.let(::formatNumber) ?: "—")
                        HealthLine("Активные калории", health.activeCalories?.let { "$it ккал" } ?: "—")
                        health.vo2max?.let { HealthLine("VO2max", formatWeight(it)) }
                        health.hrvRmssd?.let { HealthLine("HRV", "${formatWeight(it)} мс") }
                        TextButton(
                            onClick = {
                                runCatching {
                                    optionalPermissionsLauncher.launch(HealthRepository.optionalPermissions)
                                }.onFailure { viewModel.showError("Не удалось запросить доп. разрешения") }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("Доп. данные: VO2max и HRV") }
                        OutlinedButton(
                            onClick = viewModel::syncHealth,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ControlShape,
                        ) { Text("Обновить данные") }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (health.backgroundGranted) "Фон: обновление раз в час"
                                else "Фон: обновление выключено",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    if (health.backgroundGranted) {
                                        viewModel.setBackgroundSync(false)
                                    } else {
                                        runCatching {
                                            backgroundPermissionsLauncher.launch(
                                                setOf(HealthRepository.BACKGROUND_PERMISSION),
                                            )
                                        }.onFailure {
                                            viewModel.showError("Не удалось запросить фоновое чтение")
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(if (health.backgroundGranted) "Выключить" else "Включить")
                            }
                        }
                    }
                }
                health.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Цель", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (target == null) {
                    Text("Недостаточно истории", style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(
                        "${formatWeight(target.weightKg)} кг × ${target.targetReps}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    )
                    Text(target.reason.toRussian(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { viewModel.applyRecommendation(); onBack() },
                    enabled = target != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ControlShape,
                ) { Text("Применить цель") }
            }
        }

        Text(
            "Остановитесь при боли или плохом самочувствии.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HealthLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
    }
}

private fun formatSleep(minutes: Int?): String {
    if (minutes == null) return "—"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "$hours ч $rest мин" else "$rest мин"
}

private fun formatNumber(value: Int): String =
    String.format(Locale.US, "%,d", value).replace(',', ' ')

private fun formatClock(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun StatFigure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        trailing?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    )
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

private fun com.irontrack.app.core.RecommendationReason.toRussian(): String = when (this) {
    com.irontrack.app.core.RecommendationReason.NO_RECOVERY_DATA -> "нет данных о восстановлении — держать вес"
    com.irontrack.app.core.RecommendationReason.LOW_READINESS_REDUCE -> "низкая готовность — снизить вес"
    com.irontrack.app.core.RecommendationReason.MODERATE_READINESS_HOLD -> "умеренная готовность — удержать вес"
    com.irontrack.app.core.RecommendationReason.TARGET_MET_WITH_RESERVE -> "цель выполнена с запасом — увеличить вес"
    com.irontrack.app.core.RecommendationReason.ADD_REPETITION -> "добавить повторение"
    com.irontrack.app.core.RecommendationReason.HOLD_NO_RESERVE -> "удержать нагрузку"
}

/**
 * Privacy note shown when Health Connect asks the app to explain its data use
 * (`ACTION_VIEW_PERMISSION_USAGE`). Health Connect will not hand out sleep or
 * heart-rate permissions until the app handles this action.
 */
@Composable
fun PermissionUsageScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("IronTrack", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("Данные о здоровье", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("Закрыть") }
        }

        Text("Зачем IronTrack доступ к здоровью", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Приложение читает сон, пульс покоя, пульс, шаги и активные калории, " +
                "чтобы посчитать восстановление перед тренировкой и подсказать вес на подход.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Все данные остаются на этом телефоне. IronTrack ничего не отправляет в интернет, " +
                "не имеет сетевых разрешений и не передаёт историю третьим лицам. " +
                "Записи о тренировках можно выгрузить в JSON вручную.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Доступ можно отозвать в любой момент в Health Connect: «Доступ и данные» → " +
                "«Доступ к данным приложений» → IronTrack.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = ControlShape,
        ) { Text("Понятно") }
    }
}
