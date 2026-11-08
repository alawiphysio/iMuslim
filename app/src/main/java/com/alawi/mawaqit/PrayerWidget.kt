package com.alawi.mawaqit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews

class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(ctx)
        Scheduler.scheduleNext(ctx)
    }

    override fun onEnabled(ctx: Context) {
        updateAll(ctx)
    }

    companion object {
        private val cells = intArrayOf(R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4)
        private val names = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4)
        private val times = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (ids == null || ids.isEmpty()) return

            val now = System.currentTimeMillis()
            val (next, nextT) = Times.next(ctx, now)
            val today = Times.forDay(ctx, 0, now)

            val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            rv.setTextViewText(R.id.w_next_name, next.displayName(nextT))
            rv.setTextViewText(R.id.w_next_time, "الأذان ${Fmt.time(nextT)}")
            rv.setTextViewText(R.id.w_hijri, Hijri.format(now))

            val base = SystemClock.elapsedRealtime() + (nextT - now)
            rv.setChronometer(R.id.w_chrono, base, null, true)
            rv.setChronometerCountDown(R.id.w_chrono, true)

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
        }
    }
}
