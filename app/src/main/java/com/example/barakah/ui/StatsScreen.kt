package com.example.barakah.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.Category
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(vm: AppViewModel) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Stats", "Last 7 days. Consistency beats intensity.")

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
            TotalRow("💰 MRR", money(vm.totalMrr))
        }

        XWeekCard(x, vm.settings.dailyReplyGoal)

        SectionCard {
            Text(
                "“The most beloved deeds to Allah are those done consistently, even if small.” — Bukhari & Muslim",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(24.dp))
    }
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
