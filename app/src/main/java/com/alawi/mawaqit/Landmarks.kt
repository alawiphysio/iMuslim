package com.alawi.mawaqit

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.location.Location
import android.view.View

/** رسمة أشهر مسجد في المدينة اللي أنت فيها */
object Landmarks {

    enum class Type(val title: String) {
        HARAM("المسجد الحرام"),
        NABAWI("المسجد النبوي الشريف"),
        AQSA("المسجد الأقصى"),
        GENERIC("")
    }

    private fun km(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, r)
        return r[0] / 1000f
    }

    fun detect(lat: Double, lng: Double): Type = when {
        km(lat, lng, 21.4225, 39.8262) < 45f -> Type.HARAM
        km(lat, lng, 24.4672, 39.6111) < 45f -> Type.NABAWI
        km(lat, lng, 31.7780, 35.2354) < 30f -> Type.AQSA
        else -> Type.GENERIC
    }

    // ---------------- أدوات رسم ----------------

    private fun goldPaint(top: Float, bottom: Float, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top, 0f, bottom, C_GOLD_LIGHT, 0xFF8C6B1F.toInt(), Shader.TileMode.CLAMP)
        this.alpha = alpha
    }

    private fun creamPaint(top: Float, bottom: Float, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top, 0f, bottom, 0xFFFFF7E6.toInt(), 0xFFC9BFA8.toInt(), Shader.TileMode.CLAMP)
        this.alpha = alpha
    }

    /** منارة: جسم + شرفتين + رأس مدبب + هلال */
    private fun minaret(c: Canvas, x: Float, baseY: Float, height: Float, width: Float, p: Paint, finial: Paint) {
        val top = baseY - height
        c.drawRect(x - width / 2, top + height * 0.12f, x + width / 2, baseY, p)
        // شرفات
        for (f in floatArrayOf(0.30f, 0.62f)) {
            val y = top + height * f
            c.drawRect(x - width * 0.95f, y, x + width * 0.95f, y + height * 0.025f, p)
        }
        // الجزء العلوي الأرفع
        c.drawRect(x - width * 0.38f, top + height * 0.04f, x + width * 0.38f, top + height * 0.13f, p)
        // الرأس المدبب
        val cone = Path()
        cone.moveTo(x - width * 0.45f, top + height * 0.05f)
        cone.quadTo(x - width * 0.2f, top - height * 0.02f, x, top - height * 0.07f)
        cone.quadTo(x + width * 0.2f, top - height * 0.02f, x + width * 0.45f, top + height * 0.05f)
        cone.close()
        c.drawPath(cone, p)
        crescent(c, x, top - height * 0.105f, width * 0.32f, finial)
    }

    private fun crescent(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        val path = Path()
        path.addCircle(x, y, r, Path.Direction.CW)
        val cut = Path()
        cut.addCircle(x + r * 0.45f, y - r * 0.2f, r * 0.85f, Path.Direction.CW)
        path.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(path, p)
    }

    /** أروقة بأقواس */
    private fun arcade(c: Canvas, left: Float, right: Float, top: Float, bottom: Float, count: Int, body: Paint) {
        c.drawRect(left, top, right, bottom, body)
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99061512.toInt() }
        val step = (right - left) / count
        for (i in 0 until count) {
            val l = left + step * i + step * 0.2f
            val r = left + step * (i + 1) - step * 0.2f
            val archTop = top + (bottom - top) * 0.3f
            val path = Path()
            path.moveTo(l, bottom)
            path.lineTo(l, archTop + (r - l) / 2)
            path.quadTo(l, archTop, (l + r) / 2, archTop - (r - l) * 0.15f)
            path.quadTo(r, archTop, r, archTop + (r - l) / 2)
            path.lineTo(r, bottom)
            path.close()
            c.drawPath(path, hole)
        }
    }

    // ---------------- الرسومات ----------------

    fun draw(c: Canvas, type: Type, w: Float, h: Float) {
        when (type) {
            Type.HARAM -> haram(c, w, h)
            Type.NABAWI -> nabawi(c, w, h)
            Type.AQSA -> aqsa(c, w, h)
            Type.GENERIC -> generic(c, w, h)
        }
    }

    private fun haram(c: Canvas, w: Float, h: Float) {
        val base = h * 0.97f
        val gold = goldPaint(0f, h)
        val goldDim = goldPaint(0f, h, 95)
        val finial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_GOLD_LIGHT }

        // برج الساعة في الخلفية
        val tx = w * 0.5f
        val tw = w * 0.085f
        c.drawRect(tx - tw * 1.5f, base - h * 0.45f, tx + tw * 1.5f, base, goldDim)
        c.drawRect(tx - tw / 2, h * 0.16f, tx + tw / 2, base - h * 0.45f, goldDim)
        val clockY = h * 0.25f
        val clockR = tw * 0.42f
        c.drawCircle(tx, clockY, clockR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6F0DC.toInt() })
        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B5418.toInt(); strokeWidth = clockR * 0.12f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(tx, clockY, tx, clockY - clockR * 0.7f, hand)
        c.drawLine(tx, clockY, tx + clockR * 0.5f, clockY, hand)
        val spire = Path()
        spire.moveTo(tx - tw * 0.35f, h * 0.16f)
        spire.lineTo(tx, h * 0.04f)
        spire.lineTo(tx + tw * 0.35f, h * 0.16f)
        spire.close()
        c.drawPath(spire, goldDim)
        crescent(c, tx, h * 0.025f, tw * 0.22f, finial)

        // مبنى الحرم بالأروقة
        arcade(c, w * 0.04f, w * 0.96f, base - h * 0.24f, base, 14, goldPaint(base - h * 0.24f, base, 200))

        // المآذن
        val mh = h * 0.78f
        val mw = w * 0.026f
        for (x in floatArrayOf(0.08f, 0.17f, 0.26f, 0.74f, 0.83f, 0.92f)) {
            val scale = if (x == 0.08f || x == 0.92f) 0.88f else 1f
            minaret(c, w * x, base, mh * scale, mw, gold, finial)
        }

        // المطاف
        val mataf = Paint(Paint.ANTI_ALIAS_FLAG)
        mataf.shader = RadialGradient(w * 0.5f, base, w * 0.3f,
            intArrayOf(0xEEF8F3E6.toInt(), 0x00F8F3E6), null, Shader.TileMode.CLAMP)
        c.drawOval(RectF(w * 0.2f, base - h * 0.09f, w * 0.8f, base + h * 0.06f), mataf)

        // الكعبة المشرفة (منظور بسيط: وجه + جانب)
        val kw = w * 0.15f
        val kh = h * 0.30f
        val kl = w * 0.5f - kw * 0.62f
        val kb = base - h * 0.02f
        val kt = kb - kh
        val sideW = kw * 0.38f
        val skew = kh * 0.10f
        val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(kl, kt, kl + kw, kb, 0xFF1E1E1E.toInt(), 0xFF050505.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(kl, kt, kl + kw, kb, front)
        val side = Path()
        side.moveTo(kl + kw, kt)
        side.lineTo(kl + kw + sideW, kt - skew)
        side.lineTo(kl + kw + sideW, kb - skew)
        side.lineTo(kl + kw, kb)
        side.close()
        c.drawPath(side, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
        val top = Path()
        top.moveTo(kl, kt)
        top.lineTo(kl + sideW, kt - skew)
        top.lineTo(kl + kw + sideW, kt - skew)
        top.lineTo(kl + kw, kt)
        top.close()
        c.drawPath(top, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() })

        // الحزام الذهبي
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, kt, 0f, kt + kh * 0.1f, C_GOLD_LIGHT, C_GOLD, Shader.TileMode.CLAMP)
        }
        val by = kt + kh * 0.2f
        val bh = kh * 0.07f
        c.drawRect(kl, by, kl + kw, by + bh, band)
        val sideBand = Path()
        sideBand.moveTo(kl + kw, by)
        sideBand.lineTo(kl + kw + sideW, by - skew)
        sideBand.lineTo(kl + kw + sideW, by - skew + bh)
        sideBand.lineTo(kl + kw, by + bh)
        sideBand.close()
        c.drawPath(sideBand, Paint(band).apply { alpha = 190 })

        // الباب الذهبي
        val dl = kl + kw * 0.62f
        c.drawRoundRect(RectF(dl, kb - kh * 0.55f, dl + kw * 0.22f, kb - kh * 0.12f), kw * 0.02f, kw * 0.02f, band)
    }

    private fun nabawi(c: Canvas, w: Float, h: Float) {
        val base = h * 0.97f
        val cream = creamPaint(0f, h)
        val finial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_GOLD_LIGHT }

        // المآذن
        val mh = h * 0.86f
        val mw = w * 0.03f
        for (x in floatArrayOf(0.07f, 0.18f, 0.82f, 0.93f)) minaret(c, w * x, base, mh, mw, cream, finial)
        minaret(c, w * 0.66f, base, mh * 0.9f, mw, cream, finial)

        // الأروقة
        arcade(c, w * 0.03f, w * 0.97f, base - h * 0.26f, base, 13, creamPaint(base - h * 0.26f, base, 230))

        // القبة الفضية الصغيرة
        val sx = w * 0.32f
        val sr = w * 0.06f
        val sBase = base - h * 0.26f
        val silver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, sBase - sr * 2, 0f, sBase, 0xFFE8EEF0.toInt(), 0xFF9AA6AA.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawArc(RectF(sx - sr, sBase - sr, sx + sr, sBase + sr), 180f, 180f, true, silver)

        // القبة الخضراء
        val gx = w * 0.5f
        val gr = w * 0.1f
        val drumTop = base - h * 0.36f
        c.drawRect(gx - gr * 0.95f, drumTop, gx + gr * 0.95f, base - h * 0.26f, cream)
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(gx - gr, 0f, gx + gr, 0f,
                intArrayOf(0xFF2FA36A.toInt(), 0xFF1E7F4F.toInt(), 0xFF0F5233.toInt()), null, Shader.TileMode.CLAMP)
        }
        val dome = Path()
        dome.moveTo(gx - gr, drumTop)
        dome.cubicTo(gx - gr * 1.05f, drumTop - gr * 1.1f, gx - gr * 0.25f, drumTop - gr * 1.35f, gx, drumTop - gr * 1.7f)
        dome.cubicTo(gx + gr * 0.25f, drumTop - gr * 1.35f, gx + gr * 1.05f, drumTop - gr * 1.1f, gx + gr, drumTop)
        dome.close()
        c.drawPath(dome, green)
        // لمعة على القبة
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(gx - gr * 0.4f, drumTop - gr * 0.8f, gr * 0.6f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        c.drawPath(dome, shine)
        c.drawRect(gx - gr * 0.04f, drumTop - gr * 1.95f, gx + gr * 0.04f, drumTop - gr * 1.68f, finial)
        crescent(c, gx, drumTop - gr * 2.08f, gr * 0.15f, finial)
    }

    private fun aqsa(c: Canvas, w: Float, h: Float) {
        val base = h * 0.97f
        val stone = creamPaint(0f, h, 220)
        c.drawRect(w * 0.05f, base - h * 0.08f, w * 0.95f, base, stone)
        val cx = w * 0.5f
        val ow = w * 0.26f
        val octTop = base - h * 0.34f
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - ow, 0f, cx + ow, 0f,
                intArrayOf(0xFF2C6CB5.toInt(), 0xFF1D4F8C.toInt(), 0xFF153A68.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(cx - ow, octTop, cx + ow, base - h * 0.08f, blue)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; strokeWidth = w * 0.004f }
        for (i in 1..6) { val x = cx - ow + ow * 2 * i / 7; c.drawLine(x, octTop, x, base - h * 0.08f, line) }
        val drumR = ow * 0.48f
        c.drawRect(cx - drumR, octTop - h * 0.08f, cx + drumR, octTop, blue)
        val gold = goldPaint(octTop - h * 0.5f, octTop)
        val dTop = octTop - h * 0.08f
        val dome = Path()
        dome.moveTo(cx - drumR * 1.05f, dTop)
        dome.cubicTo(cx - drumR * 1.1f, dTop - drumR * 1.2f, cx - drumR * 0.3f, dTop - drumR * 1.3f, cx, dTop - drumR * 1.45f)
        dome.cubicTo(cx + drumR * 0.3f, dTop - drumR * 1.3f, cx + drumR * 1.1f, dTop - drumR * 1.2f, cx + drumR * 1.05f, dTop)
        dome.close()
        c.drawPath(dome, gold)
        crescent(c, cx, dTop - drumR * 1.65f, drumR * 0.14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_GOLD_LIGHT })
    }

    private fun generic(c: Canvas, w: Float, h: Float) {
        c.drawPath(Art.mosquePath(w, h * 0.97f), goldPaint(0f, h, 220))
    }
}

/** View لرسمة المسجد حسب الموقع، مع ظهور تدريجي */
class LandmarkView(ctx: Context) : View(ctx) {
    var type: Landmarks.Type = Landmarks.Type.GENERIC
        set(v) {
            if (field != v) { field = v; invalidate() }
        }

    override fun onDraw(c: Canvas) {
        Landmarks.draw(c, type, width.toFloat(), height.toFloat())
    }
}
