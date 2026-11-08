package com.alawi.mawaqit

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

    /** لما تفتح أو تقفل الـ Fold أو تغيّر حجم الويدجت نعيد رسم الخلفية بالمقاس الجديد */
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        updateAll(ctx)
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

        private fun minutesWord(m: Int) = when {
            m == 2 -> "دقيقتين"
            m in 3..10 -> "دقائق"
            else -> "دقيقة"
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java)) ?: IntArray(0)
            val small = mgr.getAppWidgetIds(ComponentName(ctx, CompactWidget::class.java)) ?: IntArray(0)
            if (ids.isEmpty() && small.isEmpty()) {
                cancelMinuteTick(ctx)
                return
            }
            val now = System.currentTimeMillis()
            for (id in ids) {
                try { mgr.updateAppWidget(id, build(ctx, mgr, id, now)) } catch (e: Exception) { }
            }
            if (small.isNotEmpty()) {
                try { mgr.updateAppWidget(small, buildSmall(ctx, now)) } catch (e: Exception) { }
            }
            scheduleMinuteTick(ctx, now)
        }

        /** الويدجت الصغيرة (مناسبة للشاشة الخارجية للـ Fold) */
        private fun buildSmall(ctx: Context, now: Long): RemoteViews {
            val st = Times.state(ctx, now)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_small)
            val name = st.prayer.displayName(st.adhanT)
            val mins = ((st.target - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            rv.setTextViewText(R.id.s_name, if (st.iqamaPhase) "إقامة $name" else name)
            rv.setTextViewText(R.id.s_time, if (st.iqamaPhase) "الإقامة ${Fmt.time(st.iqamaT)}" else "الأذان ${Fmt.time(st.adhanT)} • الإقامة ${Fmt.time(st.iqamaT)}")
            rv.setTextViewText(R.id.s_min, Fmt.num(mins))
            val scale = ctx.resources.displayMetrics.density.coerceAtMost(2.5f)
            rv.setImageViewBitmap(R.id.s_ring, Art.widgetRing((62 * scale).toInt(), st.progress))
            rv.setImageViewResource(R.id.s_figure, Pictures.figure(ctx, st.iqamaPhase))
            rv.setOnClickPendingIntent(R.id.s_root, PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return rv
        }

        private fun build(ctx: Context, mgr: AppWidgetManager, id: Int, now: Long): RemoteViews {
            val prefs = Prefs(ctx)
            val st = Times.state(ctx, now)
            val today = Times.forDay(ctx, 0, now)
            val density = ctx.resources.displayMetrics.density

            val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)

            // الخلفية بمقاس الويدجت الفعلي
            val opts = mgr.getAppWidgetOptions(id)
            val wDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0).takeIf { it > 0 } ?: 320
            val hDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0).takeIf { it > 0 } ?: 170
            val scale = density.coerceAtMost(2.5f)
            rv.setImageViewBitmap(R.id.w_bg, Art.widgetBackground(
                (wDp * scale).toInt().coerceIn(200, 1400), (hDp * scale).toInt().coerceIn(100, 900),
                Landmarks.detect(prefs.lat, prefs.lng), 24f * scale))

            // الشخصية
            rv.setImageViewResource(R.id.w_figure, Pictures.figure(ctx, st.iqamaPhase))

            val name = st.prayer.displayName(st.adhanT)
            val mins = ((st.target - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            if (st.iqamaPhase) {
                rv.setTextViewText(R.id.w_label, "حان وقت الصلاة • استعد للإقامة")
                rv.setTextViewText(R.id.w_next_name, "صلاة $name")
                rv.setTextViewText(R.id.w_next_time, "أذّن ${Fmt.time(st.adhanT)}")
                rv.setTextViewText(R.id.w_iqama, "الإقامة ${Fmt.time(st.iqamaT)} • باقي ${Fmt.minutesLeft(st.iqamaT - now)}")
                rv.setTextViewText(R.id.w_ring_caption, "للإقامة")
            } else {
                rv.setTextViewText(R.id.w_label, "الصلاة القادمة")
                rv.setTextViewText(R.id.w_next_name, name)
                rv.setTextViewText(R.id.w_next_time, "الأذان ${Fmt.time(st.adhanT)}")
                rv.setTextViewText(R.id.w_iqama, "الإقامة ${Fmt.time(st.iqamaT)}")
                rv.setTextViewText(R.id.w_ring_caption, "للأذان")
            }

            // العدّاد بالدقائق داخل الحلقة
            rv.setTextViewText(R.id.w_countdown, Fmt.num(mins))
            rv.setTextViewText(R.id.w_unit, minutesWord(mins))
            rv.setImageViewBitmap(R.id.w_ring, Art.widgetRing((100 * scale).toInt(), st.progress))

            // القمر
            val mp = Moon.phase(now)
            rv.setImageViewBitmap(R.id.w_moon_img, Art.widgetMoon((22 * scale * 1.7f).toInt(), Moon.frac(now)))
            rv.setTextViewText(R.id.w_moon, "${mp.name} • ${Hijri.format(now)}")

            // أوقات اليوم
            Prayer.FIVE.forEachIndexed { i, p ->
                val t = today[p.ordinal]
                rv.setTextViewText(names[i], p.displayName(t))
                rv.setTextViewText(times[i], Fmt.time(t).replace(" ص", "").replace(" م", ""))
                val highlight = p == st.prayer && t == st.adhanT
                rv.setInt(cells[i], "setBackgroundResource", if (highlight) R.drawable.cell_bg else 0)
                rv.setTextColor(names[i], if (highlight) C_GOLD else 0xFFA9BDB4.toInt())
            }

            val open = PendingIntent.getActivity(
                ctx, 10, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            return rv
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

/** ويدجت صغيرة: الصلاة القادمة + العدّاد (مناسبة للشاشة الخارجية) */
class CompactWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        PrayerWidget.updateAll(ctx)
        Scheduler.scheduleNext(ctx)
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        PrayerWidget.updateAll(ctx)
    }
}
