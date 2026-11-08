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
        Muezzin("ali_mulla", "الشيخ علي ملا", "المسجد الحرام"),
        Muezzin("sraihi", "الشيخ عبدالمجيد السريحي", "المسجد الحرام"),
        Muezzin("essam_bukhari", "الشيخ عصام بخاري", "المسجد النبوي"),
        Muezzin("afasy", "الشيخ مشاري العفاسي", "الكويت"),
        Muezzin("refaat", "الشيخ محمد رفعت", "مصر")
    )

    private fun raw(ctx: Context, name: String): Int =
        ctx.resources.getIdentifier(name, "raw", ctx.packageName)

    fun isAvailable(ctx: Context, m: Muezzin) = raw(ctx, "adhan_${m.id}") != 0

    fun adhanUri(ctx: Context, m: Muezzin): String? =
        raw(ctx, "adhan_${m.id}").takeIf { it != 0 }?.let { "android.resource://${ctx.packageName}/$it" }

    /** أذان الفجر الخاص بالشيخ، ولو مو موجود يرجع الأذان العادي */
    fun fajrUri(ctx: Context, m: Muezzin): String? =
        raw(ctx, "fajr_${m.id}").takeIf { it != 0 }?.let { "android.resource://${ctx.packageName}/$it" }
            ?: adhanUri(ctx, m)

    fun select(ctx: Context, m: Muezzin): Boolean {
        val a = adhanUri(ctx, m) ?: return false
        val prefs = Prefs(ctx)
        prefs.adhanUri = a
        prefs.adhanName = "${m.name} • ${m.place}"
        prefs.fajrUri = fajrUri(ctx, m)
        prefs.fajrName = if (raw(ctx, "fajr_${m.id}") != 0) "${m.name} • أذان الفجر" else "${m.name} (نفس الأذان العادي)"
        prefs.muezzinId = m.id
        return true
    }
}
