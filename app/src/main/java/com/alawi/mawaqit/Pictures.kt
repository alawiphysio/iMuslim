package com.alawi.mawaqit

import android.content.Context

/**
 * الصور الاحترافية (من Canva) داخل res/drawable-nodpi:
 *   img_walk.webp    رجل يمشي للمسجد (قبل الأذان)
 *   img_pray.webp    رجل على سجادة الصلاة (وقت الإقامة)
 *   img_haram.webp   المسجد الحرام
 *   img_nabawi.webp  المسجد النبوي
 * لو الصورة مو موجودة يرجع للرسمة البديلة.
 */
object Pictures {
    private fun id(ctx: Context, name: String): Int =
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)

    fun walker(ctx: Context): Int = id(ctx, "img_walk").takeIf { it != 0 } ?: R.drawable.fig_walker
    fun praying(ctx: Context): Int = id(ctx, "img_pray").takeIf { it != 0 } ?: R.drawable.fig_praying

    fun figure(ctx: Context, iqamaPhase: Boolean) = if (iqamaPhase) praying(ctx) else walker(ctx)

    /** صورة المسجد حسب المدينة، أو 0 لو ما فيه صورة (نستخدم الرسمة) */
    fun landmark(ctx: Context, type: Landmarks.Type): Int = when (type) {
        Landmarks.Type.HARAM -> id(ctx, "img_haram")
        Landmarks.Type.NABAWI -> id(ctx, "img_nabawi")
        else -> 0
    }
}
