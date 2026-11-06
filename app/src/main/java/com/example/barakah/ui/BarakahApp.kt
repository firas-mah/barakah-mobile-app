package com.example.barakah.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val emoji: String) {
    TODAY("Today", "🕰️"),
    TRACK("Track", "✅"),
    INDIE("Indie", "🚀"),
    STATS("Stats", "📊"),
    SETTINGS("Settings", "⚙️")
}

@Composable
fun BarakahApp(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(vm)
                Tab.TRACK -> TrackScreen(vm)
                Tab.INDIE -> IndieScreen(vm)
                Tab.STATS -> StatsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
