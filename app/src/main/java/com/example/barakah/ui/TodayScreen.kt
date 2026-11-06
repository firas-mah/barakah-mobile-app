package com.example.barakah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.coach.Coach
import com.example.barakah.coach.Reminders
import com.example.barakah.model.Block
import com.example.barakah.model.Kind
import com.example.barakah.prayer.Prayer
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEADER_ITEMS = 6

@Composable
fun TodayScreen(vm: AppViewModel) {
    val now by produceState(vm.now()) {
        while (true) {
            delay(30_000)
            value = vm.now()
        }
    }
    val today = now.toLocalDate()
    val version = vm.planVersion
    val blocks = remember(today, version) { vm.blocksFor(today) }
    val currentIndex = blocks.indexOfFirst { !now.isBefore(it.start) && now.isBefore(it.end) }
    val listState = rememberLazyListState()

    LaunchedEffect(today, version) {
        if (currentIndex > 1) listState.scrollToItem(currentIndex + HEADER_ITEMS - 1)
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TodayHeader(today) }
        item { NextPrayerCard(vm, now) }
        item { NowCard(vm, blocks, now) }
        item { FocusCard(vm, today) }
        item { TipCard(today) }
        item {
            Text(
                "Timeline", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
            )
        }
        itemsIndexed(blocks, key = { _, b -> b.templateId + b.start }) { i, block ->
            BlockRow(
                block = block,
                isCurrent = i == currentIndex,
                isPast = !now.isBefore(block.end),
                done = vm.isDone(today, block.templateId),
                onToggle = { vm.toggleDone(today, block.templateId) }
            )
        }
    }
}

@Composable
private fun TodayHeader(today: LocalDate) {
    val hijri = remember(today) {
        runCatching {
            HijrahDate.from(today).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)) + " AH"
        }.getOrDefault("")
    }
    ScreenTitle(
        title = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)),
        subtitle = hijri
    )
}

@Composable
private fun NextPrayerCard(vm: AppViewModel, now: LocalDateTime) {
    val today = now.toLocalDate()
    val p = vm.prayers(today)
    val friday = today.dayOfWeek == DayOfWeek.FRIDAY
    val list = listOf(
        "Fajr" to p.getValue(Prayer.FAJR),
        (if (friday) "Jumu'ah" else "Dhuhr") to p.getValue(Prayer.DHUHR),
        "Asr" to p.getValue(Prayer.ASR),
        "Maghrib" to p.getValue(Prayer.MAGHRIB),
        "Isha" to p.getValue(Prayer.ISHA)
    )
    val next = list.firstOrNull { it.second.isAfter(now) }
        ?: ("Fajr" to vm.prayers(today.plusDays(1)).getValue(Prayer.FAJR))

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next prayer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${next.first} · ${next.second.hm()}",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "in ${countdown(now, next.second)}",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            list.forEach { (name, time) ->
                val passed = !time.isAfter(now)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (passed) 0.45f else 1f)) {
                    Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(time.hm(), fontSize = 14.sp, fontWeight = if (name == next.first) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (friday) "🕌 Jumu'ah khutba ${vm.jumuahTime(today).hm()} · be at the mosque by " +
                vm.jumuahTime(today).minusMinutes(vm.settings.jumuahArriveMin.toLong()).hm()
            else "🕌 Dhuhr iqama ${vm.dhuhrIqama(today).hm()}" +
                if (vm.staysUntilAsr(today)) " · stay for Asr ${p.getValue(Prayer.ASR).hm()}" else "",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NowCard(vm: AppViewModel, blocks: List<Block>, now: LocalDateTime) {
    val today = now.toLocalDate()
    val current = blocks.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    val score = vm.dayScore(today)
    val prayers = vm.prayerScore(today)
    val summer = vm.isSummer(today)

    SectionCard(title = "NOW") {
        when {
            current != null -> {
                Text("${current.category.emoji}  ${current.title}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "until ${current.end.hm()} · ${countdown(now, current.end)} left",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                val total = current.minutes.coerceAtLeast(1)
                val elapsed = java.time.Duration.between(current.start, now).toMinutes()
                ProgressBar(elapsed.toFloat() / total, Color(current.category.color))
                if (vm.settings.faithReminders && !current.isFree) {
                    val r = Reminders.forBlock(current, today, vm.staysUntilAsr(today))
                    Spacer(Modifier.height(10.dp))
                    // Arabic is right-to-left: force RTL so the text starts on the right.
                    Text(
                        r.body,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start),
                        fontSize = 17.sp, lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        r.reference,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            blocks.isNotEmpty() && now.isBefore(blocks.first().start) ->
                Text("🌙  Sleep — your day starts at ${blocks.first().start.hm()}", fontSize = 16.sp)
            else -> Text("🌙  Rest. Tomorrow is a new day.", fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("✅ ${score.done}/${score.planned} blocks", fontSize = 13.sp)
            Text("🕌 ${prayers.done}/${prayers.planned} prayers", fontSize = 13.sp)
            Text(
                "🤝 ${minutesLabel(vm.friendsCap(today).toLong())} ${if (summer) "(summer)" else "(winter)"}",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun BlockRow(block: Block, isCurrent: Boolean, isPast: Boolean, done: Boolean, onToggle: () -> Unit) {
    val color = Color(block.category.color)
    val shape = RoundedCornerShape(16.dp)
    val bg = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .background(bg)
            .then(if (isCurrent) Modifier.border(1.dp, color, shape) else Modifier)
            .clickable(enabled = block.checkable, onClick = onToggle)
            .alpha(if (isPast && !done && !isCurrent) 0.5f else 1f)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(52.dp)) {
            Text(block.start.hm(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(block.end.hm(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${block.category.emoji}  ${block.title}",
                fontSize = 15.sp,
                fontWeight = if (block.kind == Kind.PRAYER) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                minutesLabel(block.minutes) + if (isCurrent) "  ·  now" else "",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (block.checkable) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun FocusCard(vm: AppViewModel, today: LocalDate) {
    val log = vm.logFor(today)
    SectionCard(title = "🎯 TODAY'S ONE THING") {
        androidx.compose.material3.OutlinedTextField(
            value = log.focus,
            onValueChange = { v -> vm.updateLog(today) { it.copy(focus = v) } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("If I only finish this today, the day is a win…") },
            singleLine = false,
            maxLines = 3
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Make it concrete and shippable: \"Stripe checkout works\", not \"work on the app\".",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TipCard(today: LocalDate) {
    val tip = remember(today) { Coach.tipFor(today) }
    val note = remember(today) { Coach.dayNote(today) }
    SectionCard(title = "💡 TIP OF THE DAY") {
        Text(tip.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(4.dp))
        Text(tip.body, fontSize = 14.sp)
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}
