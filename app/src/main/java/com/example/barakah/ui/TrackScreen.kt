package com.example.barakah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.Kind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrackScreen(vm: AppViewModel) {
    var dateText by rememberSaveable { mutableStateOf(vm.today().toString()) }
    val date = LocalDate.parse(dateText)
    val today = vm.today()
    val log = vm.logFor(date)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Track", "Log what happened. Honesty > perfection.")

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { dateText = date.minusDays(1).toString() }) { Text("‹ Prev") }
            Text(
                if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = { dateText = date.plusDays(1).toString() }, enabled = date.isBefore(today)) { Text("Next ›") }
        }

        // Prayers come from the Today timeline checkboxes — one source of truth.
        val prayerBlocks = vm.blocksFor(date).filter { it.kind == Kind.PRAYER }
        SectionCard(title = "🕌 PRAYERS ON TIME") {
            prayerBlocks.forEach { b ->
                val done = vm.isDone(date, b.templateId)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.title, Modifier.weight(1f))
                    Text(b.start.hm(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Switch(
                        checked = done, onCheckedChange = { vm.toggleDone(date, b.templateId) },
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        XTrackCard(vm, date)

        SectionCard(title = "📈 NUMBERS") {
            Stepper("📖 Quran pages", log.quranPages.toString(),
                onMinus = { vm.updateLog(date) { it.copy(quranPages = (it.quranPages - 1).coerceAtLeast(0)) } },
                onPlus = { vm.updateLog(date) { it.copy(quranPages = it.quranPages + 1) } })
            ToggleRow("💪 Sport done", log.sport) { v -> vm.updateLog(date) { it.copy(sport = v) } }
            ToggleRow("🤲 Sadaqa given", log.sadaqa) { v -> vm.updateLog(date) { it.copy(sadaqa = v) } }
        }

        SectionCard(title = "🏆 3 WINS OF THE DAY") {
            OutlinedTextField(
                value = log.wins,
                onValueChange = { v -> vm.updateLog(date) { it.copy(wins = v) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Shipped the login page, prayed all 5 on time, 2 replies on X…") },
                minLines = 3
            )
        }

        SectionCard(title = "📝 NOTES / WHAT TO FIX TOMORROW") {
            OutlinedTextField(
                value = log.notes,
                onValueChange = { v -> vm.updateLog(date) { it.copy(notes = v) } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        // Category progress for this day
        SectionCard(title = "TODAY BY AREA") {
            com.example.barakah.model.Category.entries
                .filter { it != com.example.barakah.model.Category.REST }
                .forEach { c ->
                    val s = vm.categoryScore(c, listOf(date))
                    if (s.planned == 0) return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${c.emoji} ${c.label}", Modifier.weight(0.4f), fontSize = 14.sp)
                        ProgressBar(s.ratio, Color(c.color), Modifier.weight(0.45f))
                        Text("${s.done}/${s.planned}", Modifier.weight(0.15f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
