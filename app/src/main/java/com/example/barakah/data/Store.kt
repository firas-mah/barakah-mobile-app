package com.example.barakah.data

import android.content.Context
import com.example.barakah.model.Anchor
import com.example.barakah.model.BlockTemplate
import com.example.barakah.model.Category
import com.example.barakah.model.DailyLog
import com.example.barakah.model.DhuhrMode
import com.example.barakah.model.Kind
import com.example.barakah.model.Product
import com.example.barakah.model.ProductStatus
import com.example.barakah.model.Settings
import com.example.barakah.prayer.Prayer
import com.example.barakah.schedule.DefaultTemplate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage on the phone (SharedPreferences + JSON). No extra library needed.
 * Everything stays offline, on your device.
 */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("barakah", Context.MODE_PRIVATE)

    // ───────── Settings ─────────
    fun loadSettings(): Settings {
        val raw = prefs.getString(K_SETTINGS, null) ?: return Settings()
        return runCatching {
            val o = JSONObject(raw)
            val d = Settings()
            val adj = o.optJSONObject("adjustments")
            Settings(
                cityName = o.optString("cityName", d.cityName),
                latitude = o.optDouble("latitude", d.latitude),
                longitude = o.optDouble("longitude", d.longitude),
                zoneId = o.optString("zoneId", d.zoneId),
                fajrAngle = o.optDouble("fajrAngle", d.fajrAngle),
                ishaAngle = o.optDouble("ishaAngle", d.ishaAngle),
                adjustments = if (adj == null) d.adjustments
                else Prayer.entries.associateWith { adj.optInt(it.name, 0) },
                baseBedtimeMin = o.optInt("baseBedtimeMin", d.baseBedtimeMin),
                winterFriendsMin = o.optInt("winterFriendsMin", d.winterFriendsMin),
                summerFriendsMin = o.optInt("summerFriendsMin", d.summerFriendsMin),
                summerIshaThresholdMin = o.optInt("summerIshaThresholdMin", d.summerIshaThresholdMin),
                targetSleepMin = o.optInt("targetSleepMin", d.targetSleepMin),
                notificationsEnabled = o.optBoolean("notificationsEnabled", d.notificationsEnabled),
                prayerHeadsUpMin = o.optInt("prayerHeadsUpMin", d.prayerHeadsUpMin),
                dailyReplyGoal = o.optInt("dailyReplyGoal", d.dailyReplyGoal),
                // Not saved yet (older version) → Kebili stays until Asr, other cities use 13:00.
                dhuhrMode = runCatching { DhuhrMode.valueOf(o.getString("dhuhrMode")) }.getOrElse {
                    if (o.optString("cityName", d.cityName) == "Kebili") DhuhrMode.BEFORE_ASR else DhuhrMode.FIXED
                },
                dhuhrBeforeAsrMin = o.optInt("dhuhrBeforeAsrMin", d.dhuhrBeforeAsrMin),
                dhuhrFixedMin = o.optInt("dhuhrFixedMin", d.dhuhrFixedMin),
                jumuahMin = o.optInt("jumuahMin", d.jumuahMin),
                mosqueArriveMin = o.optInt("mosqueArriveMin", d.mosqueArriveMin),
                jumuahArriveMin = o.optInt("jumuahArriveMin", d.jumuahArriveMin),
                faithReminders = o.optBoolean("faithReminders", d.faithReminders)
            )
        }.getOrDefault(Settings())
    }

    fun saveSettings(s: Settings) {
        val adj = JSONObject()
        s.adjustments.forEach { (p, m) -> adj.put(p.name, m) }
        val o = JSONObject()
            .put("cityName", s.cityName).put("latitude", s.latitude).put("longitude", s.longitude)
            .put("zoneId", s.zoneId).put("fajrAngle", s.fajrAngle).put("ishaAngle", s.ishaAngle)
            .put("adjustments", adj).put("baseBedtimeMin", s.baseBedtimeMin)
            .put("winterFriendsMin", s.winterFriendsMin).put("summerFriendsMin", s.summerFriendsMin)
            .put("summerIshaThresholdMin", s.summerIshaThresholdMin)
            .put("targetSleepMin", s.targetSleepMin)
            .put("notificationsEnabled", s.notificationsEnabled)
            .put("prayerHeadsUpMin", s.prayerHeadsUpMin)
            .put("dailyReplyGoal", s.dailyReplyGoal)
            .put("dhuhrMode", s.dhuhrMode.name)
            .put("dhuhrBeforeAsrMin", s.dhuhrBeforeAsrMin)
            .put("dhuhrFixedMin", s.dhuhrFixedMin)
            .put("jumuahMin", s.jumuahMin)
            .put("mosqueArriveMin", s.mosqueArriveMin)
            .put("jumuahArriveMin", s.jumuahArriveMin)
            .put("faithReminders", s.faithReminders)
        prefs.edit().putString(K_SETTINGS, o.toString()).apply()
    }

    // ───────── Week template ─────────
    fun loadTemplates(): List<BlockTemplate> {
        val raw = prefs.getString(K_TEMPLATES, null) ?: return DefaultTemplate.blocks
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val days = o.getJSONArray("days")
                BlockTemplate(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    category = Category.valueOf(o.getString("category")),
                    anchor = Anchor.valueOf(o.getString("anchor")),
                    offsetMin = o.getInt("offsetMin"),
                    durationMin = o.getInt("durationMin"),
                    kind = Kind.valueOf(o.getString("kind")),
                    days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(DefaultTemplate.blocks)
    }

    fun saveTemplates(list: List<BlockTemplate>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().put("id", t.id).put("title", t.title).put("category", t.category.name)
                    .put("anchor", t.anchor.name).put("offsetMin", t.offsetMin)
                    .put("durationMin", t.durationMin).put("kind", t.kind.name)
                    .put("days", JSONArray(t.days.sorted())).put("enabled", t.enabled)
            )
        }
        prefs.edit().putString(K_TEMPLATES, arr.toString()).apply()
    }

    fun resetTemplates() = prefs.edit().remove(K_TEMPLATES).apply()

    // ───────── Done blocks: date -> set of template ids ─────────
    fun loadCompletions(): Map<String, Set<String>> {
        val o = JSONObject(prefs.getString(K_DONE, "{}") ?: "{}")
        return o.keys().asSequence().associateWith { date ->
            val a = o.getJSONArray(date)
            (0 until a.length()).map { a.getString(it) }.toSet()
        }
    }

    fun saveCompletions(map: Map<String, Set<String>>) {
        val o = JSONObject()
        map.forEach { (date, ids) -> if (ids.isNotEmpty()) o.put(date, JSONArray(ids.toList())) }
        prefs.edit().putString(K_DONE, o.toString()).apply()
    }

    // ───────── Daily logs ─────────
    fun loadLogs(): Map<String, DailyLog> {
        val o = JSONObject(prefs.getString(K_LOGS, "{}") ?: "{}")
        return o.keys().asSequence().associateWith { date ->
            val l = o.getJSONObject(date)
            DailyLog(
                date = date,
                focus = l.optString("focus"),
                quranPages = l.optInt("quranPages"),
                postsPublished = l.optInt("postsPublished"),
                replies = l.optInt("replies"),
                newBuilderFollows = l.optInt("newBuilderFollows"),
                conversations = l.optInt("conversations"),
                // Old logs (before this version) with posts count as "logged".
                xLogged = l.optBoolean("xLogged", l.optInt("postsPublished") > 0),
                sport = l.optBoolean("sport"),
                sadaqa = l.optBoolean("sadaqa"),
                wins = l.optString("wins"),
                notes = l.optString("notes")
            )
        }
    }

    fun saveLogs(map: Map<String, DailyLog>) {
        val o = JSONObject()
        map.forEach { (date, l) ->
            o.put(
                date, JSONObject().put("focus", l.focus).put("quranPages", l.quranPages).put("postsPublished", l.postsPublished)
                    .put("replies", l.replies).put("newBuilderFollows", l.newBuilderFollows)
                    .put("conversations", l.conversations).put("xLogged", l.xLogged)
                    .put("sport", l.sport).put("sadaqa", l.sadaqa).put("wins", l.wins).put("notes", l.notes)
            )
        }
        prefs.edit().putString(K_LOGS, o.toString()).apply()
    }

    // ───────── Products ─────────
    fun loadProducts(): List<Product> {
        val arr = JSONArray(prefs.getString(K_PRODUCTS, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Product(
                id = o.getString("id"),
                name = o.getString("name"),
                status = runCatching { ProductStatus.valueOf(o.getString("status")) }.getOrDefault(ProductStatus.IDEA),
                mrr = o.optDouble("mrr", 0.0),
                users = o.optInt("users"),
                url = o.optString("url"),
                launchedOn = o.optString("launchedOn")
            )
        }
    }

    fun saveProducts(list: List<Product>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().put("id", p.id).put("name", p.name).put("status", p.status.name)
                    .put("mrr", p.mrr).put("users", p.users).put("url", p.url)
                    .put("launchedOn", p.launchedOn)
            )
        }
        prefs.edit().putString(K_PRODUCTS, arr.toString()).apply()
    }

    // ───────── Metric history (e.g. "mrr", "followers"): date -> value ─────────
    fun loadMetric(name: String): Map<String, Double> {
        val o = JSONObject(prefs.getString(K_METRIC + name, "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getDouble(it) }
    }

    fun saveMetric(name: String, map: Map<String, Double>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(K_METRIC + name, o.toString()).apply()
    }

    // ───────── Alarm bookkeeping ─────────
    var scheduledAlarmCount: Int
        get() = prefs.getInt(K_ALARMS, 0)
        set(v) = prefs.edit().putInt(K_ALARMS, v).apply()

    companion object {
        private const val K_SETTINGS = "settings"
        private const val K_TEMPLATES = "templates"
        private const val K_DONE = "done"
        private const val K_LOGS = "logs"
        private const val K_PRODUCTS = "products"
        private const val K_METRIC = "metric_"
        private const val K_ALARMS = "alarm_count"
    }
}
