package com.example.barakah.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.barakah.MainActivity
import com.example.barakah.R
import com.example.barakah.coach.Act
import com.example.barakah.coach.Coach
import com.example.barakah.coach.Reminders
import com.example.barakah.data.Store
import com.example.barakah.model.Block
import com.example.barakah.model.Category
import com.example.barakah.model.Kind
import com.example.barakah.schedule.ScheduleEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Schedules a notification at the start of every block, plus a heads-up before each prayer. */
object AlarmScheduler {
    private const val ACTION_NOTIFY = "com.example.barakah.NOTIFY"
    private const val ACTION_REFRESH = "com.example.barakah.REFRESH"
    private const val REFRESH_CODE = 999
    private const val BASE_CODE = 1000
    private const val MAX_ALARMS = 120
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun reschedule(context: Context) {
        val ctx = context.applicationContext
        val store = Store(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return

        // Cancel what was scheduled before.
        repeat(store.scheduledAlarmCount) { i ->
            am.cancel(notifyIntent(ctx, BASE_CODE + i, Event(LocalDateTime.MIN, "", "")))
        }
        store.scheduledAlarmCount = 0

        val settings = store.loadSettings()
        val zone = ZoneId.of(settings.zoneId)
        val now = LocalDateTime.now(zone)

        // Re-plan every night just after midnight (prayer times change daily).
        val refreshAt = now.toLocalDate().plusDays(1).atTime(0, 5)
        setAlarm(ctx, am, refreshAt.atZone(zone).toInstant().toEpochMilli(), refreshIntent(ctx))

        if (!settings.notificationsEnabled) return

        val engine = ScheduleEngine(settings, store.loadTemplates())
        val events = mutableListOf<Event>()
        for (date in listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))) {
            val blocks = engine.build(date)
            val stays = engine.staysUntilAsr(date)
            // Adds the matching ayah / hadith under the text (if enabled in Settings).
            fun faith(text: String, b: Block, act: Act? = null): String {
                if (!settings.faithReminders) return text
                val r = if (act != null) Reminders.pick(act, date, b.start.hour) else Reminders.forBlock(b, date, stays)
                return if (text.isBlank()) r.format() else "$text\n\n${r.format()}"
            }
            blocks.forEach { b ->
                if (b.isFree) return@forEach
                val range = "${b.start.format(HM)}–${b.end.format(HM)}"
                when {
                    b.kind == Kind.PRAYER -> {
                        val mosque = when (b.templateId) {
                            "dhuhr" -> "iqama at ${engine.dhuhrIqama(date).format(HM)}"
                            "jumuah" -> "khutba at ${engine.jumuahTime(date).format(HM)}"
                            else -> null
                        }
                        events += Event(
                            b.start.minusMinutes(settings.prayerHeadsUpMin.toLong()),
                            if (mosque != null) "🕌 Leave for the mosque in ${settings.prayerHeadsUpMin} min ($mosque)"
                            else "🕌 ${b.title} in ${settings.prayerHeadsUpMin} min",
                            faith("Save your work, close the laptop, make wudu.", b, Act.PRAYER_SOON)
                        )
                        events += Event(
                            b.start,
                            if (mosque != null) "🕌 Go to the mosque — $mosque" else "🕌 ${b.title} — now",
                            faith(if (b.templateId == "dhuhr" && stays) "$range · you pray Asr there too" else range, b)
                        )
                    }
                    b.kind == Kind.SLEEP -> events += Event(
                        b.start, "🌙 Lights off",
                        faith("Phone away. Wake-up at ${b.end.format(HM)} — your morning hours are the most productive.", b)
                    )
                    b.kind == Kind.RECOVERY_SLEEP -> events += Event(
                        b.start, "😴 Recovery sleep until ${b.end.format(HM)}",
                        faith("Short night — sleep now so your deep work is sharp.", b)
                    )
                    // Texts that depend on what you write in the app are built when the alarm fires.
                    b.templateId == "deep1" -> events += Event(b.start, "🚀 ${b.title}", range, check = CHECK_FOCUS, date = date)
                    b.templateId == "review" -> events += Event(b.start, "📝 Daily review", range, check = CHECK_DAY, date = date)
                    else -> {
                        val msg = Coach.blockMessage(b, "", date)
                        events += Event(
                            b.start, "${b.category.emoji} ${b.title}",
                            faith(if (msg.isBlank()) range else "$range · $msg", b)
                        )
                    }
                }
            }

            // ── Coaching & accountability ──
            blocks.firstOrNull { it.templateId == "breakfast" }?.let { b ->
                val tip = Coach.tipFor(date)
                val note = Coach.dayNote(date)
                events += Event(b.start.plusMinutes(5), "💡 ${tip.title}", tip.body + (note?.let { "\n\n$it" } ?: ""))
            }
            blocks.firstOrNull { it.templateId == "asr" }?.let { b ->
                events += Event(b.end.plusMinutes(5), "", "", check = CHECK_BUILD, date = date)
            }
            blocks.firstOrNull { it.templateId == "isha" }?.let { b ->
                events += Event(b.end, "", "", check = CHECK_BRAND, date = date)
            }
        }

