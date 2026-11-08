package com.alawi.mawaqit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** متابعة الصلوات: 0 = ما تأشّرت، 1 = صليت، 2 = صليت في جماعة */
object Tracker {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("tracker", Context.MODE_PRIVATE)
    private val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun dayKey(millis: Long): String = keyFmt.format(Date(millis))

    fun get(ctx: Context, day: String, p: Prayer): Int = sp(ctx).getInt("${day}_${p.key}", 0)
    fun set(ctx: Context, day: String, p: Prayer, v: Int) = sp(ctx).edit().putInt("${day}_${p.key}", v).apply()

    /** الضغط على الصلاة: ما تأشّرت ← صليت ← جماعة ← ما تأشّرت */
    fun cycle(ctx: Context, day: String, p: Prayer): Int {
        val v = (get(ctx, day, p) + 1) % 3
        set(ctx, day, p, v)
        return v
    }

    data class Week(val prayed: Int, val jamaah: Int, val total: Int)

    /** آخر ٧ أيام (بدون الصلوات اللي لسا ما جا وقتها اليوم) */
    fun week(ctx: Context, now: Long = System.currentTimeMillis()): Week {
        var prayed = 0; var jamaah = 0; var total = 0
        val today = Times.forDay(ctx, 0, now)
        for (d in 0 downTo -6) {
            val c = Calendar.getInstance(); c.timeInMillis = now; c.add(Calendar.DAY_OF_MONTH, d)
            val key = dayKey(c.timeInMillis)
            for (p in Prayer.FIVE) {
                if (d == 0 && today[p.ordinal] > now) continue
                total++
                when (get(ctx, key, p)) { 1 -> prayed++; 2 -> { prayed++; jamaah++ } }
            }
        }
        return Week(prayed, jamaah, total)
    }

    /** كم يوم متواصل صليت فيه الفجر */
    fun fajrStreak(ctx: Context, now: Long = System.currentTimeMillis()): Int {
        var streak = 0
        val c = Calendar.getInstance(); c.timeInMillis = now
        // لو فجر اليوم ما تأشّر لسا نبدأ من أمس
        if (get(ctx, dayKey(now), Prayer.FAJR) == 0) c.add(Calendar.DAY_OF_MONTH, -1)
        while (streak < 3650 && get(ctx, dayKey(c.timeInMillis), Prayer.FAJR) > 0) {
            streak++
            c.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    fun dump(ctx: Context): Map<String, *> = sp(ctx).all
    fun restore(ctx: Context, map: Map<String, Any?>) {
        val e = sp(ctx).edit()
        for ((k, v) in map) if (v is Int) e.putInt(k, v) else if (v is Number) e.putInt(k, v.toInt())
        e.apply()
    }
}

/** أزرار الإشعار: «صليت» / «صليت جماعة» */
class PrayerActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prayer.byKey(intent.getStringExtra(Scheduler.EXTRA_PRAYER)) ?: return
        val day = intent.getStringExtra("day") ?: Tracker.dayKey(System.currentTimeMillis())
        Tracker.set(ctx, day, p, intent.getIntExtra("v", 1))
        ctx.getSystemService(android.app.NotificationManager::class.java)?.cancel(intent.getIntExtra("nid", 0))
    }
}
