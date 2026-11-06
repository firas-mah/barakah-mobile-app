package com.example.barakah.progress

import com.example.barakah.model.CurrencyCode
import com.example.barakah.model.DailyLog
import com.example.barakah.model.MoneyEntry
import com.example.barakah.model.Product
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Monthly / quarterly / yearly progress: "cross last period's line".
 * Pure Kotlin (no Android) so it can be unit-tested on the JVM.
 *
 * Every value is computed from what was logged. A metric with no data for a period
 * is missing from the result ("not logged"), never shown as an invented 0.
 */
object Progress {

    enum class Area(val label: String, val emoji: String, val color: Long) {
        MONEY("Financial", "💰", 0xFFE9A23B),
        TECH("Technical", "🚀", 0xFF4F7CFF),
        BUSINESS("Business", "📈", 0xFF16A3A3),
        DEEN("Deen & balance", "🕌", 0xFF2E9E6B)
    }

    enum class Unit { TND, HOURS, COUNT, SIGNED }

    enum class Metric(val label: String, val emoji: String, val area: Area, val unit: Unit) {
        NET("Net profit", "💰", Area.MONEY, Unit.TND),
        REVENUE("Revenue", "💵", Area.MONEY, Unit.TND),
        SOURCES("Income sources", "🔀", Area.MONEY, Unit.COUNT),
        SHIPPED("Projects shipped / delivered", "📦", Area.TECH, Unit.COUNT),
        BUILD_HOURS("Deep-work hours", "⏱️", Area.TECH, Unit.HOURS),
        BUILD_DAYS("Days you built", "🔨", Area.TECH, Unit.COUNT),
        GROWTH_HOURS("Marketing & brand hours", "📣", Area.BUSINESS, Unit.HOURS),
        POSTS("Posts published", "📝", Area.BUSINESS, Unit.COUNT),
        REPLIES("Replies on X", "💬", Area.BUSINESS, Unit.COUNT),
        FOLLOWERS_GAINED("Followers gained", "👥", Area.BUSINESS, Unit.SIGNED),
        PRAYER_DAYS("Days with 5/5 prayers", "🕌", Area.DEEN, Unit.COUNT),
        QURAN_PAGES("Quran pages", "📖", Area.DEEN, Unit.COUNT),
        SADAQA_DAYS("Sadaqa days", "🤲", Area.DEEN, Unit.COUNT),
        SPORT_DAYS("Sport days", "💪", Area.DEEN, Unit.COUNT);

        companion object {
            fun byName(name: String): Metric? = entries.firstOrNull { it.name == name }
        }
    }

    enum class Medal(val emoji: String, val label: String) {
        BRONZE("🥉", "Beat last period"),
        SILVER("🥈", "+20% over last period"),
        GOLD("🥇", "Personal best")
    }

    // ───────── Periods ─────────
    enum class PeriodType { MONTH, QUARTER, YEAR }

    /** A month (index 1–12), a quarter (index 1–4) or a year (index 0). */
    data class PeriodKey(val type: PeriodType, val year: Int, val index: Int) {
        val start: LocalDate
            get() = when (type) {
                PeriodType.MONTH -> LocalDate.of(year, index, 1)
                PeriodType.QUARTER -> LocalDate.of(year, (index - 1) * 3 + 1, 1)
                PeriodType.YEAR -> LocalDate.of(year, 1, 1)
            }

        val end: LocalDate
            get() = when (type) {
                PeriodType.MONTH -> YearMonth.of(year, index).atEndOfMonth()
                PeriodType.QUARTER -> YearMonth.of(year, index * 3).atEndOfMonth()
                PeriodType.YEAR -> LocalDate.of(year, 12, 31)
            }

        fun previous(): PeriodKey = when (type) {
            PeriodType.MONTH -> if (index == 1) PeriodKey(type, year - 1, 12) else copy(index = index - 1)
            PeriodType.QUARTER -> if (index == 1) PeriodKey(type, year - 1, 4) else copy(index = index - 1)
            PeriodType.YEAR -> copy(year = year - 1)
        }

        fun next(): PeriodKey = when (type) {
            PeriodType.MONTH -> if (index == 12) PeriodKey(type, year + 1, 1) else copy(index = index + 1)
            PeriodType.QUARTER -> if (index == 4) PeriodKey(type, year + 1, 1) else copy(index = index + 1)
            PeriodType.YEAR -> copy(year = year + 1)
        }

        operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

        /** Storage key: "2026-11", "2026-Q4", "2026". */
        val key: String
            get() = when (type) {
                PeriodType.MONTH -> String.format(Locale.US, "%04d-%02d", year, index)
                PeriodType.QUARTER -> "$year-Q$index"
                PeriodType.YEAR -> "$year"
            }

        val label: String
            get() = when (type) {
                PeriodType.MONTH -> start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " $year"
                PeriodType.QUARTER -> "Q$index $year"
                PeriodType.YEAR -> "$year"
            }

