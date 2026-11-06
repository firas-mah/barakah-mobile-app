package com.example.barakah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barakah.model.CurrencyCode
import com.example.barakah.progress.Progress
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDateTime.hm(): String = format(HM)

fun minutesLabel(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

fun minuteOfDayLabel(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

fun countdown(from: LocalDateTime, to: LocalDateTime): String {
    val total = Duration.between(from, to).toMinutes().coerceAtLeast(0)
    return minutesLabel(total)
}

fun money(v: Double): String =
    if (v % 1.0 == 0.0) "$" + String.format(Locale.US, "%,d", v.toLong())
    else "$" + String.format(Locale.US, "%,.2f", v)

/** "1,250 TND" (decimals only when needed). */
fun tnd(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return (if (r % 1.0 == 0.0) String.format(Locale.US, "%,d", r.toLong())
    else String.format(Locale.US, "%,.2f", r)) + " TND"
}

fun amount(v: Double, currency: CurrencyCode): String = when (currency) {
    CurrencyCode.TND -> tnd(v)
    CurrencyCode.USD -> money(v)
}

/** A metric value in its unit: "1,250 TND", "12.5 h", "34", "+12". */
fun metricValue(metric: Progress.Metric, v: Double): String = when (metric.unit) {
    Progress.Unit.TND -> tnd(v)
    Progress.Unit.HOURS -> if (v % 1.0 == 0.0) "${v.toLong()} h" else String.format(Locale.US, "%.1f h", v)
    Progress.Unit.COUNT -> String.format(Locale.US, "%,d", Math.round(v))
    Progress.Unit.SIGNED -> (if (v > 0) "+" else "") + String.format(Locale.US, "%,d", Math.round(v))
}

/** Parses "yyyy-MM-dd", or null. */
fun parseDay(s: String): java.time.LocalDate? = runCatching { java.time.LocalDate.parse(s.trim()) }.getOrNull()

/** "‹  November 2026  ›". [canNext] false hides moving into the future. */
@Composable
fun PeriodSwitcher(
    label: String, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev) { Text("‹", fontSize = 22.sp) }
        Text(
            label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = onNext, enabled = canNext) { Text("›", fontSize = 22.sp) }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun ProgressBar(ratio: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** A "−  value  +" control. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onMinus, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)) { Text("−", fontSize = 18.sp) }
        Text(
            value, Modifier.width(76.dp), fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        FilledTonalButton(onClick = onPlus, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A wrapping row of single-choice chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            androidx.compose.material3.FilterChip(
                selected = o == selected, onClick = { onSelect(o) },
                label = { Text(label(o)) }
            )
        }
    }
}

/** Small grey caption under a row or a field. */
@Composable
fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, fontSize = 12.sp, color = color)
}
