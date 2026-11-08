package com.alawi.mawaqit

import android.content.Context
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("mawaqit", Context.MODE_PRIVATE)

    var lat: Double
        get() = sp.getString("lat", "21.4225")!!.toDouble()
        set(v) = sp.edit().putString("lat", v.toString()).apply()

    var lng: Double
        get() = sp.getString("lng", "39.8262")!!.toDouble()
        set(v) = sp.edit().putString("lng", v.toString()).apply()

    var city: String
        get() = sp.getString("city", "مكة المكرمة")!!
        set(v) = sp.edit().putString("city", v).apply()

    fun adhanOn(p: Prayer): Boolean = sp.getBoolean("adhan_${p.key}", true)
    fun setAdhanOn(p: Prayer, v: Boolean) = sp.edit().putBoolean("adhan_${p.key}", v).apply()

    var preOn: Boolean
        get() = sp.getBoolean("pre_on", true)
        set(v) = sp.edit().putBoolean("pre_on", v).apply()

    var preMin: Int
        get() = sp.getInt("pre_min", 10)
        set(v) = sp.edit().putInt("pre_min", v).apply()

    var iqamaOn: Boolean
        get() = sp.getBoolean("iqama_on", true)
        set(v) = sp.edit().putBoolean("iqama_on", v).apply()

    fun iqamaMin(p: Prayer): Int = sp.getInt("iqama_${p.key}", defaultIqama(p))
    fun setIqamaMin(p: Prayer, v: Int) = sp.edit().putInt("iqama_${p.key}", v).apply()

    /** تعديل يدوي بالدقائق لكل صلاة (+ أو -) لمطابقة مسجدك */
    fun adjust(p: Prayer): Int = sp.getInt("adj_${p.key}", 0)
    fun setAdjust(p: Prayer, v: Int) = sp.edit().putInt("adj_${p.key}", v).apply()

    var adhanUri: String?
        get() = sp.getString("adhan_uri", null)
        set(v) = sp.edit().putString("adhan_uri", v).apply()

    var adhanName: String?
        get() = sp.getString("adhan_name", null)
        set(v) = sp.edit().putString("adhan_name", v).apply()

    /** أذان الفجر (فيه «الصلاة خير من النوم») */
    var fajrUri: String?
        get() = sp.getString("fajr_uri", null)
        set(v) = sp.edit().putString("fajr_uri", v).apply()

    var fajrName: String?
        get() = sp.getString("fajr_name", null)
        set(v) = sp.edit().putString("fajr_name", v).apply()

    /** الأذان يشتغل حتى لو الجوال صامت */
    var adhanInSilent: Boolean
        get() = sp.getBoolean("adhan_silent", true)
        set(v) = sp.edit().putBoolean("adhan_silent", v).apply()

    /** الأذان يشتغل حتى في وضع عدم الإزعاج */
    var adhanInDnd: Boolean
        get() = sp.getBoolean("adhan_dnd", true)
        set(v) = sp.edit().putBoolean("adhan_dnd", v).apply()

    /** وميض الفلاش وقت الأذان لما الجوال صامت */
    var flashOn: Boolean
        get() = sp.getBoolean("flash_on", true)
        set(v) = sp.edit().putBoolean("flash_on", v).apply()

    /** شاشة الأذان الملونة الكاملة */
    var fullScreenOn: Boolean
        get() = sp.getBoolean("fullscreen_on", true)
        set(v) = sp.edit().putBoolean("fullscreen_on", v).apply()

    /** تذكير أذكار الصباح (بعد الفجر) والمساء (بعد العصر) */
    var adhkarRemind: Boolean
        get() = sp.getBoolean("adhkar_remind", true)
        set(v) = sp.edit().putBoolean("adhkar_remind", v).apply()

    /** تذكير الأذكار بعد كل صلاة */
    var afterPrayerRemind: Boolean
        get() = sp.getBoolean("after_prayer_remind", false)
        set(v) = sp.edit().putBoolean("after_prayer_remind", v).apply()

    // ---------- وضع المسجد ----------
    var mosqueOn: Boolean
        get() = sp.getBoolean("mosque_on", false)
        set(v) = sp.edit().putBoolean("mosque_on", v).apply()
    var mosqueMin: Int
        get() = sp.getInt("mosque_min", 25)
        set(v) = sp.edit().putInt("mosque_min", v).apply()
    /** true = صامت تام، false = اهتزاز */
    var mosqueSilent: Boolean
        get() = sp.getBoolean("mosque_silent", false)
        set(v) = sp.edit().putBoolean("mosque_silent", v).apply()
    var mosquePrevMode: Int
        get() = sp.getInt("mosque_prev", -1)
        set(v) = sp.edit().putInt("mosque_prev", v).apply()
    var mosqueSetMode: Int
        get() = sp.getInt("mosque_set", -1)
        set(v) = sp.edit().putInt("mosque_set", v).apply()

    // ---------- الإشعار الحي ----------
    var liveOn: Boolean
        get() = sp.getBoolean("live_on", true)
        set(v) = sp.edit().putBoolean("live_on", v).apply()

    // ---------- المناسبات ----------
    var fridayOn: Boolean
        get() = sp.getBoolean("friday_on", true)
        set(v) = sp.edit().putBoolean("friday_on", v).apply()
    var fastingOn: Boolean
        get() = sp.getBoolean("fasting_on", true)
        set(v) = sp.edit().putBoolean("fasting_on", v).apply()
    var ramadanOn: Boolean
        get() = sp.getBoolean("ramadan_on", true)
        set(v) = sp.edit().putBoolean("ramadan_on", v).apply()
    var suhoorMin: Int
        get() = sp.getInt("suhoor_min", 45)
        set(v) = sp.edit().putInt("suhoor_min", v).apply()
    var qiyamOn: Boolean
        get() = sp.getBoolean("qiyam_on", false)
        set(v) = sp.edit().putBoolean("qiyam_on", v).apply()

    // ---------- السفر ----------
    var homeLat: Double
        get() = sp.getString("home_lat", null)?.toDoubleOrNull() ?: lat
        set(v) = sp.edit().putString("home_lat", v.toString()).apply()
    var homeLng: Double
        get() = sp.getString("home_lng", null)?.toDoubleOrNull() ?: lng
        set(v) = sp.edit().putString("home_lng", v.toString()).apply()
    var homeCity: String
        get() = sp.getString("home_city", null) ?: city
        set(v) = sp.edit().putString("home_city", v).apply()
    val hasHome: Boolean get() = sp.contains("home_lat")
    var travelNotified: Boolean
        get() = sp.getBoolean("travel_notified", false)
        set(v) = sp.edit().putBoolean("travel_notified", v).apply()

    // ---------- المسبحة ----------
    var tasbihPhrase: Int
        get() = sp.getInt("tasbih_phrase", 0)
        set(v) = sp.edit().putInt("tasbih_phrase", v).apply()
    var tasbihTarget: Int
        get() = sp.getInt("tasbih_target", 33)
        set(v) = sp.edit().putInt("tasbih_target", v).apply()
    var tasbihCount: Int
        get() = sp.getInt("tasbih_count", 0)
        set(v) = sp.edit().putInt("tasbih_count", v).apply()
    var tasbihDayKey: String
        get() = sp.getString("tasbih_day", "")!!
        set(v) = sp.edit().putString("tasbih_day", v).apply()
    var tasbihToday: Int
        get() = sp.getInt("tasbih_today", 0)
        set(v) = sp.edit().putInt("tasbih_today", v).apply()
    var tasbihTotal: Long
        get() = sp.getLong("tasbih_total", 0L)
        set(v) = sp.edit().putLong("tasbih_total", v).apply()

    /** كل الإعدادات (للنسخ الاحتياطي) */
    fun dump(): Map<String, *> = sp.all
    fun restore(map: Map<String, Any?>) {
        val e = sp.edit()
        for ((k, v) in map) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
        }
        e.apply()
    }

    var muezzinId: String?
        get() = sp.getString("muezzin_id", null)
        set(v) = sp.edit().putString("muezzin_id", v).apply()

    /** التذكير الصوتي (التطبيق يكلّمك) */
    var voiceOn: Boolean
        get() = sp.getBoolean("voice_on", true)
        set(v) = sp.edit().putBoolean("voice_on", v).apply()

    /** الاسم اللي يناديك فيه */
    var userName: String
        get() = sp.getString("user_name", "أحمد")!!
        set(v) = sp.edit().putString("user_name", v).apply()

    var firstRun: Boolean
        get() = sp.getBoolean("first_run", true)
        set(v) = sp.edit().putBoolean("first_run", v).apply()

    companion object {
        fun defaultIqama(p: Prayer): Int = when (p) {
            Prayer.FAJR -> 25
            Prayer.MAGHRIB -> 10
            else -> 20
        }
    }
}

