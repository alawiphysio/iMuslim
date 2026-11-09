package com.alawi.mawaqit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Times {
    /** أوقات يوم معيّن (0 = اليوم، 1 = بكرة) مرتبة حسب Prayer.ordinal */
    fun forDay(ctx: Context, dayOffset: Int, now: Long = System.currentTimeMillis()): LongArray {
        val prefs = Prefs(ctx)
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_MONTH, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, 12)
        val ramadan = Hijri.isRamadan(c.timeInMillis)
        val arr = PrayerCalc.compute(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            prefs.lat, prefs.lng, ramadan
        )
        for (p in Prayer.values()) arr[p.ordinal] += prefs.adjust(p) * 60_000L
        return arr
    }

    /** الحالة الحالية: ننتظر الأذان، أو أذّن وننتظر الإقامة */
    data class State(
        val iqamaPhase: Boolean,
        val prayer: Prayer,
        val adhanT: Long,
        val iqamaT: Long,
        val target: Long,     // الوقت اللي يعدّ له العدّاد
        val progress: Float
    )

    fun state(ctx: Context, now: Long = System.currentTimeMillis()): State {
        val prefs = Prefs(ctx)
        // هل أذّن ولسا ما أقيمت الصلاة؟
        for (d in 0 downTo -1) {
            val arr = forDay(ctx, d, now)
            for (p in Prayer.FIVE) {
                val a = arr[p.ordinal]
                val iq = a + prefs.iqamaMin(p) * 60_000L
                if (prefs.iqamaMin(p) > 0 && now >= a && now < iq) {
                    return State(true, p, a, iq, iq, ((now - a).toFloat() / (iq - a).toFloat()).coerceIn(0f, 1f))
                }
            }
        }
        val (p, t) = next(ctx, now)
        return State(false, p, t, t + prefs.iqamaMin(p) * 60_000L, t, progress(ctx, now))
    }

    /** الصلاة اللي فاتت ووقتها */
    fun prevPair(ctx: Context, now: Long = System.currentTimeMillis()): Pair<Prayer, Long> {
        for (d in 0 downTo -1) {
            val arr = forDay(ctx, d, now)
            for (p in Prayer.FIVE.reversed()) if (arr[p.ordinal] <= now) return Pair(p, arr[p.ordinal])
        }
        return Pair(Prayer.ISHA, now - 6 * 3_600_000L)
    }

    /** وقت الصلاة اللي فاتت (لحساب امتلاء الحلقة) */
    fun prev(ctx: Context, now: Long = System.currentTimeMillis()): Long {
        for (d in 0 downTo -1) {
            val arr = forDay(ctx, d, now)
            for (p in Prayer.FIVE.reversed()) if (arr[p.ordinal] <= now) return arr[p.ordinal]
        }
        return now - 6 * 3_600_000L
    }

    /** نسبة الوقت المنقضي بين الصلاة السابقة والقادمة */
    fun progress(ctx: Context, now: Long = System.currentTimeMillis()): Float {
        val pv = prev(ctx, now)
        val nx = next(ctx, now).second
        if (nx <= pv) return 0f
        return ((now - pv).toFloat() / (nx - pv).toFloat()).coerceIn(0f, 1f)
    }

    /** الصلاة القادمة ووقتها */
    fun next(ctx: Context, now: Long = System.currentTimeMillis()): Pair<Prayer, Long> {
        for (d in 0..1) {
            val arr = forDay(ctx, d, now)
            for (p in Prayer.FIVE) if (arr[p.ordinal] > now) return Pair(p, arr[p.ordinal])
        }
        val arr = forDay(ctx, 1, now)
        return Pair(Prayer.FAJR, arr[Prayer.FAJR.ordinal])
    }
}

enum class EvType {
    PRE, ADHAN, IQAMA, REFRESH, ADHKAR_MORNING, ADHKAR_EVENING, AFTER_PRAYER,
    MOSQUE_START, MOSQUE_END, FRIDAY_KAHF, FRIDAY_HOUR, FAST_EVE, SUHOOR, IFTAR, QIYAM, KHATMA
}

data class Ev(val time: Long, val type: EvType, val prayer: Prayer?)

object Scheduler {
    const val EXTRA_TYPE = "ev_type"
    const val EXTRA_PRAYER = "ev_prayer"
    const val EXTRA_TIME = "ev_time"
    private const val MIN = 60_000L

