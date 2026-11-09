package com.alawi.mawaqit

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** فحص الأذان: سجل لما يصير + فحص الأذونات اللي ممكن توقف الأذان + تجربة حقيقية */
object Diag {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("diag", Context.MODE_PRIVATE)

    fun log(ctx: Context, msg: String) {
        try {
            val s = sp(ctx)
            val line = "${Fmt.time(System.currentTimeMillis())} ${android.text.format.DateFormat.format("dd/MM", System.currentTimeMillis())} — $msg"
            val old = s.getString("log", "")!!.split("\n").filter { it.isNotBlank() }.take(29)
            s.edit().putString("log", (listOf(line) + old).joinToString("\n")).apply()
        } catch (e: Exception) { }
    }

    fun recent(ctx: Context, n: Int = 8): List<String> =
        sp(ctx).getString("log", "")!!.split("\n").filter { it.isNotBlank() }.take(n)

    class Issue(val text: String, val fix: (Activity) -> Unit)

    /** المشاكل اللي تمنع الأذان */
    fun issues(ctx: Context): List<Issue> {
        val out = ArrayList<Issue>()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pkg = ctx.packageName
        fun appSettings(a: Activity) = a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        if (nm != null && !nm.areNotificationsEnabled())
            out.add(Issue("الإشعارات مقفلة للتطبيق") { a -> try {
                a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)) } catch (e: Exception) { appSettings(a) } })
        if (nm != null) {
            val ch = nm.getNotificationChannel(Notifier.CH_ADHAN)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE)
                out.add(Issue("قناة «الأذان» مقفلة في الإشعارات") { a -> try {
                    a.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_ADHAN)) } catch (e: Exception) { appSettings(a) } })
        }
        if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent())
            out.add(Issue("شاشة الأذان الكاملة غير مسموحة") { a -> try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))) } catch (e: Exception) { appSettings(a) } })
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms())
            out.add(Issue("المنبهات الدقيقة غير مسموحة") { a -> try {
                a.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg"))) } catch (e: Exception) { appSettings(a) } })
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg))
            out.add(Issue("توفير البطارية ممكن يوقف الأذان") { a -> try {
                @android.annotation.SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")); a.startActivity(i)
            } catch (e: Exception) { appSettings(a) } })
        return out
    }

    /** المنبه الجاي المسجّل فعلاً عند النظام */
    fun nextAlarmText(ctx: Context): String {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return "—"
        val n = am.nextAlarmClock ?: return "ما فيه منبه مسجّل ⚠️"
        val (np, nt) = Times.next(ctx)
        return "المنبه الجاي: ${Fmt.time(n.triggerTime)} • أذان ${np.displayName(nt)} ${Fmt.time(nt)}"
    }

    /** تجربة حقيقية: منبه بعد دقيقة يشغّل الأذان بنفس طريق الأذان الحقيقي */
    fun scheduleTest(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val t = System.currentTimeMillis() + 60_000L
        val (np, _) = Times.next(ctx)
        val pi = PendingIntent.getBroadcast(ctx, 101,
            Intent(ctx, AlarmReceiver::class.java).putExtra("test_adhan", true)
                .putExtra(Scheduler.EXTRA_PRAYER, np.key).putExtra(Scheduler.EXTRA_TIME, t),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val show = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try { am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi) }
        catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) }
        log(ctx, "تجربة: منبه الأذان بعد دقيقة")
        // نرجّع منبه الصلاة الحقيقي بعد التجربة (التجربة تسبقه)
    }
}
