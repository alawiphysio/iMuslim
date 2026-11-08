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
    fun countdown(diffMs: Long): String {
        val s = (diffMs / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}
