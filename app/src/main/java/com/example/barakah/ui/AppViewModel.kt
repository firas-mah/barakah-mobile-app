package com.example.barakah.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.barakah.data.Store
import com.example.barakah.model.Block
import com.example.barakah.model.BlockTemplate
import com.example.barakah.model.Category
import com.example.barakah.model.DailyLog
import com.example.barakah.model.Kind
import com.example.barakah.model.Product
import com.example.barakah.model.Settings
import com.example.barakah.notify.AlarmScheduler
import com.example.barakah.schedule.DefaultTemplate
import com.example.barakah.schedule.ScheduleEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var settings by mutableStateOf(store.loadSettings())
        private set
    var templates by mutableStateOf(store.loadTemplates())
        private set
    var completions by mutableStateOf(store.loadCompletions())
        private set
    var logs by mutableStateOf(store.loadLogs())
        private set
    var products by mutableStateOf(store.loadProducts())
        private set
    var mrrHistory by mutableStateOf(store.loadMetric("mrr"))
        private set
    var followersHistory by mutableStateOf(store.loadMetric("followers"))
        private set

    /** Bumped whenever the plan changes, so screens rebuild their timeline. */
    var planVersion by mutableStateOf(0)
        private set

    private var engine = ScheduleEngine(settings, templates)
    private val blockCache = HashMap<LocalDate, List<Block>>()

    val zone: ZoneId get() = ZoneId.of(settings.zoneId)
    fun now(): LocalDateTime = LocalDateTime.now(zone)
    fun today(): LocalDate = now().toLocalDate()

    // ───────── Plan ─────────
    fun blocksFor(date: LocalDate): List<Block> = blockCache.getOrPut(date) { engine.build(date) }
    fun prayers(date: LocalDate) = engine.prayers(date)
    fun isSummer(date: LocalDate) = engine.isSummer(date)
    fun friendsCap(date: LocalDate) = engine.friendsCapMin(date)
    fun night(date: LocalDate) = engine.night(date)
    fun dhuhrIqama(date: LocalDate) = engine.dhuhrIqama(date)
    fun jumuahTime(date: LocalDate) = engine.jumuahTime(date)
    fun staysUntilAsr(date: LocalDate) = engine.staysUntilAsr(date)

    private fun planChanged(reschedule: Boolean = true) {
        engine = ScheduleEngine(settings, templates)
        blockCache.clear()
        planVersion++
        if (reschedule) AlarmScheduler.reschedule(getApplication())
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        store.saveSettings(settings)
        planChanged()
    }

    fun saveTemplate(t: BlockTemplate) {
        templates = if (templates.any { it.id == t.id }) templates.map { if (it.id == t.id) t else it }
        else templates + t
        store.saveTemplates(templates)
        planChanged()
    }

    fun newTemplateId(): String = "custom_" + UUID.randomUUID().toString().take(8)

    fun deleteTemplate(id: String) {
        templates = templates.filterNot { it.id == id }
        store.saveTemplates(templates)
        planChanged()
    }

    fun moveTemplate(id: String, up: Boolean) {
        val i = templates.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in templates.indices) return
        val list = templates.toMutableList()
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        templates = list
        store.saveTemplates(templates)
        planChanged()
    }

    fun resetTemplates() {
        store.resetTemplates()
        templates = DefaultTemplate.blocks
        planChanged()
    }

    fun refreshAlarms() = AlarmScheduler.reschedule(getApplication())

    // ───────── Done / not done ─────────
    fun isDone(date: LocalDate, templateId: String) =
        completions[date.toString()]?.contains(templateId) == true

    fun toggleDone(date: LocalDate, templateId: String) {
        val key = date.toString()
        val current = completions[key].orEmpty()
        val updated = if (templateId in current) current - templateId else current + templateId
        completions = completions + (key to updated)
        store.saveCompletions(completions)
    }

    // ───────── Daily log ─────────
    fun logFor(date: LocalDate): DailyLog = logs[date.toString()] ?: DailyLog(date.toString())

    fun updateLog(date: LocalDate, transform: (DailyLog) -> DailyLog) {
        logs = logs + (date.toString() to transform(logFor(date)))
        store.saveLogs(logs)
    }

    // ───────── Products & money ─────────
    val totalMrr: Double get() = products.sumOf { it.mrr }

    fun saveProduct(input: Product) {
        // Remember the day a product is first launched (used for "ship regularly" warnings).
        val p = if (input.status.shipped && input.launchedOn.isBlank()) input.copy(launchedOn = today().toString()) else input
        products = if (products.any { it.id == p.id }) products.map { if (it.id == p.id) p else it }
        else products + p
        store.saveProducts(products)
        recordMrr()
    }

    fun deleteProduct(id: String) {
        products = products.filterNot { it.id == id }
        store.saveProducts(products)
        recordMrr()
    }

    fun newProductId(): String = UUID.randomUUID().toString()

    private fun recordMrr() {
        mrrHistory = mrrHistory + (today().toString() to totalMrr)
        store.saveMetric("mrr", mrrHistory)
    }

    /** Saves the follower count for [date] (today by default). One value per day; saving again overwrites it. */
    fun setFollowers(count: Int, date: LocalDate = today()) {
        followersHistory = followersHistory + (date.toString() to count.toDouble())
        store.saveMetric("followers", followersHistory)
    }

    /** The follower count you logged on exactly that day, or null if you didn't log one. */
    fun followersOn(date: LocalDate): Int? = followersHistory[date.toString()]?.toInt()

    val latestFollowers: Int get() = followersHistory.maxByOrNull { it.key }?.value?.toInt() ?: 0

    // ───────── X growth ─────────
    /** Changes one X number for [date] and marks the day as "logged". */
    fun updateX(date: LocalDate, transform: (DailyLog) -> DailyLog) =
        updateLog(date) { transform(it).copy(xLogged = true) }

    fun replyGoalReached(date: LocalDate): Boolean = logFor(date).replies >= settings.dailyReplyGoal

    data class XWeek(
        val loggedDays: Int,
        val replies: Int,
        val posts: Int,
        val conversations: Int,
        val newBuilderFollows: Int,
        val daysAtGoal: Int,
        /** Null when there aren't two follower counts to compare. */
        val followersStart: Int?,
        val followersEnd: Int?
    ) {
        val followerDelta: Int? get() = if (followersStart != null && followersEnd != null) followersEnd - followersStart else null
    }

    /** Totals for the given days. Only days where you logged X numbers are counted. */
    fun xWeek(days: List<LocalDate>): XWeek {
        val logged = days.map { logFor(it) }.filter { it.xLogged }
        val sorted = followersHistory.toSortedMap()
        val first = days.first().toString()
        val last = days.last().toString()
        // Start = last count logged before the week, or else the first count inside the week.
        val start = sorted.headMap(first).values.lastOrNull()
            ?: sorted.subMap(first, last + "~").values.firstOrNull()
        val startKey = sorted.headMap(first).keys.lastOrNull()
            ?: sorted.subMap(first, last + "~").keys.firstOrNull()
        val endEntry = sorted.subMap(first, last + "~").entries.lastOrNull()
        val hasTwo = startKey != null && endEntry != null && endEntry.key != startKey
        return XWeek(
            loggedDays = logged.size,
            replies = logged.sumOf { it.replies },
            posts = logged.sumOf { it.postsPublished },
            conversations = logged.sumOf { it.conversations },
            newBuilderFollows = logged.sumOf { it.newBuilderFollows },
            daysAtGoal = logged.count { it.replies >= settings.dailyReplyGoal },
            followersStart = if (hasTwo) start?.toInt() else null,
            followersEnd = if (hasTwo) endEntry?.value?.toInt() else null
        )
    }

    // ───────── Stats ─────────
    data class Score(val done: Int, val planned: Int) {
        val ratio: Float get() = if (planned == 0) 0f else done.toFloat() / planned
    }

    fun prayerScore(date: LocalDate): Score {
        val prayers = blocksFor(date).filter { it.kind == Kind.PRAYER }
        return Score(prayers.count { isDone(date, it.templateId) }, prayers.size)
    }

    fun dayScore(date: LocalDate): Score {
        val blocks = blocksFor(date).filter { it.checkable }
        return Score(blocks.count { isDone(date, it.templateId) }, blocks.size)
    }

    fun categoryScore(category: Category, dates: List<LocalDate>): Score {
        var done = 0
        var planned = 0
        dates.forEach { d ->
            blocksFor(d).filter { it.checkable && it.category == category }.forEach { b ->
                planned++
                if (isDone(d, b.templateId)) done++
            }
        }
        return Score(done, planned)
    }

    /** Consecutive days (ending today, or yesterday if today isn't finished yet) where [ok] is true. */
    fun streak(ok: (LocalDate) -> Boolean): Int {
        var d = today()
        if (!ok(d)) d = d.minusDays(1)
        var n = 0
        while (ok(d) && n < 3650) { n++; d = d.minusDays(1) }
        return n
    }

    fun allPrayersDone(date: LocalDate): Boolean {
        val s = prayerScore(date)
        return s.planned > 0 && s.done == s.planned
    }

    fun categoryDoneAtLeastOnce(date: LocalDate, category: Category): Boolean =
        blocksFor(date).any { it.category == category && it.checkable && isDone(date, it.templateId) }
}
