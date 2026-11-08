package com.alawi.mawaqit

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class PrayerWidget : AppWidgetProvider() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) {
            updateAll(ctx)
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(ctx)
        Scheduler.scheduleNext(ctx)
    }

    override fun onEnabled(ctx: Context) {
        updateAll(ctx)
    }

    override fun onDisabled(ctx: Context) {
        cancelMinuteTick(ctx)
    }

    companion object {
        const val ACTION_TICK = "com.alawi.mawaqit.WIDGET_TICK"

        private val cells = intArrayOf(R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4)
        private val names = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4)
        private val times = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (ids == null || ids.isEmpty()) {
                cancelMinuteTick(ctx)
                return
            }

            val now = System.currentTimeMillis()
            val (next, nextT) = Times.next(ctx, now)
            val today = Times.forDay(ctx, 0, now)

            val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            rv.setTextViewText(R.id.w_next_name, next.displayName(nextT))
            rv.setTextViewText(R.id.w_next_time, "الأذان ${Fmt.time(nextT)}")
            rv.setTextViewText(R.id.w_hijri, Hijri.format(now))
            // العدّاد بالدقائق داخل الحلقة الذهبية
            val mins = ((nextT - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            rv.setTextViewText(R.id.w_countdown, Fmt.num(mins))
            rv.setTextViewText(R.id.w_unit, when { mins == 2 -> "دقيقتين"; mins in 3..10 -> "دقائق"; else -> "دقيقة" })
            val density = ctx.resources.displayMetrics.density
            rv.setImageViewBitmap(R.id.w_ring, Art.widgetRing((104 * density).toInt().coerceAtMost(420), Times.progress(ctx, now)))

            // القمر
            val mp = Moon.phase(now)
            rv.setImageViewBitmap(R.id.w_moon_img, Art.widgetMoon((30 * density * 1.6f).toInt(), Moon.frac(now)))
            rv.setTextViewText(R.id.w_moon, "${mp.name} • ${Fmt.num(mp.illumination)}٪")

            Prayer.FIVE.forEachIndexed { i, p ->
                val t = today[p.ordinal]
                rv.setTextViewText(names[i], p.displayName(t))
                rv.setTextViewText(times[i], Fmt.time(t).replace(" ص", "").replace(" م", ""))
                val highlight = p == next && nextT == t
                rv.setInt(cells[i], "setBackgroundResource", if (highlight) R.drawable.cell_bg else 0)
            }

            val open = PendingIntent.getActivity(
                ctx, 10, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            mgr.updateAppWidget(ids, rv)
            scheduleMinuteTick(ctx, now)
        }

        private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 300, Intent(ctx, PrayerWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * تحديث الويدجت على رأس كل دقيقة.
         * نوع RTC بدون إيقاظ: لما الشاشة مطفية ما يشتغل (ما يستهلك بطارية)،
         * وأول ما تفتح الشاشة يتحدث على طول.
         */
        private fun scheduleMinuteTick(ctx: Context, now: Long) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = tickIntent(ctx)
            val nextMinute = (now / 60_000L + 1) * 60_000L + 500L
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExact(AlarmManager.RTC, nextMinute, pi)
                } else {
                    am.set(AlarmManager.RTC, nextMinute, pi)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC, nextMinute, pi)
            }
        }

        private fun cancelMinuteTick(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx))
        }
    }
}
