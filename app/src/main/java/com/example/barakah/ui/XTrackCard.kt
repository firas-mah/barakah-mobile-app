package com.example.barakah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.Category
import java.time.LocalDate
import java.util.Locale

/**
 * "📣 X TODAY" card on the Track tab.
 * Every change goes through vm.updateX(), which also marks the day as "logged",
 * so Stats can tell a real 0 apart from "nothing logged".
 */
@Composable
fun XTrackCard(vm: AppViewModel, date: LocalDate) {
    val log = vm.logFor(date)
    val goal = vm.settings.dailyReplyGoal
    val brand = Color(Category.BRAND.color)

    SectionCard(title = "📣 X TODAY") {
        // ── Replies: the main growth engine, so it gets the progress bar and a +5 button ──
        CounterRow(
            label = "💬 Replies",
            value = log.replies,
            onChange = { v -> vm.updateX(date) { it.copy(replies = v) } },
            bigStep = 5
        )
        Spacer(Modifier.height(4.dp))
        ProgressBar(log.replies.toFloat() / goal, brand)
        Text(
            when {
                !log.xLogged -> "Goal: $goal replies · not logged yet"
                log.replies >= goal -> "✓ Goal reached ($goal) — keep the conversations going"
                else -> "${goal - log.replies} to go · goal $goal"
            },
            fontSize = 12.sp,
            color = if (log.replies >= goal) brand else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        CounterRow(
            label = "📝 Posts published",
            value = log.postsPublished,
            onChange = { v -> vm.updateX(date) { it.copy(postsPublished = v) } }
        )
        CounterRow(
            label = "🧑‍💻 New builders followed",
            value = log.newBuilderFollows,
            onChange = { v -> vm.updateX(date) { it.copy(newBuilderFollows = v) } }
        )
        CounterRow(
            label = "🔁 Conversations",
            value = log.conversations,
            onChange = { v -> vm.updateX(date) { it.copy(conversations = v) } }
        )
        Text(
            "Conversation = someone answered your reply.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        FollowersField(vm, date)
    }
}

/** Follower count for the selected day, saved into the existing "followers" history. */
@Composable
private fun FollowersField(vm: AppViewModel, date: LocalDate) {
    val logged = vm.followersOn(date)
    // remember(date): the text resets when you switch day with ‹ Prev / Next ›.
    var text by remember(date, logged) { mutableStateOf(logged?.toString() ?: "") }
    val focus = LocalFocusManager.current
    val lastKnown = vm.latestFollowers

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(9) },
            label = { Text("👥 Followers") },
            placeholder = { Text(if (lastKnown > 0) "last: $lastKnown" else "e.g. 120") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            enabled = text.isNotEmpty() && text.toIntOrNull() != logged,
            onClick = {
                text.toIntOrNull()?.let { vm.setFollowers(it, date) }
                focus.clearFocus() // closes the keyboard
            }
        ) { Text("Save") }
    }
    Text(
        if (logged != null) "Saved for this day: ${String.format(Locale.US, "%,d", logged)}"
        else "Not logged for this day. One number a day is enough.",
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** "label   −  value  +  (+5)" — never goes below 0. */
@Composable
private fun CounterRow(label: String, value: Int, onChange: (Int) -> Unit, bigStep: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SmallButton("−", enabled = value > 0) { onChange((value - 1).coerceAtLeast(0)) }
        Text(value.toString(), Modifier.width(44.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        SmallButton("+") { onChange(value + 1) }
        if (bigStep != null) SmallButton("+$bigStep", width = 52) { onChange(value + bigStep) }
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean = true, width: Int = 40, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(width = width.dp, height = 40.dp)
    ) { Text(text, fontSize = if (text.length > 1) 14.sp else 18.sp) }
}