        val upcoming = events.filter { it.time.isAfter(now) }.sortedBy { it.time }.take(MAX_ALARMS)
        upcoming.forEachIndexed { i, e ->
            setAlarm(ctx, am, e.time.atZone(zone).toInstant().toEpochMilli(), notifyIntent(ctx, BASE_CODE + i, e))
        }
        store.scheduledAlarmCount = upcoming.size
    }

    data class Event(
        val time: LocalDateTime,
        val title: String,
        val text: String,
        val check: String? = null,
        val date: LocalDate? = null
    )

    const val CHECK_FOCUS = "focus"
    const val CHECK_DAY = "day"
    const val CHECK_BUILD = "build"
    const val CHECK_BRAND = "brand"

    /** Builds the notification for a "smart" alarm, using what you logged today. Null = nothing to say. */
    fun evaluateCheck(ctx: Context, check: String, dateText: String): Pair<String, String>? {
        val store = Store(ctx)
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return null
        val settings = store.loadSettings()
        val engine = ScheduleEngine(settings, store.loadTemplates())
        val blocks = engine.build(date)
        fun faith(text: String, act: Act): String =
            if (settings.faithReminders) "$text\n\n${Reminders.pick(act, date).format()}" else text
        val done = store.loadCompletions()[date.toString()].orEmpty()
        val log = store.loadLogs()[date.toString()]
        val focus = log?.focus.orEmpty()
        return when (check) {
            CHECK_FOCUS -> {
                val b = blocks.firstOrNull { it.templateId == "deep1" } ?: return null
                "🚀 ${b.title}" to faith(Coach.blockMessage(b, focus, date), Act.WORK)
            }
            CHECK_BUILD -> {
                val built = blocks.any { it.category == Category.BUILD && it.templateId in done }
                if (built) null
                else "⚠️ No deep work checked today" to
                    "Even 25 focused minutes count. Pick one small task, finish it, tick it in the app."
            }
            CHECK_BRAND -> {
                val posted = (log?.postsPublished ?: 0) > 0 || blocks.any { it.category == Category.BRAND && it.templateId in done }
                if (posted) null
                else "📣 You haven't posted today" to
                    "One tweet about what you did today: a screenshot, a number or a lesson. 5 minutes."
            }
            CHECK_DAY -> {
                val checkable = blocks.filter { it.checkable }
                val prayers = blocks.filter { it.kind == Kind.PRAYER }
                val doneCount = checkable.count { it.templateId in done }
                val prayersDone = prayers.count { it.templateId in done }
                val focusLine = if (focus.isBlank()) "" else "\nONE thing: $focus"
                "📝 Daily review — $doneCount/${checkable.size} blocks, $prayersDone/${prayers.size} prayers" to
                    faith("Write 3 wins and tomorrow's ONE thing.$focusLine", Act.REVIEW)
            }
            else -> null
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun setAlarm(ctx: Context, am: AlarmManager, atMillis: Long, pi: PendingIntent) {
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            // Without the exact-alarm permission Android may deliver a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun notifyIntent(ctx: Context, code: Int, e: Event): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra("title", e.title)
            putExtra("text", e.text)
            putExtra("id", code)
            putExtra("check", e.check)
            putExtra("date", e.date?.toString())
        }
        return PendingIntent.getBroadcast(
            ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply { action = ACTION_REFRESH }
        return PendingIntent.getBroadcast(
            ctx, REFRESH_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun isRefresh(intent: Intent) = intent.action == ACTION_REFRESH
}

object Notifier {
    const val CHANNEL_ID = "day_blocks"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Day plan & prayers", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "A reminder when each block of your day starts"
            nm.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_barakah)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) { }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AlarmScheduler.isRefresh(intent)) {
            AlarmScheduler.reschedule(context)
            return
        }
        val id = intent.getIntExtra("id", 0)
        val check = intent.getStringExtra("check")
        if (check != null) {
            val (title, text) = AlarmScheduler.evaluateCheck(context, check, intent.getStringExtra("date") ?: "") ?: return
            Notifier.show(context, id, title, text)
        } else {
            Notifier.show(context, id, intent.getStringExtra("title") ?: "Barakah", intent.getStringExtra("text") ?: "")
        }
    }
}

/** Re-plans after reboot, time change, timezone change or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.reschedule(context)
    }
}
