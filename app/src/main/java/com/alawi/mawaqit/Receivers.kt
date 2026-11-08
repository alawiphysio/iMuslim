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
        val t = intent.getLongExtra(Scheduler.EXTRA_TIME, System.currentTimeMillis())

        // كل الأحداث اللي بنفس الوقت
        var evs = Scheduler.eventsAt(ctx, t)
        if (evs.isEmpty()) {
            val type = try { EvType.valueOf(intent.getStringExtra(Scheduler.EXTRA_TYPE) ?: "REFRESH") } catch (e: Exception) { EvType.REFRESH }
            evs = listOf(Ev(t, type, Prayer.byKey(intent.getStringExtra(Scheduler.EXTRA_PRAYER))))
        }

        // قبل التنبيهات (غير الأذان نفسه) نتأكد إنك ما انتقلت لمدينة ثانية
        if (evs.none { it.type == EvType.ADHAN }) {
            try { if (AutoLocation.updateFromLastKnown(ctx)) Travel.check(ctx) } catch (e: Exception) { }
        }

        val speech = ArrayList<String>()
        var speechTitle = ""
        var speechPrayer: Prayer? = null
        val mosqueNow = evs.any { it.type == EvType.MOSQUE_START }

        for (ev in evs) {
            val p = ev.prayer
            try {
                when (ev.type) {
                    EvType.ADHAN -> if (p != null && prefs.adhanOn(p)) AdhanService.start(ctx, p, t)
                    EvType.PRE -> if (p != null) {
                        val adhanT = t + prefs.preMin * 60_000L
                        val title = "باقي ${Fmt.num(prefs.preMin)} دقائق على أذان ${p.displayName(adhanT)}"
                        Notifier.reminder(ctx, 201, title, "الأذان الساعة ${Fmt.time(adhanT)} • توضأ واستعد 🤍")
                        speech.add(Voice.before(prefs.userName, p, adhanT, prefs.preMin)); speechTitle = title; speechPrayer = p
                    }
                    EvType.IQAMA -> if (p != null) {
                        val title = "حان وقت إقامة صلاة ${p.displayName(t)}"
                        Notifier.reminder(ctx, 202, title, "أقيموا الصلاة 🤍", null, prayedActions(ctx, p, t, 202))
                        // في وضع المسجد ما نتكلم بصوت عالي
                        if (!mosqueNow) { speech.add(Voice.iqama(prefs.userName, p, t)); speechTitle = title; speechPrayer = p }
                    }
                    EvType.ADHKAR_MORNING, EvType.ADHKAR_EVENING, EvType.AFTER_PRAYER -> if (p != null) {
                        val (cat, title, say) = when (ev.type) {
                            EvType.ADHKAR_MORNING -> Triple(Adhkar.MORNING_EVENING, "☀️ أذكار الصباح",
                                "يا ${prefs.userName}، لا تنسى أذكار الصباح، حصّن نفسك ليومك.")
                            EvType.ADHKAR_EVENING -> Triple(Adhkar.MORNING_EVENING, "🌇 أذكار المساء",
                                "يا ${prefs.userName}، حان وقت أذكار المساء.")
                            else -> Triple(Adhkar.AFTER_PRAYER, "🤲 الأذكار بعد الصلاة",
                                "يا ${prefs.userName}، لا تنسى الأذكار بعد صلاة ${p.displayName(t)}.")
                        }
                        Notifier.reminder(ctx, 210, title, "اضغط لقراءتها والاستماع لها",
                            Intent(ctx, AdhkarReaderActivity::class.java).putExtra("cat", cat),
                            if (ev.type == EvType.AFTER_PRAYER) prayedActions(ctx, p, t, 210) else emptyList())
                        speech.add(say); speechTitle = title; speechPrayer = p
                    }
                    EvType.MOSQUE_START -> Mosque.start(ctx)
                    EvType.MOSQUE_END -> Mosque.end(ctx)
                    EvType.FRIDAY_KAHF -> {
                        Notifier.reminder(ctx, 240, "📖 جمعة مباركة", "اضغط لقراءة سورة الكهف • وأكثر من الصلاة على النبي ﷺ",
                            Intent(ctx, QuranReaderActivity::class.java).putExtra("s", 18).putExtra("a", 1))
                        speech.add("جمعة مباركة يا ${prefs.userName}. لا تنسى قراءة سورة الكهف، وأكثر من الصلاة على النبي صلى الله عليه وسلم.")
                        speechTitle = "جمعة مباركة"; speechPrayer = p
                    }
                    EvType.FRIDAY_HOUR -> {
                        Notifier.reminder(ctx, 241, "🤲 ساعة الاستجابة", "آخر ساعة من يوم الجمعة، أكثر من الدعاء قبل المغرب",
                            Intent(ctx, AdhkarActivity::class.java))
                        speech.add("يا ${prefs.userName}، هذي آخر ساعة من يوم الجمعة، ساعة ترجى فيها الإجابة، فأكثر من الدعاء.")
                        speechTitle = "ساعة الاستجابة"; speechPrayer = p
                    }
                    EvType.FAST_EVE -> {
                        val reason = Fasting.reason(t + 12 * 3_600_000L)
                        if (reason != null) {
                            Notifier.reminder(ctx, 250, "🌙 بكرة يُستحب الصيام", "$reason • انوِ الصيام ولا تنسى السحور")
                            speech.add("يا ${prefs.userName}، بكرة $reason، يستحب صيامه. انوِ الصيام ولا تنسى السحور.")
                            speechTitle = "تذكير الصيام"; speechPrayer = p
                        }
                    }
                    EvType.SUHOOR -> {
                        Notifier.reminder(ctx, 251, "🍽 وقت السحور", "باقي ${Fmt.num(prefs.suhoorMin)} دقيقة على أذان الفجر. تسحّروا فإن في السحور بركة")
                        speech.add("يا ${prefs.userName}، قم تسحّر، باقي ${prefs.suhoorMin} دقيقة على أذان الفجر. تسحّروا فإن في السحور بركة.")
                        speechTitle = "السحور"; speechPrayer = p
                    }
                    EvType.IFTAR -> {
                        Notifier.reminder(ctx, 252, "🌅 حان الإفطار بعد دقيقة", "اضغط لدعاء الإفطار",
                            Intent(ctx, AdhkarReaderActivity::class.java).putExtra("cat", 68))
                    }
                    EvType.QIYAM -> {
                        Notifier.reminder(ctx, 253, "🌌 الثلث الأخير من الليل",
                            "ينزل ربنا إلى السماء الدنيا، فيقول: من يدعوني فأستجيب له. قم صلِّ ركعتين")
                    }
                    EvType.KHATMA -> if (!Khatma.doneToday(ctx)) {
                        val (s, a) = Quran.pageStart(ctx, Khatma.nextRange(ctx).first)
                        Notifier.reminder(ctx, 260, "📖 ورد الختمة", Khatma.describe(ctx),
                            Intent(ctx, QuranReaderActivity::class.java).putExtra("s", s).putExtra("a", a))
                        speech.add("يا ${prefs.userName}، لا تنسى وردك من القرآن اليوم.")
                        speechTitle = "ورد الختمة"; speechPrayer = p
                    }
                    EvType.REFRESH -> { Travel.check(ctx); AyahWidget.updateAll(ctx) }
                }
            } catch (e: Exception) {
                // في حال منع النظام تشغيل الخدمة نرسل إشعار بالصوت على الأقل
                if (ev.type == EvType.ADHAN && p != null) Notifier.reminder(ctx, 203, "حان الآن موعد أذان ${p.displayName(t)}", prefs.city)
            }
        }

        // كلام واحد يجمع كل التذكيرات اللي بنفس الوقت (إلا لو فيه أذان شغال)
        if (prefs.voiceOn && speech.isNotEmpty() && evs.none { it.type == EvType.ADHAN && it.prayer?.let(prefs::adhanOn) == true }) {
            try { AdhanService.say(ctx, speechPrayer ?: Prayer.DHUHR, t, speechTitle, speech.joinToString(" ")) } catch (e: Exception) { }
        }

        PrayerWidget.updateAll(ctx)
        LiveNotif.update(ctx)
        Scheduler.scheduleNext(ctx)
    }

    private fun prayedActions(ctx: Context, p: Prayer, t: Long, nid: Int): List<Notification.Action> {
        val day = Tracker.dayKey(t)
        fun act(label: String, v: Int, code: Int): Notification.Action {
            val pi = PendingIntent.getBroadcast(ctx, code,
                Intent(ctx, PrayerActionReceiver::class.java)
                    .putExtra(Scheduler.EXTRA_PRAYER, p.key).putExtra("day", day).putExtra("v", v).putExtra("nid", nid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_notif), label, pi).build()
        }
        return listOf(act("✓ صليت", 1, 400 + p.ordinal * 2), act("👥 صليت جماعة", 2, 401 + p.ordinal * 2))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.ensureChannels(ctx)
        try { AutoLocation.updateFromLastKnown(ctx) } catch (e: Exception) { }
        Scheduler.scheduleNext(ctx)
        PrayerWidget.updateAll(ctx)
        LiveNotif.update(ctx)
    }
}

object Notifier {
    const val CH_ADHAN = "adhan_v2"
    const val CH_REMIND = "remind_v1"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val adhan = NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعار وقت الأذان (الصوت يشتغل من التطبيق)"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            enableLights(true)
            lightColor = 0xFFD9B44A.toInt()
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

    fun reminder(ctx: Context, id: Int, title: String, text: String, target: Intent? = null,
                 actions: List<Notification.Action> = emptyList()) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            ctx, id, target ?: Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(0xFFD9B44A.toInt())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .also { b -> actions.forEach { b.addAction(it) } }
            .build()
        try { nm.notify(id, n) } catch (e: SecurityException) { }
    }
}