        val shortLabel: String
            get() = when (type) {
                PeriodType.MONTH -> start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                PeriodType.QUARTER -> "Q$index"
                PeriodType.YEAR -> "$year"
            }

        companion object {
            fun of(type: PeriodType, date: LocalDate) = when (type) {
                PeriodType.MONTH -> PeriodKey(type, date.year, date.monthValue)
                PeriodType.QUARTER -> PeriodKey(type, date.year, (date.monthValue - 1) / 3 + 1)
                PeriodType.YEAR -> PeriodKey(type, date.year, 0)
            }

            fun parse(key: String): PeriodKey? = runCatching {
                when {
                    Regex("""\d{4}-\d{2}""").matches(key) ->
                        PeriodKey(PeriodType.MONTH, key.take(4).toInt(), key.takeLast(2).toInt()).takeIf { it.index in 1..12 }
                    Regex("""\d{4}-Q[1-4]""").matches(key) ->
                        PeriodKey(PeriodType.QUARTER, key.take(4).toInt(), key.last().digitToInt())
                    Regex("""\d{4}""").matches(key) -> PeriodKey(PeriodType.YEAR, key.toInt(), 0)
                    else -> null
                }
            }.getOrNull()
        }
    }

    // ───────── Inputs ─────────
    /** What happened on one day where at least one block was checked. Built by the ViewModel from the plan. */
    data class DayFacts(
        val buildMin: Int = 0,
        /** Minutes of checked GROW (marketing) and BRAND blocks. */
        val growthMin: Int = 0,
        val built: Boolean = false,
        val allPrayers: Boolean = false
    )

    class Inputs(
        val money: List<MoneyEntry>,
        val usdToTnd: Double,
        val products: List<Product>,
        logs: Map<String, DailyLog>,
        followers: Map<String, Double>,
        /** Only days where something was checked. */
        val days: Map<LocalDate, DayFacts>,
        val today: LocalDate
    ) {
        val logs: List<Pair<LocalDate, DailyLog>> =
            logs.mapNotNull { (k, v) -> parseDate(k)?.let { it to v } }.sortedBy { it.first }
        val followers: List<Pair<LocalDate, Double>> =
            followers.mapNotNull { (k, v) -> parseDate(k)?.let { it to v } }.sortedBy { it.first }
        val moneyDates: List<Pair<LocalDate, MoneyEntry>> =
            money.mapNotNull { m -> parseDate(m.date)?.let { it to m } }
        val launches: List<LocalDate> = products.mapNotNull { parseDate(it.launchedOn) }

        val moneyStart: LocalDate? = moneyDates.minOfOrNull { it.first }
        val checksStart: LocalDate? = days.keys.minOrNull()

        /** First day anything at all was recorded. Periods ending before it have no data. */
        val dataStart: LocalDate? = listOfNotNull(
            moneyStart, checksStart, this.logs.firstOrNull()?.first,
            this.followers.firstOrNull()?.first, launches.minOrNull()
        ).minOrNull()
    }

    fun toTnd(amount: Double, currency: CurrencyCode, usdToTnd: Double): Double =
        if (currency == CurrencyCode.USD) amount * usdToTnd else amount

    fun toTnd(m: MoneyEntry, usdToTnd: Double): Double = toTnd(m.amount, m.currency, usdToTnd)

    // ───────── Money ─────────
    data class MoneySummary(
        val revenue: Double,
        val expenses: Double,
        /** Income per project id (blank = general), in TND. */
        val incomeByProject: Map<String, Double>
    ) {
        val net: Double get() = revenue - expenses
        val sources: Int get() = incomeByProject.count { it.value > 0 }
        /** The biggest source and its share of revenue (0..1), or null with no revenue. */
        val topSource: Pair<String, Double>?
            get() = if (revenue <= 0) null else incomeByProject.maxByOrNull { it.value }?.let { it.key to it.value / revenue }
    }

    fun money(inputs: Inputs, from: LocalDate, to: LocalDate): MoneySummary {
        var revenue = 0.0
        var expenses = 0.0
        val byProject = HashMap<String, Double>()
        inputs.moneyDates.forEach { (d, m) ->
            if (d.isBefore(from) || d.isAfter(to)) return@forEach
            val v = toTnd(m, inputs.usdToTnd)
            if (m.type.isExpense) expenses += v
            else {
                revenue += v
                byProject[m.projectId] = (byProject[m.projectId] ?: 0.0) + v
            }
        }
        return MoneySummary(revenue, expenses, byProject)
    }

