package com.example.barakah.schedule

import com.example.barakah.model.Anchor
import com.example.barakah.model.BlockTemplate
import com.example.barakah.model.Category
import com.example.barakah.model.Kind

/**
 * The default week of a full-time indie hacker. ORDER MATTERS: a block with
 * Anchor.PREVIOUS starts when the block listed before it (on that day) ends.
 * Days: 1 = Monday ... 7 = Sunday. Empty set = every day.
 *
 * Sat–Thu = work days. Friday = Jumu'ah, family, rest, weekly review.
 */
object DefaultTemplate {
    private val WORK = setOf(6, 7, 1, 2, 3, 4)
    private val NOT_FRIDAY = setOf(1, 2, 3, 4, 6, 7)
    private val FRIDAY = setOf(5)

    val blocks: List<BlockTemplate> = listOf(
        // ── Night & Fajr ──
        BlockTemplate("tahajjud", "Tahajjud & du'a for your work", Category.DEEN, Anchor.FAJR, -25, 15, Kind.TAHAJJUD),
        BlockTemplate("fajr", "Fajr prayer", Category.DEEN, Anchor.FAJR, 0, 15, Kind.PRAYER),
        BlockTemplate("adhkar_am", "Morning adhkar & Quran", Category.DEEN, Anchor.PREVIOUS, 0, 30, days = NOT_FRIDAY),
        BlockTemplate("adhkar_fri", "Morning adhkar & Surat al-Kahf", Category.DEEN, Anchor.PREVIOUS, 0, 40, days = FRIDAY),
        BlockTemplate("recovery", "Recovery sleep", Category.REST, Anchor.PREVIOUS, 0, 0, Kind.RECOVERY_SLEEP),

        // ── Morning: the best hours go to the product ──
        BlockTemplate("deep1", "Deep work #1 — your ONE thing (phone on DND)", Category.BUILD, Anchor.PREVIOUS, 0, 150, days = WORK),
        BlockTemplate("learn_fri", "Learn — a course, a book, or a teardown of a product that works", Category.LEARN, Anchor.PREVIOUS, 0, 120, days = FRIDAY),
        BlockTemplate("breakfast", "Breakfast & walk / sport", Category.HEALTH, Anchor.PREVIOUS, 0, 60),
        BlockTemplate("deep2", "Deep work #2 — build & ship", Category.BUILD, Anchor.PREVIOUS, 0, 0, Kind.FILL, WORK),
        BlockTemplate("family_fri_am", "Family & errands", Category.FAMILY, Anchor.PREVIOUS, 0, 0, Kind.FILL, FRIDAY),

        // ── Lunch & Dhuhr (iqama-based, see Settings → Mosque times) ──
        // Kebili: lunch first, then Dhuhr at the mosque and stay for Asr.
        // Elsewhere: Dhuhr at 13:00, lunch after.
        BlockTemplate("lunch", "Lunch & rest (no screens)", Category.HEALTH, Anchor.LUNCH, 0, 60, days = NOT_FRIDAY),
        BlockTemplate("dhuhr", "Dhuhr at the mosque", Category.DEEN, Anchor.DHUHR_IQAMA, 0, 15, Kind.PRAYER, NOT_FRIDAY),

        // ── Friday: khutba 13:00, arrive 20 min before ──
        BlockTemplate("ghusl", "Ghusl, best clothes, perfume", Category.DEEN, Anchor.JUMUAH, -60, 30, days = FRIDAY),
        BlockTemplate("jumuah", "Jumu'ah — khutba & prayer", Category.DEEN, Anchor.JUMUAH, 0, 50, Kind.PRAYER, FRIDAY),
        BlockTemplate("lunch_fri", "Friday lunch with family", Category.FAMILY, Anchor.PREVIOUS, 0, 60, days = FRIDAY),

        // ── Until Asr (empty in Kebili, since you're at the mosque) ──
        BlockTemplate("deep3", "Deep work #3 — bugs, user replies, small tasks", Category.BUILD, Anchor.PREVIOUS, 0, 0, Kind.FILL, WORK),
        BlockTemplate("rest_fri", "Free time — rest & recharge", Category.REST, Anchor.PREVIOUS, 0, 0, Kind.FILL, FRIDAY),

        // ── Asr → Maghrib ──
        BlockTemplate("asr", "Asr prayer", Category.DEEN, Anchor.ASR, 0, 20, Kind.PRAYER),
        BlockTemplate("learn", "Learn 30 min — one skill for your business", Category.LEARN, Anchor.PREVIOUS, 0, 30, days = WORK),
        BlockTemplate("weekly_review", "Weekly review — revenue, users, Quran, next week's goal", Category.BUILD, Anchor.PREVIOUS, 0, 60, days = FRIDAY),
        BlockTemplate("grow", "Marketing — talk to users, outreach, SEO, launch posts", Category.GROW, Anchor.PREVIOUS, 0, 0, Kind.FILL, WORK),
        BlockTemplate("read_fri", "Read / plan the week", Category.LEARN, Anchor.PREVIOUS, 0, 0, Kind.FILL, FRIDAY),
        BlockTemplate("brand", "Build in public — post your progress on X, reply to 10 people", Category.BRAND, Anchor.MAGHRIB, -45, 40),

        // ── Evening ──
        BlockTemplate("maghrib", "Maghrib & evening adhkar", Category.DEEN, Anchor.MAGHRIB, 0, 20, Kind.PRAYER),
        BlockTemplate("family", "Family & dinner", Category.FAMILY, Anchor.PREVIOUS, 0, 0, Kind.FILL),
        BlockTemplate("isha", "Isha prayer", Category.DEEN, Anchor.ISHA, 0, 15, Kind.PRAYER),
        BlockTemplate("friends", "Friends time", Category.SOCIAL, Anchor.PREVIOUS, 0, 0, Kind.FRIENDS),
        BlockTemplate("review", "Daily review, set tomorrow's ONE thing, sleep adhkar", Category.DEEN, Anchor.BEDTIME, -30, 30),
        BlockTemplate("sleep", "Sleep — lights off, phone away", Category.REST, Anchor.BEDTIME, 0, 0, Kind.SLEEP)
    )
}
