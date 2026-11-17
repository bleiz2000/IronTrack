package com.irontrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.irontrack.app.data.local.WorkoutDatabase
import com.irontrack.app.data.local.WorkoutExport
import com.irontrack.app.data.local.WorkoutRepository
import com.irontrack.app.health.HealthRepository
import com.irontrack.app.ui.ActiveWorkoutScreen
import com.irontrack.app.ui.PermissionUsageScreen
import com.irontrack.app.ui.SetCueScreen
import com.irontrack.app.ui.StatsScreen
import com.irontrack.app.ui.WorkoutTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { WorkoutDatabase.create(applicationContext) }
    private val repository by lazy { WorkoutRepository(database) }
    private val healthRepository by lazy { HealthRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val prefs = getSharedPreferences("workout", MODE_PRIVATE)
        val bundledImporter: suspend () -> Unit = {
            if (!prefs.getBoolean("bundled_loaded", false)) {
                WorkoutExport.readBundledHistory(applicationContext, database)
                prefs.edit().putBoolean("bundled_loaded", true).apply()
            }
        }
        val showPermissionUsage = intent?.action in PERMISSION_USAGE_ACTIONS
        setContent {
            WorkoutTheme {
                val vm: WorkoutViewModel = viewModel(
                    factory = WorkoutViewModel.factory(
                        applicationContext,
                        repository,
                        healthRepository,
                        bundledImporter,
                    ),
                )
                if (showPermissionUsage) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        PermissionUsageScreen(onBack = { finish() })
                    }
                } else {
                    WorkoutApp(vm, database)
                }
            }
        }
    }

    companion object {
        /**
         * Health Connect opens the app with these actions before it grants sleep
         * and heart-rate access: `VIEW_PERMISSION_USAGE` on Android 14+ and the
         * older rationale action on the standalone Health Connect APK.
         */
        private val PERMISSION_USAGE_ACTIONS = setOf(
            "android.intent.action.VIEW_PERMISSION_USAGE",
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
        )
    }
}

private enum class Tab { TRAIN, STATS }

@Composable
private fun WorkoutApp(viewModel: WorkoutViewModel, database: WorkoutDatabase) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.TRAIN) }
    var showCue by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (showCue) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                SetCueScreen(state, viewModel, onBack = { showCue = false })
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.TRAIN -> ActiveWorkoutScreen(state, viewModel, onCue = { showCue = true })
                        Tab.STATS -> StatsScreen(state, viewModel, database)
                    }
                }
                BottomBar(current = tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        )
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
        ) {
            TabButton("Тренировка", current == Tab.TRAIN, { onSelect(Tab.TRAIN) }, Modifier.weight(1f))
            TabButton("Статистика", current == Tab.STATS, { onSelect(Tab.STATS) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.width(16.dp).height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