    fun events(ctx: Context, now: Long): List<Ev> {
        val prefs = Prefs(ctx)
        val list = ArrayList<Ev>()
        for (d in 0..1) {
            val arr = Times.forDay(ctx, d, now)
            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, d); set(Calendar.HOUR_OF_DAY, 12) }
            val friday = day.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
            val ramadan = Hijri.isRamadan(day.timeInMillis)
            for (p in Prayer.FIVE) {
                val t = arr[p.ordinal]
                // حدث الأذان يُضاف دائماً لتحديث الويدجت، والصوت يشتغل فقط إذا كان مفعّل
                list.add(Ev(t, EvType.ADHAN, p))
                val iq = t + prefs.iqamaMin(p) * MIN
                if (prefs.adhkarRemind && p == Prayer.FAJR) list.add(Ev(iq + 20 * MIN, EvType.ADHKAR_MORNING, p))
                if (prefs.adhkarRemind && p == Prayer.ASR) list.add(Ev(iq + 20 * MIN, EvType.ADHKAR_EVENING, p))
                if (prefs.afterPrayerRemind) list.add(Ev(iq + 10 * MIN, EvType.AFTER_PRAYER, p))
                if (prefs.adhanOn(p)) {
                    if (prefs.preOn && prefs.preMin > 0) list.add(Ev(t - prefs.preMin * MIN, EvType.PRE, p))
                    if (prefs.iqamaOn && prefs.iqamaMin(p) > 0) list.add(Ev(iq, EvType.IQAMA, p))
                }
                // وضع المسجد: الجمعة يبدأ من الأذان (الخطبة) ويطول ساعة
                if (prefs.mosqueOn) {
                    if (friday && p == Prayer.DHUHR) {
                        list.add(Ev(t + MIN, EvType.MOSQUE_START, p))
                        list.add(Ev(t + 60 * MIN, EvType.MOSQUE_END, p))
                    } else {
                        list.add(Ev(iq, EvType.MOSQUE_START, p))
                        list.add(Ev(iq + prefs.mosqueMin * MIN, EvType.MOSQUE_END, p))
                    }
                }
            }
            if (prefs.fridayOn && friday) {
                list.add(Ev(arr[Prayer.SUNRISE.ordinal] + 2 * 60 * MIN, EvType.FRIDAY_KAHF, Prayer.DHUHR))
                list.add(Ev(arr[Prayer.MAGHRIB.ordinal] - 45 * MIN, EvType.FRIDAY_HOUR, Prayer.MAGHRIB))
            }
            if (prefs.fastingOn) {
                val tomorrow = day.timeInMillis + 24 * 60 * MIN
                if (Fasting.reason(tomorrow) != null) list.add(Ev(arr[Prayer.ISHA.ordinal] + 45 * MIN, EvType.FAST_EVE, Prayer.ISHA))
            }
            if (prefs.ramadanOn && ramadan) {
                list.add(Ev(arr[Prayer.FAJR.ordinal] - prefs.suhoorMin * MIN, EvType.SUHOOR, Prayer.FAJR))
                list.add(Ev(arr[Prayer.MAGHRIB.ordinal] - MIN, EvType.IFTAR, Prayer.MAGHRIB))
            }
            if (prefs.qiyamOn) list.add(Ev(Night.lastThird(ctx, d, now), EvType.QIYAM, Prayer.FAJR))
            if (Khatma.active(ctx) && !Khatma.finished(ctx))
                list.add(Ev(arr[Prayer.ISHA.ordinal] + (prefs.iqamaMin(Prayer.ISHA) + 30) * MIN, EvType.KHATMA, Prayer.ISHA))
        }
        // تحديث بعد منتصف الليل لتغيير التاريخ
        val mid = Calendar.getInstance()
        mid.timeInMillis = now
        mid.add(Calendar.DAY_OF_MONTH, 1)
        mid.set(Calendar.HOUR_OF_DAY, 0); mid.set(Calendar.MINUTE, 0)
        mid.set(Calendar.SECOND, 30); mid.set(Calendar.MILLISECOND, 0)
        list.add(Ev(mid.timeInMillis, EvType.REFRESH, null))

        return list.filter { it.time > now + 1000 }.sortedBy { it.time }
    }

    /** كل الأحداث اللي وقتها يطابق وقت التنبيه (عشان لو تزامن حدثين ما يضيع واحد) */
    fun eventsAt(ctx: Context, t: Long): List<Ev> =
        events(ctx, t - 5_000L).filter { kotlin.math.abs(it.time - t) < 2_000L }

    fun scheduleNext(ctx: Context) {
        val now = System.currentTimeMillis()
        val ev = events(ctx, now).firstOrNull() ?: return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return

        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, ev.type.name)
            putExtra(EXTRA_PRAYER, ev.prayer?.key)
            putExtra(EXTRA_TIME, ev.time)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).getLong("last_sched", 0L) != ev.time) {
            ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).edit().putLong("last_sched", ev.time).apply()
            Diag.log(ctx, "سجّلت ${ev.type}${ev.prayer?.let { " " + it.displayName(ev.time) } ?: ""} الساعة ${Fmt.time(ev.time)}${if (canExact) "" else " (غير دقيق ⚠️)"}")
        }
        try {
            if (canExact) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ev.time, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, pi)
        }
    }
}
