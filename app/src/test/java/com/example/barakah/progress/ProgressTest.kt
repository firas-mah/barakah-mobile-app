package com.example.barakah.progress

import com.example.barakah.model.CurrencyCode
import com.example.barakah.model.DailyLog
import com.example.barakah.model.MoneyEntry
import com.example.barakah.model.MoneyType
import com.example.barakah.model.Product
import com.example.barakah.progress.Progress.Medal
import com.example.barakah.progress.Progress.Metric
import com.example.barakah.progress.Progress.PeriodKey
import com.example.barakah.progress.Progress.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val nov = PeriodKey(PeriodType.MONTH, 2026, 11)
    private val oct = PeriodKey(PeriodType.MONTH, 2026, 10)

    private var n = 0
    private fun income(date: String, amount: Double, project: String = "a", cur: CurrencyCode = CurrencyCode.TND) =
        MoneyEntry("m${n++}", date, project, amount, cur, MoneyType.CLIENT_PAYMENT)

    private fun expense(date: String, amount: Double) =
        MoneyEntry("m${n++}", date, "", amount, CurrencyCode.TND, MoneyType.EXPENSE)

    private fun inputs(
        money: List<MoneyEntry> = emptyList(),
        products: List<Product> = emptyList(),
        logs: Map<String, DailyLog> = emptyMap(),
        followers: Map<String, Double> = emptyMap(),
        days: Map<LocalDate, Progress.DayFacts> = emptyMap()
    ) = Progress.Inputs(money, 3.0, products, logs, followers, days, today)

    // ── Periods ──
    @Test fun periodKeysRoundTrip() {
        assertEquals("2026-11", nov.key)
        assertEquals(nov, PeriodKey.parse("2026-11"))
        assertEquals(PeriodKey(PeriodType.QUARTER, 2026, 4), PeriodKey.parse("2026-Q4"))
        assertEquals(PeriodKey(PeriodType.YEAR, 2026, 0), PeriodKey.parse("2026"))
        assertNull(PeriodKey.parse("2026-13"))
        assertNull(PeriodKey.parse("nope"))
    }

    @Test fun periodBoundaries() {
        assertEquals(PeriodKey(PeriodType.MONTH, 2025, 12), PeriodKey(PeriodType.MONTH, 2026, 1).previous())
        assertEquals(PeriodKey(PeriodType.QUARTER, 2025, 4), PeriodKey(PeriodType.QUARTER, 2026, 1).previous())
        val q4 = PeriodKey.of(PeriodType.QUARTER, today)
        assertEquals(LocalDate.of(2026, 10, 1), q4.start)
        assertEquals(LocalDate.of(2026, 12, 31), q4.end)
        assertEquals(LocalDate.of(2026, 2, 28), PeriodKey(PeriodType.MONTH, 2026, 2).end)
    }

    // ── Money ──
    @Test fun moneyConvertsUsdAndCountsSources() {
        val i = inputs(
            listOf(
                income("2026-11-02", 600.0, "a"),
                income("2026-11-05", 100.0, "b", CurrencyCode.USD), // 300 TND
                expense("2026-11-06", 50.0),
                income("2026-10-30", 999.0, "a") // other month
            )
        )
        val m = Progress.money(i, nov.start, nov.end)
        assertEquals(900.0, m.revenue, 0.001)
        assertEquals(50.0, m.expenses, 0.001)
        assertEquals(850.0, m.net, 0.001)
        assertEquals(2, m.sources)
        assertEquals("a", m.topSource!!.first)
        assertEquals(600.0 / 900.0, m.topSource!!.second, 0.001)
    }

    @Test fun moneyBeforeLedgerStartIsNotLogged() {
        val i = inputs(listOf(income("2026-11-02", 100.0)))
        assertNull(Progress.stats(i, oct)[Metric.NET])
        assertEquals(100.0, Progress.stats(i, nov)[Metric.NET]!!, 0.001)
    }

    @Test fun monthWithoutEntriesAfterLedgerStartIsZero() {
        val i = inputs(listOf(income("2026-09-10", 100.0)))
        assertEquals(0.0, Progress.stats(i, oct)[Metric.REVENUE]!!, 0.001)
    }

    // ── Not logged vs zero ──
    @Test fun noDataMeansEmptyStats() {
        assertTrue(Progress.stats(inputs(), nov).isEmpty())
    }

    @Test fun xMetricsOnlyWhenXLogged() {
        val i = inputs(logs = mapOf("2026-11-03" to DailyLog("2026-11-03", quranPages = 5)))
        val s = Progress.stats(i, nov)
        assertNull(s[Metric.POSTS])
        assertEquals(5.0, s[Metric.QURAN_PAGES]!!, 0.001)
    }

    @Test fun futureDaysAreIgnored() {
        val i = inputs(logs = mapOf(
            "2026-11-03" to DailyLog("2026-11-03", quranPages = 5),
            "2026-11-25" to DailyLog("2026-11-25", quranPages = 50)
        ))
        assertEquals(5.0, Progress.stats(i, nov)[Metric.QURAN_PAGES]!!, 0.001)
    }

    @Test fun checkedDaysGiveHoursAndPrayerDays() {
        val i = inputs(days = mapOf(
            LocalDate.of(2026, 11, 1) to Progress.DayFacts(buildMin = 90, growthMin = 30, built = true, allPrayers = true),
            LocalDate.of(2026, 11, 2) to Progress.DayFacts(allPrayers = false)
        ))
        val s = Progress.stats(i, nov)
        assertEquals(1.5, s[Metric.BUILD_HOURS]!!, 0.001)
        assertEquals(0.5, s[Metric.GROWTH_HOURS]!!, 0.001)
        assertEquals(1.0, s[Metric.BUILD_DAYS]!!, 0.001)
        assertEquals(1.0, s[Metric.PRAYER_DAYS]!!, 0.001)
    }

    @Test fun followersGainedUsesCountBeforeThePeriod() {
        val i = inputs(followers = mapOf("2026-10-28" to 100.0, "2026-11-10" to 130.0, "2026-11-18" to 145.0))
        assertEquals(45.0, Progress.stats(i, nov)[Metric.FOLLOWERS_GAINED]!!, 0.001)
    }

    @Test fun followersNeedTwoPoints() {
        val i = inputs(followers = mapOf("2026-11-10" to 130.0))
        assertNull(Progress.stats(i, nov)[Metric.FOLLOWERS_GAINED])
    }

    @Test fun shippedCountsLaunchDatesInPeriod() {
        val i = inputs(products = listOf(
            Product("1", "Depot", launchedOn = "2026-11-04"),
            Product("2", "Store", launchedOn = "2026-10-04"),
            Product("3", "Idea")
        ))
        assertEquals(1.0, Progress.stats(i, nov)[Metric.SHIPPED]!!, 0.001)
    }

    // ── Medals & lines ──
    @Test fun medals() {
        assertNull(Progress.medal(100.0, 100.0, 200.0))           // equal: not crossed
        assertNull(Progress.medal(100.0, null, null))             // no line yet
        assertEquals(Medal.BRONZE, Progress.medal(110.0, 100.0, 200.0))
        assertEquals(Medal.SILVER, Progress.medal(120.0, 100.0, 200.0))
        assertEquals(Medal.GOLD, Progress.medal(250.0, 100.0, 200.0))
        assertEquals(Medal.GOLD, Progress.medal(5.0, 0.0, 0.0))   // first non-zero month is a record
    }

    @Test fun linesCompareWithPreviousMonthAndBest() {
        val i = inputs(listOf(
            income("2026-09-10", 2000.0),
            income("2026-10-10", 1000.0),
            income("2026-11-10", 1300.0)
        ))
        val line = Progress.lines(i, nov).first { it.metric == Metric.REVENUE }
        assertEquals(1300.0, line.value!!, 0.001)
        assertEquals(1000.0, line.last!!, 0.001)
        assertEquals(2000.0, line.best!!, 0.001)
        assertEquals(Medal.SILVER, line.medal)
        assertTrue(line.crossed)
        assertEquals(0.0, line.toLine!!, 0.001)
    }

    @Test fun toLineWhenBehind() {
        val i = inputs(listOf(income("2026-10-10", 1000.0), income("2026-11-10", 400.0)))
        val line = Progress.lines(i, nov).first { it.metric == Metric.REVENUE }
        assertFalse(line.crossed)
        assertEquals(600.0, line.toLine!!, 0.001)
    }

    // ── Targets ──
    @Test fun suggestedTargetIsAboveBestAndLastPlus20() {
        // last 1000 → 1200; best 2000 → 2100; take the higher, rounded up to 100.
        assertEquals(2100.0, Progress.suggestTarget(Metric.REVENUE, 1000.0, 2000.0)!!, 0.001)
        assertEquals(1200.0, Progress.suggestTarget(Metric.REVENUE, 1000.0, 1000.0)!!, 0.001)
        assertEquals(13.0, Progress.suggestTarget(Metric.POSTS, 10.0, 12.0)!!, 0.001)
        assertNull(Progress.suggestTarget(Metric.POSTS, null, null))
    }
}
