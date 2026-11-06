package com.example.barakah.prayer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val label: String) {
    FAJR("Fajr"), SUNRISE("Sunrise"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha")
}

/**
 * Settings for the prayer-time calculation.
 * Default angles follow the Tunisian convention (Fajr 18°, Isha 18°, Asr with shadow factor 1).
 * Use [adjustmentsMin] to match your local mosque exactly (+/- minutes per prayer).
 */
data class PrayerConfig(
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val fajrAngle: Double = 18.0,
    val ishaAngle: Double = 18.0,
    val asrShadowFactor: Int = 1,
    val adjustmentsMin: Map<Prayer, Int> = emptyMap()
)

/**
 * Offline astronomical prayer-time calculation (same method as PrayTimes.org).
 * No internet and no external library needed.
 */
object PrayerCalculator {

    fun compute(date: LocalDate, cfg: PrayerConfig): Map<Prayer, LocalDateTime> {
        val zone = ZoneId.of(cfg.zoneId)
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - cfg.longitude / (15.0 * 24.0)

        // Initial guesses in hours, refined twice.
        var times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) { times = computeTimes(times, jDate, cfg) }

        val result = LinkedHashMap<Prayer, LocalDateTime>()
        Prayer.entries.forEachIndexed { i, prayer ->
            var hours = times[i] + tz - cfg.longitude / 15.0
            if (hours.isNaN()) hours = FALLBACK[i]
            val seconds = (hours * 3600.0).roundToLong() + (cfg.adjustmentsMin[prayer] ?: 0) * 60L
            result[prayer] = date.atStartOfDay().plusSeconds(seconds)
        }
        return result
    }

    private val FALLBACK = doubleArrayOf(5.0, 6.5, 12.5, 15.5, 18.0, 19.5)

    private fun computeTimes(t: DoubleArray, jDate: Double, cfg: PrayerConfig): DoubleArray {
        val p = DoubleArray(6) { t[it] / 24.0 }
        val lat = cfg.latitude
        return doubleArrayOf(
            sunAngleTime(cfg.fajrAngle, p[0], jDate, lat, ccw = true),
            sunAngleTime(0.833, p[1], jDate, lat, ccw = true),
            midDay(p[2], jDate),
            asrTime(cfg.asrShadowFactor.toDouble(), p[3], jDate, lat),
            sunAngleTime(0.833, p[4], jDate, lat, ccw = false),
            sunAngleTime(cfg.ishaAngle, p[5], jDate, lat, ccw = false)
        )
    }

    private fun midDay(time: Double, jDate: Double): Double =
        fixHour(12.0 - sunPosition(jDate + time).second)

    private fun sunAngleTime(angle: Double, time: Double, jDate: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + time).first
        val noon = midDay(time, jDate)
        val t = 1.0 / 15.0 * dAcos((-dSin(angle) - dSin(decl) * dSin(lat)) / (dCos(decl) * dCos(lat)))
        return noon + if (ccw) -t else t
    }

    private fun asrTime(factor: Double, time: Double, jDate: Double, lat: Double): Double {
        val decl = sunPosition(jDate + time).first
        val angle = -dAcot(factor + dTan(abs(lat - decl)))
        return sunAngleTime(angle, time, jDate, lat, ccw = false)
    }

    /** Returns (declination, equation of time). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = dAtan2(dCos(e) * dSin(l), dCos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = dAsin(dSin(e) * dSin(l))
        return decl to eqt
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun rad(d: Double) = d * Math.PI / 180.0
    private fun deg(r: Double) = r * 180.0 / Math.PI
    private fun dSin(d: Double) = sin(rad(d))
    private fun dCos(d: Double) = cos(rad(d))
    private fun dTan(d: Double) = tan(rad(d))
    private fun dAsin(x: Double) = deg(asin(x))
    private fun dAcos(x: Double) = deg(acos(x))
    private fun dAtan2(y: Double, x: Double) = deg(atan2(y, x))
    private fun dAcot(x: Double) = deg(atan(1.0 / x))
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }
}
