package com.example.barakah.model

import com.example.barakah.prayer.Prayer
import com.example.barakah.prayer.PrayerConfig
import java.time.Duration
import java.time.LocalDateTime

/** Life areas. Every block belongs to one; stats are computed per area. */
enum class Category(val label: String, val emoji: String, val color: Long) {
    DEEN("Deen", "🕌", 0xFF2E9E6B),
    BUILD("Build", "🚀", 0xFF4F7CFF),
    GROW("Marketing", "📈", 0xFF16A3A3),
    BRAND("Brand", "📣", 0xFFE9A23B),
    LEARN("Learn", "📚", 0xFF8E6CEF),
    HEALTH("Health", "💪", 0xFFE5534B),
    FAMILY("Family", "🏠", 0xFFD97BB0),
    SOCIAL("Friends", "🤝", 0xFF2BB3C0),
    REST("Rest", "🌙", 0xFF7A8699)
}

/** What a block's start time is tied to. PREVIOUS = starts when the block before it ends. */
enum class Anchor(val label: String) {
    FAJR("Fajr"), SUNRISE("Sunrise"), DHUHR("Dhuhr"), ASR("Asr"),
    MAGHRIB("Maghrib"), ISHA("Isha"), BEDTIME("Bedtime"), PREVIOUS("After previous block"),
    /** Dhuhr iqama at your mosque (see Settings → Mosque times). Prayer blocks start "arrive before" earlier. */
    DHUHR_IQAMA("Dhuhr iqama"),
    /** Start of the Jumu'ah khutba. Prayer blocks start "arrive before" earlier. */
    JUMUAH("Jumu'ah khutba"),
    /** Lunch placed automatically: before Dhuhr if you stay at the mosque until Asr, after Dhuhr otherwise. */
    LUNCH("Lunch (automatic)")
}

/** How the Dhuhr iqama time is decided. */
enum class DhuhrMode(val label: String) {
    /** e.g. Kebili: iqama 30 min before Asr; you stay in the mosque and pray Asr there. */
    BEFORE_ASR("Before Asr — stay until Asr"),
    /** Most other places: iqama at a fixed clock time (13:00). */
    FIXED("Fixed time")
}

/** How a block's length is decided. */
enum class Kind(val label: String) {
    NORMAL("Fixed length"),
    PRAYER("Prayer (never moved)"),
    FILL("Fills until the next anchored block"),
    FRIENDS("Friends (3h winter / 2h summer, never past bedtime)"),
    RECOVERY_SLEEP("Recovery sleep (only if the night was 45+ min short)"),
    TAHAJJUD("Tahajjud (only if you slept enough)"),
    SLEEP("Night sleep")
}

data class BlockTemplate(
    val id: String,
    val title: String,
    val category: Category,
    val anchor: Anchor,
    val offsetMin: Int,
    val durationMin: Int,
    val kind: Kind = Kind.NORMAL,
    /** 1 = Monday ... 7 = Sunday. Empty = every day. */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true
)

/** A concrete block on a given day, produced by the ScheduleEngine. */
data class Block(
    val templateId: String,
    val title: String,
    val category: Category,
    val kind: Kind,
    val start: LocalDateTime,
    val end: LocalDateTime
) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()
    val isFree: Boolean get() = templateId.startsWith(FREE_PREFIX)
    val checkable: Boolean get() = !isFree && kind != Kind.SLEEP

    companion object { const val FREE_PREFIX = "free-" }
}

/**
 * A city with its default minute adjustments, so times match the official Tunisian calendar.
 * Prayer times are always calculated automatically, every day, from the coordinates.
 */
data class City(
    val name: String,
    val lat: Double,
    val lng: Double,
    val zone: String,
    val adjustments: Map<Prayer, Int> = TUNISIA_DEFAULT_ADJ,
    val dhuhrMode: DhuhrMode = DhuhrMode.FIXED
)

val TUNISIA_DEFAULT_ADJ = mapOf(Prayer.FAJR to 1, Prayer.DHUHR to 1, Prayer.ASR to 1, Prayer.MAGHRIB to 5, Prayer.ISHA to 1)
val TUNIS_ADJ = mapOf(Prayer.DHUHR to 7, Prayer.ASR to 1, Prayer.MAGHRIB to 3, Prayer.ISHA to 1)

