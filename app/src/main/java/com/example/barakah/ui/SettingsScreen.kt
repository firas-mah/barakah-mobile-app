@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.barakah.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.Anchor
import com.example.barakah.model.BlockTemplate
import com.example.barakah.model.CITIES
import com.example.barakah.model.Category
import com.example.barakah.model.DhuhrMode
import com.example.barakah.model.Kind
import com.example.barakah.notify.AlarmScheduler
import com.example.barakah.prayer.Prayer

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val today = vm.today()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<BlockTemplate?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings", "Make the plan fit your real life.")

        // ── Location ──
        SectionCard(title = "📍 CITY") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CITIES.forEach { city ->
                    FilterChip(
                        selected = s.cityName == city.name,
                        onClick = {
                            vm.updateSettings {
                                it.copy(
                                    cityName = city.name, latitude = city.lat, longitude = city.lng,
                                    zoneId = city.zone, adjustments = city.adjustments,
                                    dhuhrMode = city.dhuhrMode
                                )
                            }
                        },
                        label = { Text(city.name) }
                    )
                }
            }
        }

        // ── Prayer times ──
        val prayers = vm.prayers(today)
        SectionCard(title = "🕌 TODAY'S PRAYER TIMES — ADJUST TO YOUR MOSQUE") {
            listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { p ->
                val adj = s.adjustments[p] ?: 0
                Stepper(
                    "${p.label}  ${prayers.getValue(p).hm()}",
                    (if (adj >= 0) "+" else "") + "$adj min",
                    onMinus = { vm.updateSettings { it.copy(adjustments = it.adjustments + (p to adj - 1)) } },
                    onPlus = { vm.updateSettings { it.copy(adjustments = it.adjustments + (p to adj + 1)) } }
                )
            }
            Text(
                "Times are calculated automatically every day — they follow summer and winter by themselves. The ± minutes only align them with your mosque; set them once.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Mosque times ──
        SectionCard(title = "🕌 MOSQUE TIMES") {
            Text("Dhuhr iqama", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DhuhrMode.entries.forEach { m ->
                    FilterChip(
                        selected = s.dhuhrMode == m,
                        onClick = { vm.updateSettings { it.copy(dhuhrMode = m) } },
                        label = { Text(m.label) }
                    )
                }
            }
            if (s.dhuhrMode == DhuhrMode.BEFORE_ASR) {
                Stepper("Iqama before Asr", "${s.dhuhrBeforeAsrMin} min",
                    onMinus = { vm.updateSettings { it.copy(dhuhrBeforeAsrMin = (it.dhuhrBeforeAsrMin - 5).coerceAtLeast(10)) } },
                    onPlus = { vm.updateSettings { it.copy(dhuhrBeforeAsrMin = (it.dhuhrBeforeAsrMin + 5).coerceAtMost(120)) } })
            } else {
                Stepper("Iqama time", minuteOfDayLabel(s.dhuhrFixedMin),
                    onMinus = { vm.updateSettings { it.copy(dhuhrFixedMin = it.dhuhrFixedMin - 5) } },
                    onPlus = { vm.updateSettings { it.copy(dhuhrFixedMin = it.dhuhrFixedMin + 5) } })
            }
            Stepper("Arrive before iqama", "${s.mosqueArriveMin} min",
                onMinus = { vm.updateSettings { it.copy(mosqueArriveMin = (it.mosqueArriveMin - 5).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(mosqueArriveMin = (it.mosqueArriveMin + 5).coerceAtMost(60)) } })
            Spacer(Modifier.height(6.dp))
            Text("Friday", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Stepper("Jumu'ah khutba", minuteOfDayLabel(s.jumuahMin),
                onMinus = { vm.updateSettings { it.copy(jumuahMin = it.jumuahMin - 5) } },
                onPlus = { vm.updateSettings { it.copy(jumuahMin = it.jumuahMin + 5) } })
            Stepper("Arrive before khutba", "${s.jumuahArriveMin} min",
                onMinus = { vm.updateSettings { it.copy(jumuahArriveMin = (it.jumuahArriveMin - 5).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(jumuahArriveMin = (it.jumuahArriveMin + 5).coerceAtMost(90)) } })
            Spacer(Modifier.height(6.dp))
            Text(
                "Today: Dhuhr iqama ${vm.dhuhrIqama(today).hm()}" +
                    (if (vm.staysUntilAsr(today)) " → you stay for Asr, lunch is planned before." else " → lunch is planned after.") +
                    " Choosing a city sets this automatically (Kebili = before Asr, others = 13:00).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Sleep & friends ──
        val night = vm.night(today.plusDays(1))
        SectionCard(title = "🌙 SLEEP & 🤝 FRIENDS") {
            Stepper("Earliest bedtime", minuteOfDayLabel(s.baseBedtimeMin),
                onMinus = { vm.updateSettings { it.copy(baseBedtimeMin = (it.baseBedtimeMin - 15 + 1440) % 1440) } },
                onPlus = { vm.updateSettings { it.copy(baseBedtimeMin = (it.baseBedtimeMin + 15) % 1440) } })
            Stepper("Night sleep target", minutesLabel(s.targetSleepMin.toLong()),
                onMinus = { vm.updateSettings { it.copy(targetSleepMin = (it.targetSleepMin - 15).coerceAtLeast(240)) } },
                onPlus = { vm.updateSettings { it.copy(targetSleepMin = (it.targetSleepMin + 15).coerceAtMost(600)) } })
            Stepper("Friends — winter", minutesLabel(s.winterFriendsMin.toLong()),
                onMinus = { vm.updateSettings { it.copy(winterFriendsMin = (it.winterFriendsMin - 15).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(winterFriendsMin = (it.winterFriendsMin + 15).coerceAtMost(240)) } })
            Stepper("Friends — summer", minutesLabel(s.summerFriendsMin.toLong()),
                onMinus = { vm.updateSettings { it.copy(summerFriendsMin = (it.summerFriendsMin - 15).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(summerFriendsMin = (it.summerFriendsMin + 15).coerceAtMost(240)) } })
            Stepper("Summer = Isha after", minuteOfDayLabel(s.summerIshaThresholdMin),
                onMinus = { vm.updateSettings { it.copy(summerIshaThresholdMin = it.summerIshaThresholdMin - 15) } },
                onPlus = { vm.updateSettings { it.copy(summerIshaThresholdMin = it.summerIshaThresholdMin + 15) } })
            Spacer(Modifier.height(6.dp))
            Text(
                "Tonight: ${if (vm.isSummer(today)) "summer" else "winter"} mode · friends ${minutesLabel(vm.friendsCap(today).toLong())} · " +
                    "sleep ${night.bedtime.hm()} → ${night.wake.hm()} (${minutesLabel(night.sleepMin)})" +
                    if (night.tahajjud) " · tahajjud ✓" else " · no tahajjud (short night)",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── X growth ──
        SectionCard(title = "📣 X GROWTH") {
            Stepper("Daily reply goal", "${s.dailyReplyGoal}",
                onMinus = { vm.updateSettings { it.copy(dailyReplyGoal = (it.dailyReplyGoal - 5).coerceAtLeast(5)) } },
                onPlus = { vm.updateSettings { it.copy(dailyReplyGoal = (it.dailyReplyGoal + 5).coerceAtMost(60)) } })
            Text(
                "A day counts for your reply streak when you reach this goal. Changing it also re-checks past days.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Notifications ──
        SectionCard(title = "🔔 NOTIFICATIONS") {
            ToggleRow("Remind me when each block starts", s.notificationsEnabled) { v ->
                vm.updateSettings { it.copy(notificationsEnabled = v) }
            }
            ToggleRow("Add an ayah or hadith to each reminder", s.faithReminders) { v ->
                vm.updateSettings { it.copy(faithReminders = v) }
            }
            Stepper("Heads-up before prayer", "${s.prayerHeadsUpMin} min",
                onMinus = { vm.updateSettings { it.copy(prayerHeadsUpMin = (it.prayerHeadsUpMin - 5).coerceAtLeast(0)) } },
                onPlus = { vm.updateSettings { it.copy(prayerHeadsUpMin = (it.prayerHeadsUpMin + 5).coerceAtMost(30)) } })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(context)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reminders may arrive a few minutes late. Allow exact alarms to get them on time:",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    )
                }) { Text("Allow exact alarms") }
            }
        }

        // ── Week template ──
        SectionCard(title = "🧩 YOUR DAY TEMPLATE (ORDER MATTERS)") {
            Text(
                "Blocks marked “after previous” start when the block above them ends. Tap a block to edit it.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            vm.templates.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = t }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(Color(t.category.color))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(t.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(templateSummary(t), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.moveTemplate(t.id, up = true) }) { Text("↑") }
                    TextButton(onClick = { vm.moveTemplate(t.id, up = false) }) { Text("↓") }
                    Switch(checked = t.enabled, onCheckedChange = { vm.saveTemplate(t.copy(enabled = it)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    editing = BlockTemplate(
                        id = vm.newTemplateId(), title = "", category = Category.BUILD,
                        anchor = Anchor.PREVIOUS, offsetMin = 0, durationMin = 30
                    )
                }) { Text("+ Add block") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("Reset to default") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { t ->
        TemplateDialog(
            template = t,
            isNew = vm.templates.none { it.id == t.id },
            onSave = { vm.saveTemplate(it); editing = null },
            onDelete = { vm.deleteTemplate(t.id); editing = null },
            onDismiss = { editing = null }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset the day template?") },
            text = { Text("Your custom blocks and edits will be replaced by the default week. Your logs and products stay.") },
            confirmButton = { TextButton(onClick = { vm.resetTemplates(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

private fun templateSummary(t: BlockTemplate): String {
    val start = when (t.anchor) {
        Anchor.PREVIOUS -> "after previous"
        else -> t.anchor.label + when {
            t.offsetMin > 0 -> " +${t.offsetMin}m"
            t.offsetMin < 0 -> " ${t.offsetMin}m"
            else -> ""
        }
    }
    val length = when (t.kind) {
        Kind.PRAYER -> if (t.anchor == Anchor.DHUHR_IQAMA || t.anchor == Anchor.JUMUAH) "mosque" else minutesLabel(t.durationMin.toLong())
        Kind.NORMAL -> minutesLabel(t.durationMin.toLong())
        Kind.FILL -> "fills the gap"
        Kind.FRIENDS -> "friends cap"
        Kind.RECOVERY_SLEEP -> "only if night was short"
        Kind.TAHAJJUD -> "if you slept 5h+"
        Kind.SLEEP -> "until wake-up"
    }
    val days = if (t.days.isEmpty()) "every day" else t.days.sorted().joinToString(" ") { DAY_LABELS[it - 1] }
    return "$start · $length · $days"
}

@Composable
private fun TemplateDialog(
    template: BlockTemplate,
    isNew: Boolean,
    onSave: (BlockTemplate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var t by remember { mutableStateOf(template) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New block" else "Edit block") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t.title, { t = t.copy(title = it) }, label = { Text("Title") }, singleLine = true)

                Label("Area")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(t.category == c, { t = t.copy(category = c) }, { Text("${c.emoji} ${c.label}") })
                    }
                }

                Label("Starts at")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Anchor.entries.forEach { a ->
                        FilterChip(t.anchor == a, { t = t.copy(anchor = a) }, { Text(a.label) })
                    }
                }
                if (t.anchor != Anchor.PREVIOUS) {
                    Stepper("Offset", "${t.offsetMin} min",
                        onMinus = { t = t.copy(offsetMin = t.offsetMin - 5) },
                        onPlus = { t = t.copy(offsetMin = t.offsetMin + 5) })
                }

                Label("Length")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Kind.NORMAL, Kind.FILL, Kind.PRAYER).forEach { k ->
                        FilterChip(t.kind == k, { t = t.copy(kind = k) }, { Text(k.label) })
                    }
                }
                if (t.kind == Kind.NORMAL || t.kind == Kind.PRAYER) {
                    Stepper("Duration", minutesLabel(t.durationMin.toLong()),
                        onMinus = { t = t.copy(durationMin = (t.durationMin - 5).coerceAtLeast(5)) },
                        onPlus = { t = t.copy(durationMin = t.durationMin + 5) })
                } else if (t.kind != Kind.FILL) {
                    Text(t.kind.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Label("Days (none selected = every day)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { i, d ->
                        val day = i + 1
                        FilterChip(day in t.days, {
                            t = t.copy(days = if (day in t.days) t.days - day else t.days + day)
                        }, { Text(d) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = t.title.isNotBlank(), onClick = { onSave(t.copy(title = t.title.trim())) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