    // ───────── Stats for a period ─────────
    /** All metrics for [period]. A missing key means "not logged". */
    fun stats(inputs: Inputs, period: PeriodKey): Map<Metric, Double> {
        val from = period.start
        val to = minOf(period.end, inputs.today)
        val out = LinkedHashMap<Metric, Double>()
        if (inputs.dataStart == null || to.isBefore(inputs.dataStart) || from.isAfter(inputs.today)) return out

        // Money: once you've started the ledger, a month with no entry really is 0.
        if (inputs.moneyStart != null && !to.isBefore(inputs.moneyStart)) {
            val m = money(inputs, from, to)
            out[Metric.NET] = m.net
            out[Metric.REVENUE] = m.revenue
            out[Metric.SOURCES] = m.sources.toDouble()
        }

        out[Metric.SHIPPED] = inputs.launches.count { it in period }.toDouble()

        // Checked blocks: once you've started checking blocks, an unchecked day really is 0.
        if (inputs.checksStart != null && !to.isBefore(inputs.checksStart)) {
            val days = inputs.days.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values
            out[Metric.BUILD_HOURS] = days.sumOf { it.buildMin } / 60.0
            out[Metric.BUILD_DAYS] = days.count { it.built }.toDouble()
            out[Metric.GROWTH_HOURS] = days.sumOf { it.growthMin } / 60.0
            out[Metric.PRAYER_DAYS] = days.count { it.allPrayers }.toDouble()
        }

        val logs = inputs.logs.filter { it.first in period && !it.first.isAfter(to) }.map { it.second }
        val xLogs = logs.filter { it.xLogged }
        if (xLogs.isNotEmpty()) {
            out[Metric.POSTS] = xLogs.sumOf { it.postsPublished }.toDouble()
            out[Metric.REPLIES] = xLogs.sumOf { it.replies }.toDouble()
        }
        if (logs.isNotEmpty()) {
            out[Metric.QURAN_PAGES] = logs.sumOf { it.quranPages }.toDouble()
            out[Metric.SADAQA_DAYS] = logs.count { it.sadaqa }.toDouble()
            out[Metric.SPORT_DAYS] = logs.count { it.sport }.toDouble()
        }

        followersGained(inputs, from, to)?.let { out[Metric.FOLLOWERS_GAINED] = it }
        return out
    }

    /** Last count in the period minus the last count before it (or the first count inside it). */
    private fun followersGained(inputs: Inputs, from: LocalDate, to: LocalDate): Double? {
        val before = inputs.followers.lastOrNull { it.first.isBefore(from) }
        val inside = inputs.followers.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }
        val end = inside.lastOrNull() ?: return null
        val start = before ?: inside.first()
        if (start.first == end.first) return null
        return end.second - start.second
    }

    // ───────── Lines to cross ─────────
    data class Line(
        val metric: Metric,
        val value: Double?,
        /** Previous period's value: the line to cross. */
        val last: Double?,
        /** Best of all earlier periods. */
        val best: Double?,
        val medal: Medal?
    ) {
        val crossed: Boolean get() = medal != null
        /** How much is still missing to reach the line (0 when reached). */
        val toLine: Double? get() = if (value != null && last != null) max(0.0, last - value) else null
    }

    fun medal(value: Double?, last: Double?, best: Double?): Medal? {
        if (value == null || last == null) return null
        if (value <= last) return null
        return when {
            best != null && value > best -> Medal.GOLD
            last > 0 && value >= last * 1.2 -> Medal.SILVER
            else -> Medal.BRONZE
        }
    }

    /** Values of every period before [period], most recent first (stops at the first data). */
    fun history(inputs: Inputs, period: PeriodKey, maxPeriods: Int = 120): List<Map<Metric, Double>> {
        val start = inputs.dataStart ?: return emptyList()
        val out = ArrayList<Map<Metric, Double>>()
        var p = period.previous()
        while (!p.end.isBefore(start) && out.size < maxPeriods) {
            out += stats(inputs, p)
            p = p.previous()
        }
        return out
    }

    fun lines(inputs: Inputs, period: PeriodKey): List<Line> {
        val current = stats(inputs, period)
        val past = history(inputs, period)
        val previous = past.firstOrNull().orEmpty()
        return Metric.entries.map { m ->
            val last = previous[m]
            val best = past.mapNotNull { it[m] }.maxOrNull()
            Line(m, current[m], last, best, medal(current[m], last, best))
        }
    }

    // ───────── Targets ─────────
    /**
     * A harder target for the next period: at least last × 1.2, and above your best.
     * Null when there's nothing to base it on yet.
     */
    fun suggestTarget(metric: Metric, last: Double?, best: Double?): Double? {
        if (last == null && best == null) return null
        val step = stepFor(metric, max(last ?: 0.0, best ?: 0.0))
        val raw = max((last ?: 0.0) * 1.2, (best ?: 0.0) + step)
        if (raw <= 0) return null
        return ceil(raw / step) * step
    }

    fun suggestTarget(inputs: Inputs, metric: Metric, period: PeriodKey): Double? {
        val past = history(inputs, period)
        return suggestTarget(metric, past.firstOrNull()?.get(metric), past.mapNotNull { it[metric] }.maxOrNull())
    }

    private fun stepFor(metric: Metric, base: Double): Double = when (metric.unit) {
        Unit.TND -> when {
            base < 500 -> 50.0
            base < 5000 -> 100.0
            else -> 500.0
        }
        Unit.HOURS -> 1.0
        Unit.COUNT, Unit.SIGNED -> if (base >= 200) 10.0 else 1.0
    }

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
}
