package com.alawi.mawaqit

import android.content.Context

/** مظهر التطبيق: تلقائي (نهار/ليل حسب الشروق والمغرب)، نهاري، ليلي، داكن */
object Look {
    enum class Mode(val key: String, val title: String, val emoji: String) {
        AUTO("auto", "تلقائي", "🌗"),
        DAY("day", "نهاري", "☀️"),
        NIGHT("night", "ليلي", "🌙"),
        DARK("dark", "داكن", "⚫")
    }

    fun mode(ctx: Context): Mode {
        val k = ctx.getSharedPreferences("look", Context.MODE_PRIVATE).getString("mode", "auto")
        return Mode.values().firstOrNull { it.key == k } ?: Mode.AUTO
    }

    fun setMode(ctx: Context, m: Mode) =
        ctx.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putString("mode", m.key).apply()

    /** نهار = من الشروق إلى المغرب */
    fun isDayNow(ctx: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        val t = Times.forDay(ctx, 0, now)
        now >= t[Prayer.SUNRISE.ordinal] && now < t[Prayer.MAGHRIB.ordinal]
    } catch (e: Exception) { false }

    /** المظهر الفعلي الحين */
    fun effective(ctx: Context): Mode = when (val m = mode(ctx)) {
        Mode.AUTO -> if (isDayNow(ctx)) Mode.DAY else Mode.NIGHT
        else -> m
    }

    fun cardColor(ctx: Context): Int = when (effective(ctx)) {
        Mode.DARK -> 0xF20A0A0A.toInt()
        Mode.DAY -> 0xC70D3A33.toInt()
        else -> 0xCC0F2E27.toInt()
    }
}
