package com.alawi.mawaqit

import android.content.Context

/**
 * مكتبة المؤذنين.
 * كل مؤذن له ملفين داخل التطبيق في مجلد res/raw:
 *   adhan_<id>.mp3  (الأذان العادي)
 *   fajr_<id>.mp3   (أذان الفجر، اختياري)
 * أي ملف تضيفه هناك يظهر تلقائياً كمؤذن متاح.
 */
data class Muezzin(val id: String, val name: String, val place: String)

object Muezzins {
    val ALL = listOf(
        Muezzin("makkah", "أذان الحرم المكي", "المسجد الحرام"),
        Muezzin("madinah", "أذان الحرم النبوي", "المسجد النبوي")
    )
    const val DEFAULT = "makkah"

    private fun raw(ctx: Context, name: String): Int =
        ctx.resources.getIdentifier(name, "raw", ctx.packageName)

    fun isAvailable(ctx: Context, m: Muezzin) = raw(ctx, "adhan_${m.id}") != 0

    // بالاسم (مو بالرقم) عشان يبقى صالح بعد كل تحديث
    private fun uri(ctx: Context, name: String) = "android.resource://${ctx.packageName}/raw/$name"

    fun adhanUri(ctx: Context, m: Muezzin): String? =
        raw(ctx, "adhan_${m.id}").takeIf { it != 0 }?.let { uri(ctx, "adhan_${m.id}") }

    /** أذان الفجر (فيه «الصلاة خير من النوم»): الخاص بالشيخ، وإلا أذان فجر الحرم */
    fun fajrUri(ctx: Context, m: Muezzin): String? =
        raw(ctx, "fajr_${m.id}").takeIf { it != 0 }?.let { uri(ctx, "fajr_${m.id}") }
            ?: raw(ctx, "fajr_makkah").takeIf { it != 0 }?.let { uri(ctx, "fajr_makkah") }
            ?: adhanUri(ctx, m)

    /** أول تشغيل أو اختيار قديم تالف: نختار أذان الحرم */
    fun ensureDefault(ctx: Context) {
        val prefs = Prefs(ctx)
        val a = prefs.adhanUri
        val broken = a == null || (a.startsWith("android.resource://") && !a.contains("/raw/"))
        if (broken) ALL.firstOrNull { it.id == (prefs.muezzinId.takeIf { id -> ALL.any { it.id == id } } ?: DEFAULT) }?.let { select(ctx, it) }
    }

    fun select(ctx: Context, m: Muezzin): Boolean {
        val a = adhanUri(ctx, m) ?: return false
        val prefs = Prefs(ctx)
        prefs.adhanUri = a
        prefs.adhanName = "${m.name} • ${m.place}"
        prefs.fajrUri = fajrUri(ctx, m)
        prefs.fajrName = if (raw(ctx, "fajr_${m.id}") != 0) "${m.name} • أذان الفجر" else "أذان الفجر من الحرم المكي"
        prefs.muezzinId = m.id
        return true
    }
}
