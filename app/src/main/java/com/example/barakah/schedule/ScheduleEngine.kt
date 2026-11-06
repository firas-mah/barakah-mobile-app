package com.example.barakah.schedule

import com.example.barakah.model.Anchor
import com.example.barakah.model.Block
import com.example.barakah.model.BlockTemplate
import com.example.barakah.model.Category
import com.example.barakah.model.DhuhrMode
import com.example.barakah.model.Kind
import com.example.barakah.model.Settings
import com.example.barakah.prayer.Prayer
import com.example.barakah.prayer.PrayerCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Turns the week template + today's prayer times into a concrete timeline.
 * Everything is recomputed per date, so the day follows the prayers all year.
 */
class ScheduleEngine(
    private val settings: Settings,
    private val templates: List<BlockTemplate>
) {
    private val prayerCache = HashMap<LocalDate, Map<Prayer, LocalDateTime>>()

    fun prayers(date: LocalDate): Map<Prayer, LocalDateTime> =
        prayerCache.getOrPut(date) { PrayerCalculator.compute(date, settings.prayerConfig()) }

    fun isSummer(date: LocalDate): Boolean {
        val isha = prayers(date).getValue(Prayer.ISHA)
        return isha.toLocalTime().toSecondOfDay() / 60 >= settings.summerIshaThresholdMin
    }

    fun friendsCapMin(date: LocalDate): Int =
        if (isSummer(date)) settings.summerFriendsMin else settings.winterFriendsMin

    /** Bedtime = your base bedtime, pushed later only if friends time after Isha needs it. */
    fun bedtime(date: LocalDate): LocalDateTime {
        val base = settings.baseBedtimeMin
        val baseTime = if (base < 12 * 60) date.plusDays(1).atStartOfDay().plusMinutes(base.toLong())
        else date.atStartOfDay().plusMinutes(base.toLong())
        val needed = prayers(date).getValue(Prayer.ISHA)
            .plusMinutes(15L + friendsCapMin(date) + REVIEW_MIN)
        return if (needed.isAfter(baseTime)) needed else baseTime
    }

    data class Night(val bedtime: LocalDateTime, val wake: LocalDateTime, val tahajjud: Boolean) {
        /** Real sleep: time in bed minus the time it takes to fall asleep. */
        val sleepMin: Long get() = (Duration.between(bedtime, wake).toMinutes() - FALL_ASLEEP_MIN).coerceAtLeast(0)
    }

    /** The night that ENDS on the morning of [date]. */
    fun night(date: LocalDate): Night {
        val bed = bedtime(date.minusDays(1))
        val fajr = prayers(date).getValue(Prayer.FAJR)
        val tahajjudTpl = active(date).firstOrNull { it.kind == Kind.TAHAJJUD }
        if (tahajjudTpl != null) {
            val wake = fajr.plusMinutes(tahajjudTpl.offsetMin.toLong())
            if (Duration.between(bed, wake).toMinutes() - FALL_ASLEEP_MIN >= MIN_SLEEP_FOR_TAHAJJUD) {
                return Night(bed, wake, tahajjud = true)
            }
        }
        return Night(bed, fajr.minusMinutes(10), tahajjud = false)
    }

    private fun active(date: LocalDate): List<BlockTemplate> {
        val dow = date.dayOfWeek.value
        return templates.filter { it.enabled && (it.days.isEmpty() || dow in it.days) }
    }

    // ───────── Mosque times ─────────

    /** Dhuhr iqama at your mosque: "Asr − 30 min" (Kebili) or a fixed time (13:00). Never before the adhan. */
    fun dhuhrIqama(date: LocalDate): LocalDateTime {
        val p = prayers(date)
        val iqama = when (settings.dhuhrMode) {
            DhuhrMode.BEFORE_ASR -> p.getValue(Prayer.ASR).minusMinutes(settings.dhuhrBeforeAsrMin.toLong())
            DhuhrMode.FIXED -> date.atStartOfDay().plusMinutes(settings.dhuhrFixedMin.toLong())
        }
        val earliest = p.getValue(Prayer.DHUHR).plusMinutes(5)
        return if (iqama.isBefore(earliest)) earliest else iqama
    }

    /** Start of the Jumu'ah khutba (13:00 by default). Never before the Dhuhr adhan. */
    fun jumuahTime(date: LocalDate): LocalDateTime {
        val t = date.atStartOfDay().plusMinutes(settings.jumuahMin.toLong())
        val adhan = prayers(date).getValue(Prayer.DHUHR)
        return if (t.isBefore(adhan)) adhan else t
    }

    /** True when you pray Dhuhr at the mosque and stay there until Asr (not on Friday). */
    fun staysUntilAsr(date: LocalDate): Boolean =
        settings.dhuhrMode == DhuhrMode.BEFORE_ASR && date.dayOfWeek != DayOfWeek.FRIDAY

    /** Where a block that is NOT "after previous" starts. */
    private fun anchorTime(t: BlockTemplate, date: LocalDate): LocalDateTime? {
        val offset = t.offsetMin.toLong()
        return when (t.anchor) {
            Anchor.PREVIOUS -> null
            Anchor.BEDTIME -> bedtime(date).plusMinutes(offset)
            Anchor.FAJR -> prayers(date).getValue(Prayer.FAJR).plusMinutes(offset)
            Anchor.SUNRISE -> prayers(date).getValue(Prayer.SUNRISE).plusMinutes(offset)
            Anchor.DHUHR -> prayers(date).getValue(Prayer.DHUHR).plusMinutes(offset)
            Anchor.ASR -> prayers(date).getValue(Prayer.ASR).plusMinutes(offset)
            Anchor.MAGHRIB -> prayers(date).getValue(Prayer.MAGHRIB).plusMinutes(offset)
            Anchor.ISHA -> prayers(date).getValue(Prayer.ISHA).plusMinutes(offset)
            // A prayer block starts when you leave for the mosque; other blocks use the offset.
            Anchor.DHUHR_IQAMA -> dhuhrIqama(date).minusMinutes(
                if (t.kind == Kind.PRAYER) settings.mosqueArriveMin.toLong() else 0L
            ).plusMinutes(offset)
            Anchor.JUMUAH -> jumuahTime(date).minusMinutes(
                if (t.kind == Kind.PRAYER) settings.jumuahArriveMin.toLong() else 0L
            ).plusMinutes(offset)
            Anchor.LUNCH -> {
                val iqama = dhuhrIqama(date)
                if (staysUntilAsr(date)) {
                    // Eat BEFORE going to the mosque, since you come home only after Asr.
                    iqama.minusMinutes(settings.mosqueArriveMin + t.durationMin.toLong())
                } else {
                    iqama.plusMinutes(PRAYER_AFTER_IQAMA_MIN)
                }.plusMinutes(offset)
            }
        }
    }

    /** Where a prayer block ends (for iqama-based blocks the duration counts from the iqama/khutba). */
    private fun prayerEnd(t: BlockTemplate, date: LocalDate, start: LocalDateTime): LocalDateTime = when {
        t.anchor == Anchor.DHUHR_IQAMA && staysUntilAsr(date) -> prayers(date).getValue(Prayer.ASR)
        t.anchor == Anchor.DHUHR_IQAMA -> dhuhrIqama(date).plusMinutes(t.durationMin.toLong())
        t.anchor == Anchor.JUMUAH -> jumuahTime(date).plusMinutes(t.durationMin.toLong())
        else -> start.plusMinutes(t.durationMin.toLong())
    }

    fun build(date: LocalDate): List<Block> {
        val list = active(date)
        val bed = bedtime(date)
        val night = night(date)
        val raw = mutableListOf<Block>()
        var cursor: LocalDateTime? = null

        list.forEachIndexed { i, t ->
            if (t.kind == Kind.TAHAJJUD && !night.tahajjud) return@forEachIndexed
            val start = if (t.anchor == Anchor.PREVIOUS) cursor ?: return@forEachIndexed
            else anchorTime(t, date)!!

            val end: LocalDateTime? = when (t.kind) {
                // Fills until the EARLIEST anchored block that comes later in the list.
                Kind.FILL -> list.drop(i + 1)
                    .filter { it.anchor != Anchor.PREVIOUS && it.kind != Kind.TAHAJJUD }
                    .mapNotNull { anchorTime(it, date) }
                    .filter { !it.isBefore(start) }
                    .minOrNull()
                Kind.FRIENDS -> minOf(start.plusMinutes(friendsCapMin(date).toLong()), bed.minusMinutes(REVIEW_MIN))
                Kind.RECOVERY_SLEEP -> {
                    val missing = settings.targetSleepMin - night.sleepMin
                    // Short naps are useless (you don't fall asleep in 2 seconds):
                    // only plan recovery sleep when 45+ min are missing, rounded to 15 min,
                    // plus the time needed to fall asleep.
                    if (missing < 45) null
                    else {
                        val rounded = ((missing + 14) / 15) * 15
                        start.plusMinutes(minOf(rounded + FALL_ASLEEP_MIN, MAX_RECOVERY_MIN))
                    }
                }
                Kind.SLEEP -> night(date.plusDays(1)).wake
                Kind.PRAYER -> prayerEnd(t, date, start)
                else -> start.plusMinutes(t.durationMin.toLong())
            }
            if (end == null || !end.isAfter(start)) return@forEachIndexed
            // A "fill" block shorter than 15 min is useless — leave it as a small buffer instead.
            if (t.kind == Kind.FILL && Duration.between(start, end).toMinutes() < 15) return@forEachIndexed
            val title = if (t.anchor == Anchor.DHUHR_IQAMA && t.kind == Kind.PRAYER && staysUntilAsr(date))
                "Dhuhr at the mosque — stay for Asr (Quran, dhikr)" else t.title
            raw += Block(t.id, title, t.category, t.kind, start, end)
            // "After previous" = after the LATEST block so far (lunch can be placed before Dhuhr).
            cursor = cursor?.let { if (end.isAfter(it)) end else it } ?: end
        }
        return withFreeTime(resolveOverlaps(raw.sortedBy { it.start }))
    }

    /** Prayers never move. Anything that collides with a prayer gets shortened. */
    private fun resolveOverlaps(sorted: List<Block>): List<Block> {
        val out = sorted.toMutableList()
        var i = 0
        while (i < out.size - 1) {
            val a = out[i]
            val b = out[i + 1]
            if (a.end.isAfter(b.start)) {
                if (b.kind == Kind.PRAYER || a.kind != Kind.PRAYER) {
                    out[i] = a.copy(end = b.start)
                } else {
                    out[i + 1] = b.copy(start = a.end)
                }
            }
            i++
        }
        return out.filter { it.end.isAfter(it.start) }
    }

    private fun withFreeTime(blocks: List<Block>): List<Block> {
        val out = mutableListOf<Block>()
        blocks.forEachIndexed { i, b ->
            out += b
            val next = blocks.getOrNull(i + 1) ?: return@forEachIndexed
            if (Duration.between(b.end, next.start).toMinutes() >= 15) {
                out += Block(
                    Block.FREE_PREFIX + b.end.toLocalTime(), "Free time / buffer",
                    Category.REST, Kind.NORMAL, b.end, next.start
                )
            }
        }
        return out
    }

    companion object {
        const val REVIEW_MIN = 30L
        const val MIN_SLEEP_FOR_TAHAJJUD = 330L // 5h30 of real sleep
        const val MAX_RECOVERY_MIN = 240L
        const val FALL_ASLEEP_MIN = 20L
        /** Dhuhr prayer done ~15 min after the iqama. */
        const val PRAYER_AFTER_IQAMA_MIN = 15L
    }
}