object Fmt {
    private val ar = Locale("ar")
    fun time(ms: Long): String = SimpleDateFormat("h:mm a", ar).format(Date(ms))
    fun date(ms: Long): String = SimpleDateFormat("EEEE d MMMM yyyy", ar).format(Date(ms))
    fun num(n: Int): String = NumberFormat.getInstance(ar).apply { isGroupingUsed = false }.format(n)
    /** "باقي ٤٥ دقيقة" أو "ساعة و ٢٠ دقيقة" أو "٣ ساعات و ٥ دقائق" */
    fun minutesLeft(diffMs: Long): String {
        val total = ((diffMs + 59_999L) / 60_000L).coerceAtLeast(0L).toInt()
        if (total == 0) return "حان الآن"
        val h = total / 60
        val m = total % 60
        val hPart = when (h) {
            0 -> ""
            1 -> "ساعة"
            2 -> "ساعتين"
            in 3..10 -> "${num(h)} ساعات"
            else -> "${num(h)} ساعة"
        }
        val mPart = when (m) {
            0 -> ""
            1 -> "دقيقة"
            2 -> "دقيقتين"
            in 3..10 -> "${num(m)} دقائق"
            else -> "${num(m)} دقيقة"
        }
        return when {
            hPart.isEmpty() -> mPart
            mPart.isEmpty() -> hPart
            else -> "$hPart و $mPart"
        }
    }

    fun countdown(diffMs: Long): String {
        val s = (diffMs / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}