val CITIES = listOf(
    City("Kebili", 33.7044, 8.9690, "Africa/Tunis", dhuhrMode = DhuhrMode.BEFORE_ASR),
    City("Douz", 33.4572, 9.0204, "Africa/Tunis"),
    City("Tozeur", 33.9197, 8.1335, "Africa/Tunis"),
    City("Gabès", 33.8815, 10.0982, "Africa/Tunis"),
    City("Gafsa", 34.4250, 8.7842, "Africa/Tunis"),
    City("Sfax", 34.7406, 10.7603, "Africa/Tunis"),
    City("Sousse", 35.8256, 10.6411, "Africa/Tunis"),
    City("Tunis", 36.8065, 10.1815, "Africa/Tunis", TUNIS_ADJ),
    City("Paris", 48.8566, 2.3522, "Europe/Paris", emptyMap())
)

data class Settings(
    val cityName: String = "Kebili",
    val latitude: Double = 33.7044,
    val longitude: Double = 8.9690,
    val zoneId: String = "Africa/Tunis",
    val fajrAngle: Double = 18.0,
    val ishaAngle: Double = 18.0,
    /** Minutes added to the astronomical times to match the official Tunisian calendar. */
    val adjustments: Map<Prayer, Int> = TUNISIA_DEFAULT_ADJ,
    /** Earliest bedtime, minutes after midnight (22:30). Pushed later if friends time needs it. */
    val baseBedtimeMin: Int = 22 * 60 + 30,
    val winterFriendsMin: Int = 180,
    val summerFriendsMin: Int = 120,
    /** If Isha is at or after this time (minutes after midnight), it's "summer". */
    val summerIshaThresholdMin: Int = 20 * 60,
    /** Night sleep target (real sleep, not time in bed). */
    val targetSleepMin: Int = 390,
    val notificationsEnabled: Boolean = true,
    val prayerHeadsUpMin: Int = 10,
    /** Replies per day that count as a "reply streak" day. */
    val dailyReplyGoal: Int = 20,

    // ── Mosque times ──
    val dhuhrMode: DhuhrMode = DhuhrMode.BEFORE_ASR,
    /** BEFORE_ASR mode: Dhuhr iqama = Asr adhan − this. */
    val dhuhrBeforeAsrMin: Int = 30,
    /** FIXED mode: Dhuhr iqama, minutes after midnight (13:00). */
    val dhuhrFixedMin: Int = 13 * 60,
    /** Jumu'ah khutba, minutes after midnight (13:00). */
    val jumuahMin: Int = 13 * 60,
    /** You arrive at the mosque this long before the Dhuhr iqama. */
    val mosqueArriveMin: Int = 10,
    /** You arrive at the mosque this long before the Jumu'ah khutba. */
    val jumuahArriveMin: Int = 20,

    /** Add a matching ayah or hadith to every reminder. */
    val faithReminders: Boolean = true
) {
    fun prayerConfig() = PrayerConfig(
        latitude = latitude, longitude = longitude, zoneId = zoneId,
        fajrAngle = fajrAngle, ishaAngle = ishaAngle, adjustmentsMin = adjustments
    )
}

data class DailyLog(
    val date: String,
    /** The ONE thing that would make today a win. */
    val focus: String = "",
    val quranPages: Int = 0,
    val postsPublished: Int = 0,
    // ── X (Twitter) growth ──
    /** Thoughtful replies written today. */
    val replies: Int = 0,
    /** Builders you started following today (real people in your niche, not follow-for-follow). */
    val newBuilderFollows: Int = 0,
    /** Replies where the other person answered back. */
    val conversations: Int = 0,
    /**
     * True once you touched any X number for this day. Lets the app tell
     * "you logged 0" apart from "you didn't log anything" — it never invents results.
     */
    val xLogged: Boolean = false,
    val sport: Boolean = false,
    val sadaqa: Boolean = false,
    val wins: String = "",
    val notes: String = ""
)

enum class ProductStatus(val label: String, val emoji: String) {
    IDEA("Idea", "💡"), BUILDING("Building", "🔨"), LAUNCHED("Launched", "🚀"),
    PROFITABLE("Profitable", "💰"), KILLED("Killed", "🪦");

    val shipped: Boolean get() = this == LAUNCHED || this == PROFITABLE || this == KILLED
}

data class Product(
    val id: String,
    val name: String,
    val status: ProductStatus = ProductStatus.IDEA,
    val mrr: Double = 0.0,
    val users: Int = 0,
    val url: String = "",
    /** yyyy-MM-dd of the day it was first launched (set automatically). */
    val launchedOn: String = ""
)
