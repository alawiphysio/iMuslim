package com.alawi.mawaqit

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val key: String, val ar: String, val hasAdhan: Boolean) {
    FAJR("fajr", "الفجر", true),
    SUNRISE("sunrise", "الشروق", false),
    DHUHR("dhuhr", "الظهر", true),
    ASR("asr", "العصر", true),
    MAGHRIB("maghrib", "المغرب", true),
    ISHA("isha", "العشاء", true);

    /** يوم الجمعة يظهر "الجمعة" بدل "الظهر" */
    fun displayName(atMillis: Long): String {
        if (this == DHUHR) {
            val c = Calendar.getInstance()
            c.timeInMillis = atMillis
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return "الجمعة"
        }
        return ar
    }

    companion object {
        val FIVE = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
        fun byKey(k: String?): Prayer? = values().firstOrNull { it.key == k }
    }
}

/**
 * حساب أوقات الصلاة بطريقة تقويم أم القرى:
 * الفجر 18.5 درجة، العشاء بعد المغرب بـ 90 دقيقة (120 في رمضان)، العصر على المذهب الشافعي (ظل المثل).
 */
object PrayerCalc {
    private fun dtr(d: Double) = d * PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / PI
    private fun dsin(d: Double) = sin(dtr(d))
    private fun dcos(d: Double) = cos(dtr(d))
    private fun dtan(d: Double) = tan(dtr(d))
    private fun darcsin(x: Double) = rtd(asin(x))
    private fun darccos(x: Double) = rtd(acos(x))
    private fun darctan2(y: Double, x: Double) = rtd(atan2(y, x))
    private fun darccot(x: Double) = rtd(atan(1.0 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** يرجع (الميل، معادلة الزمن) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = darcsin(dsin(e) * dsin(l))
        return Pair(decl, eqt)
    }

    /**
     * يرجع مصفوفة أوقات (بالملي ثانية) مرتبة حسب Prayer.ordinal
     * month: من 1 إلى 12
     */
    fun compute(year: Int, month: Int, day: Int, lat: Double, lng: Double, ramadan: Boolean): LongArray {
        val jDate = julian(year, month, day) - lng / (15.0 * 24.0)

        fun midDay(t: Double): Double = fixHour(12.0 - sunPosition(jDate + t).second)

        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + t).first
            val noon = midDay(t)
            val v = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            val tt = darccos(v.coerceIn(-1.0, 1.0)) / 15.0
            return noon + if (ccw) -tt else tt
        }

        fun asrTime(factor: Double, t: Double): Double {
            val decl = sunPosition(jDate + t).first
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return angleTime(angle, t, false)
        }

        var times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val t = times.map { it / 24.0 }
            val fajr = angleTime(18.5, t[0], true)
            val sunrise = angleTime(0.833, t[1], true)
            val dhuhr = midDay(t[2])
            val asr = asrTime(1.0, t[3])
            val maghrib = angleTime(0.833, t[4], false)
            times = doubleArrayOf(fajr, sunrise, dhuhr, asr, maghrib, maghrib)
        }
        times[5] = times[4] + (if (ramadan) 120.0 else 90.0) / 60.0

        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(year, month - 1, day, 0, 0, 0)
        val utcMidnight = c.timeInMillis

        return LongArray(6) { i ->
            val utcHours = times[i] - lng / 15.0
            val ms = utcMidnight + (utcHours * 3_600_000.0).toLong()
            // تقريب لأقرب دقيقة
            ((ms + 30_000L) / 60_000L) * 60_000L
        }
    }
}
