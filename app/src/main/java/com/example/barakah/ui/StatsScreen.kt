package com.example.barakah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.Category
import com.example.barakah.model.Goal
import com.example.barakah.progress.Progress
import com.example.barakah.progress.Progress.Metric
import com.example.barakah.progress.Progress.PeriodKey
import com.example.barakah.progress.Progress.PeriodType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val CROSSED_GREEN = Color(0xFF2E9E6B)

private enum class StatsView(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

@Composable
fun StatsScreen(vm: AppViewModel) {
    var view by rememberSaveable { mutableStateOf(StatsView.MONTH) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Stats",
            when (view) {
                StatsView.WEEK -> "Last 7 days. Consistency beats intensity."
                StatsView.MONTH -> "Cross last month's line. Then raise it."
                StatsView.YEAR -> "Compare yourself to last year, not to others."
            }
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsView.entries.forEach { v ->
                FilterChip(selected = view == v, onClick = { view = v }, label = { Text(v.label) })
            }
        }
        Spacer(Modifier.height(4.dp))

        when (view) {
            StatsView.WEEK -> WeekStats(vm)
            StatsView.MONTH -> PeriodStats(vm, PeriodType.MONTH)
            StatsView.YEAR -> PeriodStats(vm, PeriodType.YEAR)
        }

        SectionCard {
            Text(
                "“The most beloved deeds to Allah are those done consistently, even if small.” — Bukhari & Muslim",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────── Week ─────────────────────────

@Composable
private fun WeekStats(vm: AppViewModel) {
    val today = vm.today()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }

    val prayerStreak = vm.streak { vm.allPrayersDone(it) }
    val buildStreak = vm.streak { vm.categoryDoneAtLeastOnce(it, Category.BUILD) }
    // Reply streak = consecutive days with replies >= your daily goal.
    val replyStreak = vm.streak { vm.replyGoalReached(it) }
    val x = vm.xWeek(week)

    val logs = week.map { vm.logFor(it) }
    val prayersWeek = week.map { vm.prayerScore(it) }
    val prayersDone = prayersWeek.sumOf { it.done }
    val prayersPlanned = prayersWeek.sumOf { it.planned }

    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("🕌 5/5 streak", "$prayerStreak d", Modifier.weight(1f))
        StatTile("🚀 Build streak", "$buildStreak d", Modifier.weight(1f), Color(Category.BUILD.color))
        StatTile("💬 Reply streak", "$replyStreak d", Modifier.weight(1f), Color(Category.BRAND.color))
    }
    Spacer(Modifier.height(8.dp))

    SectionCard(title = "BLOCKS DONE PER DAY") {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEach { d ->
                val s = vm.dayScore(d)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(s.ratio * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(s.ratio.coerceIn(0.03f, 1f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (d == today) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                        fontSize = 11.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    SectionCard(title = "THIS WEEK BY AREA") {
        Category.entries.filter { it != Category.REST }.forEach { cat ->
            val s = vm.categoryScore(cat, week)
            if (s.planned == 0) return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${cat.emoji} ${cat.label}", Modifier.weight(0.38f), fontSize = 14.sp)
                ProgressBar(s.ratio, Color(cat.color), Modifier.weight(0.42f))
                Text("${(s.ratio * 100).toInt()}%", Modifier.weight(0.2f), fontSize = 13.sp, textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    SectionCard(title = "WEEK TOTALS") {
        TotalRow("🕌 Prayers on time", "$prayersDone / $prayersPlanned")
        TotalRow("📖 Quran pages", logs.sumOf { it.quranPages }.toString())
        TotalRow("💪 Sport days", logs.count { it.sport }.toString())
        TotalRow("🤲 Sadaqa days", logs.count { it.sadaqa }.toString())
        TotalRow("💰 Net (7 days)", tnd(vm.moneySummary(week.first(), week.last()).net))
    }

    XWeekCard(x, vm.settings.dailyReplyGoal)
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Weekly X totals. Shows "log your numbers" instead of zeros when nothing was logged. */
@Composable
private fun XWeekCard(x: AppViewModel.XWeek, goal: Int) {
    SectionCard(title = "📣 X THIS WEEK") {
        if (x.loggedDays == 0) {
            Text(
                "No X numbers logged this week. Log your replies and posts in the Track tab — " +
                    "the totals will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
            )
        } else {
            TotalRow("💬 Replies", "${x.replies}  (${x.daysAtGoal}/7 days ≥ $goal)")
            TotalRow("📝 Posts", x.posts.toString())
            TotalRow("🔁 Conversations", x.conversations.toString())
            TotalRow("🧑‍💻 Builders followed", x.newBuilderFollows.toString())
            Text(
                "Based on ${x.loggedDays} logged day${if (x.loggedDays > 1) "s" else ""}.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
        }
        val delta = x.followerDelta
        TotalRow(
            "👥 Followers",
            if (delta == null) "log your numbers"
            else "${x.followersStart} → ${x.followersEnd}  (${if (delta >= 0) "+" else ""}$delta)"
        )
        if (delta == null) {
            Text(
                "Save your follower count on at least 2 different days to see the change.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────── Month / Year ─────────────────────────

private fun daysLeft(today: LocalDate, p: PeriodKey): Long? =
    if (today in p) ChronoUnit.DAYS.between(today, p.end) + 1 else null

@Composable
private fun PeriodStats(vm: AppViewModel, type: PeriodType) {
    val today = vm.today()
    val current = PeriodKey.of(type, today)
    var period by remember(type) { mutableStateOf(current) }
    val key = vm.progressKey
    val inputs = remember(key) { vm.progressInputs() }
    val lines = remember(key, period) { Progress.lines(inputs, period) }
    val prev = period.previous()

    PeriodSwitcher(
        period.label, { period = period.previous() }, { period = period.next() }, period != current,
        Modifier.padding(horizontal = 16.dp)
    )

    // ── Header: lines crossed ──
    val comparable = lines.filter { it.value != null && it.last != null }
    val crossed = comparable.count { it.crossed }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (comparable.isEmpty()) "No line to cross yet" else "Lines crossed: $crossed / ${comparable.size}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                val left = daysLeft(today, period)
                Caption(
                    if (comparable.isEmpty()) "This ${type.name.lowercase()} sets the lines for the next one. Log everything."
                    else "vs ${prev.label}" + if (left != null) " · $left day${if (left > 1) "s" else ""} left" else ""
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Progress.Medal.entries.reversed().forEach { m ->
                    val n = lines.count { it.medal == m }
                    if (n > 0) Text("${m.emoji} × $n", fontSize = 14.sp)
                }
            }
        }
        if (comparable.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ProgressBar(crossed.toFloat() / comparable.size, CROSSED_GREEN)
            Spacer(Modifier.height(4.dp))
            Caption("🥉 beat ${prev.shortLabel} · 🥈 +20% · 🥇 personal best")
        }
    }

    if (type == PeriodType.YEAR) YearChart(inputs, period, key)

    GoalsCard(vm, inputs, period)

    Progress.Area.entries.forEach { area ->
        SectionCard(title = "${area.emoji} ${area.label.uppercase()}") {
            lines.filter { it.metric.area == area }.forEach { LineRow(it, prev.shortLabel, Color(area.color)) }
            if (area == Progress.Area.MONEY) {
                val m = Progress.money(inputs, period.start, minOf(period.end, today))
                if (m.revenue > 0 || m.expenses > 0) {
                    val top = m.topSource
                    Caption(
                        "Expenses ${tnd(m.expenses)}" + if (top != null)
                            " · biggest source ${vm.projectName(top.first)} (${(top.second * 100).toInt()}%)" else ""
                    )
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: Progress.Line, prevLabel: String, color: Color) {
    val m = line.metric
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.emoji} ${m.label}", Modifier.weight(1f), fontSize = 14.sp)
            Text(
                line.value?.let { metricValue(m, it) } ?: "not logged",
                fontWeight = if (line.value != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (line.value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Text(line.medal?.emoji ?: "", Modifier.width(28.dp), textAlign = TextAlign.End)
        }
        val value = line.value ?: return@Column
        val last = line.last
        if (last == null) {
            Caption("No line yet — this one sets it.")
            return@Column
        }
        val ratio = when {
            last > 0 -> (value / last).toFloat()
            value > last -> 1f
            else -> 0f
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(ratio, if (line.crossed) CROSSED_GREEN else color, height = 6)
        Spacer(Modifier.height(2.dp))
        val best = line.best?.let { " · best ${metricValue(m, it)}" }.orEmpty()
        Caption(
            when {
                line.crossed -> "✓ crossed $prevLabel (${metricValue(m, last)})$best"
                line.toLine == 0.0 -> "$prevLabel: ${metricValue(m, last)} · equal — one more to cross"
                else -> "$prevLabel: ${metricValue(m, last)} · ${metricValue(m, line.toLine ?: 0.0).removePrefix("+")} to go$best"
            }
        )
    }
}

// ───────────────────────── Year chart ─────────────────────────

private fun compact(v: Double): String = when {
    abs(v) >= 1000 -> String.format(Locale.US, "%.1fk", v / 1000).replace(".0k", "k")
    else -> Math.round(v).toString()
}

@Composable
private fun YearChart(inputs: Progress.Inputs, year: PeriodKey, key: Any) {
    val months = (1..12).map { PeriodKey(PeriodType.MONTH, year.year, it) }
    val data = remember(key, year) {
        months.map { p ->
            val started = inputs.moneyStart != null && !p.end.isBefore(inputs.moneyStart)
            val net = if (started && !p.start.isAfter(inputs.today)) Progress.money(inputs, p.start, p.end).net else null
            val lines = if (p.start.isAfter(inputs.today)) emptyList() else Progress.lines(inputs, p)
            val comparable = lines.filter { it.value != null && it.last != null }
            Triple(net, comparable.count { it.crossed }, comparable.size)
        }
    }
    val maxAbs = data.mapNotNull { it.first?.let(::abs) }.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    SectionCard(title = "💰 NET PROFIT BY MONTH (TND)") {
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            months.forEachIndexed { i, p ->
                val net = data[i].first
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(net?.let(::compact) ?: "", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (net != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((abs(net) / maxAbs).toFloat().coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (net < 0) Color(0xFFE5534B) else CROSSED_GREEN)
                            )
                        }
                    }
                    Text(p.shortLabel.take(1), fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Caption("Red = month with a loss.")
    }

    SectionCard(title = "LINES CROSSED PER MONTH") {
        months.forEachIndexed { i, p ->
            val (_, crossed, total) = data[i]
            if (total == 0) return@forEachIndexed
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.shortLabel, Modifier.width(44.dp), fontSize = 13.sp)
                ProgressBar(crossed.toFloat() / total, CROSSED_GREEN, Modifier.weight(1f))
                Text("$crossed / $total", Modifier.width(56.dp), fontSize = 13.sp, textAlign = TextAlign.End)
            }
        }
        if (data.all { it.third == 0 }) Caption("Nothing to compare yet in ${year.label}.")
    }
}

// ───────────────────────── Goals ─────────────────────────

@Composable
private fun GoalsCard(vm: AppViewModel, inputs: Progress.Inputs, period: PeriodKey) {
    var editing by remember { mutableStateOf<Goal?>(null) }
    val today = vm.today()

    SectionCard(title = "🎯 GOALS") {
        val goals = vm.goalsCovering(period)
        if (goals.isEmpty()) {
            Caption("No goal for ${period.label}. Set one above your best — that's how you grow.")
        }
        goals.forEach { (g, p) ->
            val metric = Metric.byName(g.metric) ?: return@forEach
            val value = Progress.stats(inputs, p)[metric]
            Column(Modifier.fillMaxWidth().clickable { editing = g }.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${metric.emoji} ${metric.label} ≥ ${metricValue(metric, g.target)}", Modifier.weight(1f), fontSize = 14.sp)
                    Text(p.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                val reached = value != null && value >= g.target
                ProgressBar(((value ?: 0.0) / g.target).toFloat(), if (reached) CROSSED_GREEN else Color(metric.area.color), height = 6)
                Spacer(Modifier.height(2.dp))
                val left = daysLeft(today, p)
                Caption(
                    when {
                        value == null -> "Not logged yet."
                        reached -> "✅ Reached: ${metricValue(metric, value)}"
                        else -> "${metricValue(metric, value)} / ${metricValue(metric, g.target)} · " +
                            "${metricValue(metric, g.target - value).removePrefix("+")} to go" +
                            if (left != null) " · $left d left" else ""
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { editing = Goal(vm.newId(), period.key, Metric.NET.name, 0.0) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Goal") }
    }

    editing?.let { g ->
        GoalDialog(
            goal = g,
            isNew = vm.goals.none { it.id == g.id },
            inputs = inputs,
            today = today,
            onSave = { vm.saveGoal(it); editing = null },
            onDelete = { vm.deleteGoal(g.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun GoalDialog(
    goal: Goal,
    isNew: Boolean,
    inputs: Progress.Inputs,
    today: LocalDate,
    onSave: (Goal) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val thisMonth = PeriodKey.of(PeriodType.MONTH, today)
    val options = (listOf(
        thisMonth, thisMonth.next(),
        PeriodKey.of(PeriodType.QUARTER, today), PeriodKey.of(PeriodType.YEAR, today)
    ) + listOfNotNull(PeriodKey.parse(goal.period))).distinct()

    var period by remember { mutableStateOf(PeriodKey.parse(goal.period) ?: thisMonth) }
    var metric by remember { mutableStateOf(Metric.byName(goal.metric) ?: Metric.NET) }
    val suggestion = remember(period, metric) { Progress.suggestTarget(inputs, metric, period) }
    var targetText by remember {
        mutableStateOf(
            if (!isNew) plain(goal.target)
            else Progress.suggestTarget(inputs, metric, period)?.let(::plain).orEmpty()
        )
    }
    var touched by remember { mutableStateOf(!isNew) }
    fun refill(p: PeriodKey, m: Metric) {
        if (!touched) targetText = Progress.suggestTarget(inputs, m, p)?.let(::plain).orEmpty()
    }
    val target = targetText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Period")
                ChipRow(options, period, { it.label }) { period = it; refill(it, metric) }
                Caption("What to measure (computed from what you log)")
                ChipRow(Metric.entries, metric, { "${it.emoji} ${it.label}" }) { metric = it; refill(period, it) }
                OutlinedTextField(
                    targetText, { targetText = it.filter { c -> c.isDigit() || c == '.' }; touched = true },
                    label = { Text("Target" + if (metric.unit == Progress.Unit.TND) " (TND)" else if (metric.unit == Progress.Unit.HOURS) " (hours)" else "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (suggestion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Caption("Suggested: ${metricValue(metric, suggestion)}")
                            Caption("Above your best and +20% on last ${period.type.name.lowercase()}.")
                        }
                        TextButton(onClick = { targetText = plain(suggestion); touched = true }) { Text("Use") }
                    }
                } else {
                    Caption("No history yet for this — pick a target that scares you a little.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && target > 0,
                onClick = { onSave(goal.copy(period = period.key, metric = metric.name, target = target ?: 0.0)) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
