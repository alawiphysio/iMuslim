package com.alawi.mawaqit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.IslamicCalendar
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import org.json.JSONObject
import java.util.Calendar

// =================== قيام الليل والضحى ===================
object Night {
    /** بداية الثلث الأخير من الليل اللي يبدأ بمغرب اليوم d */
    fun lastThird(ctx: Context, d: Int, now: Long = System.currentTimeMillis()): Long {
        val maghrib = Times.forDay(ctx, d, now)[Prayer.MAGHRIB.ordinal]
        val fajr = Times.forDay(ctx, d + 1, now)[Prayer.FAJR.ordinal]
        val night = fajr - maghrib
        return fajr - night / 3
    }

    /** وقت الضحى: من ارتفاع الشمس (بعد الشروق بـ ١٥ دقيقة) إلى قبل الظهر بـ ١٠ دقائق */
    fun duha(ctx: Context, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val arr = Times.forDay(ctx, 0, now)
        return Pair(arr[Prayer.SUNRISE.ordinal] + 15 * 60_000L, arr[Prayer.DHUHR.ordinal] - 10 * 60_000L)
    }
}

// =================== الصيام ===================
object Fasting {
    private fun hijri(millis: Long): Pair<Int, Int> {
        val d = Hijri.date(millis)
        return Pair(d.month, d.day)
    }

    /** أيام يحرم صيامها: العيدين وأيام التشريق */
    private fun forbidden(m: Int, d: Int) =
        (m == IslamicCalendar.SHAWWAL && d == 1) || (m == IslamicCalendar.DHU_AL_HIJJAH && d in 10..13)

    /** سبب صيام يوم معيّن (أو null) */
    fun reason(dayMillis: Long): String? {
        val (m, d) = hijri(dayMillis)
        if (forbidden(m, d)) return null
        if (m == IslamicCalendar.RAMADAN) return null
        if (m == IslamicCalendar.DHU_AL_HIJJAH && d == 9) return "يوم عرفة، صيامه يكفّر السنة الماضية والباقية"
        if (m == IslamicCalendar.MUHARRAM && d == 10) return "يوم عاشوراء، صيامه يكفّر السنة الماضية"
        if (m == IslamicCalendar.MUHARRAM && d == 9) return "تاسوعاء، يُستحب صيامه مع عاشوراء"
        if (m == IslamicCalendar.DHU_AL_HIJJAH && d in 1..8) return "من العشر الأوائل من ذي الحجة"
        if (d in 13..15) return "من الأيام البيض (${Fmt.num(d)} من الشهر الهجري)"
        val c = Calendar.getInstance(); c.timeInMillis = dayMillis
        return when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "يوم الاثنين"
            Calendar.THURSDAY -> "يوم الخميس"
            else -> null
        }
    }
}

// =================== وضع المسجد ===================
object Mosque {
    fun start(ctx: Context) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return // أصلاً صامت
        val target = if (prefs.mosqueSilent && nm?.isNotificationPolicyAccessGranted == true)
            AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE
        try {
            prefs.mosquePrevMode = am.ringerMode
            am.ringerMode = target
            prefs.mosqueSetMode = target
            Notifier.reminder(ctx, 220, "🕌 وضع المسجد", "الجوال صار ${if (target == AudioManager.RINGER_MODE_SILENT) "صامت" else "على الاهتزاز"} لمدة ${Fmt.num(prefs.mosqueMin)} دقيقة")
        } catch (e: SecurityException) {
            prefs.mosquePrevMode = -1
        }
    }

    fun end(ctx: Context) {
        val prefs = Prefs(ctx)
        val prev = prefs.mosquePrevMode
        if (prev < 0) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        // نرجعه بس لو أنت ما غيّرته بنفسك
        if (am.ringerMode == prefs.mosqueSetMode) {
            try { am.ringerMode = prev } catch (e: SecurityException) { }
        }
        prefs.mosquePrevMode = -1
        prefs.mosqueSetMode = -1
        ctx.getSystemService(NotificationManager::class.java)?.cancel(220)
    }
}

// =================== الإشعار الحي ===================
object LiveNotif {
    private const val CH = "live_v1"
    private const val ID = 300

    fun update(ctx: Context) {
        val prefs = Prefs(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!prefs.liveOn) { nm.cancel(ID); return }
        nm.createNotificationChannel(NotificationChannel(CH, "عدّاد الصلاة القادمة", NotificationManager.IMPORTANCE_LOW).apply {
            description = "إشعار دائم فيه الصلاة القادمة والعدّاد"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val now = System.currentTimeMillis()
        val st = Times.state(ctx, now)
        val name = st.prayer.displayName(st.adhanT)
        val title = if (st.iqamaPhase) "الإقامة لصلاة $name ${Fmt.time(st.iqamaT)}" else "$name ${Fmt.time(st.adhanT)}"
        val open = PendingIntent.getActivity(ctx, 301, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(if (st.iqamaPhase) "باقي على الإقامة" else "باقي على الأذان • ${prefs.city}")
            .setSubText(Hijri.format(now))
            .setWhen(st.target)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setColor(C_GOLD)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        try { nm.notify(ID, n) } catch (e: SecurityException) { }
    }
}

// =================== السفر ===================
object Travel {
    const val QASR_KM = 80f

    fun distanceKm(prefs: Prefs): Float {
        val r = FloatArray(1)
        Location.distanceBetween(prefs.homeLat, prefs.homeLng, prefs.lat, prefs.lng, r)
        return r[0] / 1000f
    }

    fun isTraveling(ctx: Context): Boolean {
        val p = Prefs(ctx)
        return p.hasHome && distanceKm(p) >= QASR_KM
    }

    /** نحفظ مدينتك أول مرة، ونرسل تنبيه مرة وحدة لكل سفرة */
    fun check(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.hasHome) { p.homeLat = p.lat; p.homeLng = p.lng; p.homeCity = p.city; return }
        if (isTraveling(ctx)) {
            if (!p.travelNotified) {
                p.travelNotified = true
                Notifier.reminder(ctx, 230, "✈️ أنت مسافر الآن",
                    "تبعد ${Fmt.num(distanceKm(p).toInt())} كم عن ${p.homeCity}. يجوز لك قصر الرباعية والجمع. والأوقات تحدّثت حسب موقعك.")
            }
        } else p.travelNotified = false
    }

    fun setHomeHere(ctx: Context) {
        val p = Prefs(ctx)
        p.homeLat = p.lat; p.homeLng = p.lng; p.homeCity = p.city; p.travelNotified = false
    }
}

// =================== النسخ الاحتياطي ===================
object Backup {
    fun export(ctx: Context, uri: Uri): Boolean = try {
        val root = JSONObject()
        root.put("app", "mawaqit-ahmed")
        root.put("version", 1)
        root.put("prefs", JSONObject(Prefs(ctx).dump().filterValues { it != null }))
        root.put("tracker", JSONObject(Tracker.dump(ctx)))
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: Exception) { false }

    fun import(ctx: Context, uri: Uri): Boolean = try {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        val root = JSONObject(text)
        if (root.optString("app") != "mawaqit-ahmed") throw IllegalArgumentException()
        fun toMap(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { k ->
            when (val v = o.get(k)) { is Double -> v.toFloat(); else -> v }
        }
        Prefs(ctx).restore(toMap(root.getJSONObject("prefs")))
        Tracker.restore(ctx, toMap(root.optJSONObject("tracker") ?: JSONObject()))
        true
    } catch (e: Exception) { false }
}
