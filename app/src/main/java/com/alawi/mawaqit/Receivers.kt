package com.alawi.mawaqit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        val type = try { EvType.valueOf(intent.getStringExtra(Scheduler.EXTRA_TYPE) ?: "REFRESH") } catch (e: Exception) { EvType.REFRESH }
        val p = Prayer.byKey(intent.getStringExtra(Scheduler.EXTRA_PRAYER))
        val t = intent.getLongExtra(Scheduler.EXTRA_TIME, System.currentTimeMillis())

        // قبل التنبيهات (غير الأذان نفسه) نتأكد إنك ما انتقلت لمدينة ثانية
        if (type != EvType.ADHAN) {
            try { AutoLocation.updateFromLastKnown(ctx) } catch (e: Exception) { }
        }

        try {
            when (type) {
                EvType.ADHAN -> if (p != null && prefs.adhanOn(p)) AdhanService.start(ctx, p, t)
                EvType.PRE -> if (p != null) Notifier.reminder(
                    ctx, 201,
                    "باقي ${Fmt.num(prefs.preMin)} دقائق على أذان ${p.displayName(t)}",
                    "الأذان الساعة ${Fmt.time(t + prefs.preMin * 60_000L)}"
                )
                EvType.IQAMA -> if (p != null) Notifier.reminder(
                    ctx, 202,
                    "حان وقت إقامة صلاة ${p.displayName(t)}",
                    "أقيموا الصلاة 🤍"
                )
                EvType.REFRESH -> {}
            }
        } catch (e: Exception) {
            // في حال منع النظام تشغيل الخدمة نرسل إشعار بالصوت على الأقل
            if (p != null) Notifier.reminder(ctx, 203, "حان الآن موعد أذان ${p.displayName(t)}", prefs.city)
        }

        PrayerWidget.updateAll(ctx)
        Scheduler.scheduleNext(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.ensureChannels(ctx)
        try { AutoLocation.updateFromLastKnown(ctx) } catch (e: Exception) { }
        Scheduler.scheduleNext(ctx)
        PrayerWidget.updateAll(ctx)
    }
}

object Notifier {
    const val CH_ADHAN = "adhan_v1"
    const val CH_REMIND = "remind_v1"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val adhan = NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعار وقت الأذان (الصوت يشتغل من التطبيق)"
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val remind = NotificationChannel(CH_REMIND, "تنبيهات قبل الأذان والإقامة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه قبل الأذان وتنبيه الإقامة"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(adhan)
        nm.createNotificationChannel(remind)
    }

    fun reminder(ctx: Context, id: Int, title: String, text: String) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(0xFFD9B44A.toInt())
            .build()
        try { nm.notify(id, n) } catch (e: SecurityException) { }
    }
}
