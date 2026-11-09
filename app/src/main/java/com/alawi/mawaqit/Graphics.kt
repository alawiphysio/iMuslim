package com.alawi.mawaqit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

const val C_GOLD = 0xFFD9B44A.toInt()
const val C_GOLD_LIGHT = 0xFFF6DE8D.toInt()
const val C_TEXT = 0xFFF4EFE1.toInt()
const val C_BG_TOP = 0xFF061512.toInt()
const val C_BG_BOTTOM = 0xFF0E3A30.toInt()

/** دوال رسم مشتركة بين واجهة التطبيق والويدجت */
object Art {

    // ---------------- القمر ----------------
    /** frac: عمر القمر ÷ طول الشهر (0 محاق، 0.5 بدر) */
    fun drawMoon(c: Canvas, cx: Float, cy: Float, r: Float, frac: Double, glow: Float = 1f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // توهج
        val illum = ((1 - cos(2 * PI * frac)) / 2).toFloat()
        val glowR = r * 1.9f
        p.shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(Color.argb((90 * illum * glow).toInt().coerceIn(0, 255), 246, 222, 141), Color.TRANSPARENT),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, glowR, p)

        // الجزء المظلم
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
            0xFF2C3D39.toInt(), 0xFF141F1D.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)

        // الجزء المضيء
        val lit = litPath(cx, cy, r, frac)
        p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.6f,
            0xFFFFF8E1.toInt(), 0xFFE6CF8E.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(lit, p)

        // فوهات خفيفة داخل الجزء المضيء
        c.save()
        c.clipPath(lit)
        p.shader = null
        p.color = 0x22806A2E
        val craters = arrayOf(
            floatArrayOf(-0.35f, -0.25f, 0.18f), floatArrayOf(0.25f, 0.30f, 0.22f),
            floatArrayOf(0.30f, -0.40f, 0.10f), floatArrayOf(-0.20f, 0.45f, 0.12f),
            floatArrayOf(0.05f, 0.0f, 0.08f)
        )
        for (cr in craters) c.drawCircle(cx + cr[0] * r, cy + cr[1] * r, cr[2] * r, p)
        c.restore()

        // حافة رفيعة
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.03f
        p.color = 0x33F6DE8D
        c.drawCircle(cx, cy, r, p)
    }

    private fun litPath(cx: Float, cy: Float, r: Float, frac: Double): Path {
        val path = Path()
        val waxing = frac < 0.5
        val k = if (waxing) cos(2 * PI * frac) else -cos(2 * PI * frac)
        val n = 60
        // الحافة: من فوق لتحت
        for (i in 0..n) {
            val a = -PI / 2 + PI * i / n
            val x = if (waxing) cx + r * cos(a) else cx - r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        // خط الظل: من تحت لفوق
        for (i in 0..n) {
            val a = PI / 2 - PI * i / n
            val x = if (waxing) cx + r * k * cos(a) else cx - r * k * cos(a)
            val y = cy + r * sin(a)
            path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    // ---------------- حلقة العدّاد ----------------
    fun drawRing(c: Canvas, cx: Float, cy: Float, r: Float, stroke: Float, progress: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        // المسار الخلفي
        p.color = 0x26F6DE8D
        c.drawArc(rect, 0f, 360f, false, p)

        // علامات الساعة
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40F6DE8D; strokeWidth = stroke * 0.18f }
        for (i in 0 until 60) {
            val a = 2 * PI * i / 60 - PI / 2
            val inner = r - stroke * (if (i % 5 == 0) 1.6f else 1.1f)
            val outer = r - stroke * 0.75f
            c.drawLine(
                (cx + inner * cos(a)).toFloat(), (cy + inner * sin(a)).toFloat(),
                (cx + outer * cos(a)).toFloat(), (cy + outer * sin(a)).toFloat(), tick
            )
        }

        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            // توهج
            val glow = Paint(p).apply {
                color = 0x66D9B44A
                strokeWidth = stroke * 1.8f
                maskFilter = BlurMaskFilter(stroke, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawArc(rect, -90f, sweep, false, glow)

            // التقدّم بتدرج ذهبي
            c.save()
            c.rotate(-90f, cx, cy)
            p.shader = SweepGradient(cx, cy,
                intArrayOf(0xFF9C7A22.toInt(), C_GOLD, C_GOLD_LIGHT, C_GOLD_LIGHT),
                floatArrayOf(0f, 0.5f * progress, progress.coerceAtLeast(0.01f), 1f))
            c.drawArc(rect, 0f, sweep, false, p)
            c.restore()

            // نقطة لامعة في الطرف
            val a = Math.toRadians((sweep - 90f).toDouble())
            val ex = (cx + r * cos(a)).toFloat()
            val ey = (cy + r * sin(a)).toFloat()
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            dot.shader = RadialGradient(ex, ey, stroke * 1.6f, intArrayOf(Color.WHITE, 0x00F6DE8D), null, Shader.TileMode.CLAMP)
            c.drawCircle(ex, ey, stroke * 1.6f, dot)
        }
    }

    // ---------------- المسجد ----------------
    fun mosquePath(w: Float, h: Float): Path {
        val p = Path()
        val base = h
        val cx = w / 2
        // القبة
        val domeR = h * 0.42f
        val domeBase = base - h * 0.30f
        p.moveTo(cx - domeR, domeBase)
        p.cubicTo(cx - domeR, domeBase - domeR * 1.25f, cx - domeR * 0.15f, domeBase - domeR * 1.35f, cx, domeBase - domeR * 1.6f)
        p.cubicTo(cx + domeR * 0.15f, domeBase - domeR * 1.35f, cx + domeR, domeBase - domeR * 1.25f, cx + domeR, domeBase)
        p.close()
        // جسم المسجد
        p.addRect(cx - domeR * 1.6f, domeBase, cx + domeR * 1.6f, base, Path.Direction.CW)
        // المنارتين
        for (side in intArrayOf(-1, 1)) {
            val mx = cx + side * domeR * 2.3f
            val mw = h * 0.07f
            p.addRect(mx - mw, base - h * 0.95f, mx + mw, base, Path.Direction.CW)
            p.addRect(mx - mw * 1.6f, base - h * 0.72f, mx + mw * 1.6f, base - h * 0.68f, Path.Direction.CW)
            p.moveTo(mx - mw * 1.2f, base - h * 0.95f)
            p.lineTo(mx, base - h * 1.12f)
            p.lineTo(mx + mw * 1.2f, base - h * 0.95f)
            p.close()
        }
        // أرضية
        p.addRect(0f, base - h * 0.04f, w, base, Path.Direction.CW)
        return p
    }

    // ---------------- أيقونات الصلوات ----------------
    fun drawPrayerIcon(c: Canvas, prayer: Prayer, s: Float, active: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val col = if (active) C_GOLD_LIGHT else 0xFFB9C9C1.toInt()
        p.color = col
        val cx = s / 2
        val horizon = s * 0.72f
        when (prayer) {
            Prayer.FAJR, Prayer.SUNRISE, Prayer.MAGHRIB -> {
                val sunY = if (prayer == Prayer.FAJR) horizon + s * 0.08f else horizon
                c.save(); c.clipRect(0f, 0f, s, horizon)
                c.drawCircle(cx, sunY, s * 0.22f, p)
                c.restore()
                p.strokeWidth = s * 0.06f; p.strokeCap = Paint.Cap.ROUND
                if (prayer != Prayer.FAJR) {
                    for (i in 0..4) {
                        val a = PI + PI * i / 4
                        c.drawLine((cx + s * 0.3f * cos(a)).toFloat(), (sunY + s * 0.3f * sin(a)).toFloat(),
                            (cx + s * 0.4f * cos(a)).toFloat(), (sunY + s * 0.4f * sin(a)).toFloat(), p)
                    }
                }
                c.drawLine(s * 0.1f, horizon, s * 0.9f, horizon, p)
                if (prayer == Prayer.MAGHRIB) {
                    c.drawLine(cx, s * 0.08f, cx, s * 0.2f, p)
                    c.drawLine(cx - s * 0.06f, s * 0.14f, cx, s * 0.2f, p)
                    c.drawLine(cx + s * 0.06f, s * 0.14f, cx, s * 0.2f, p)
                }
            }
            Prayer.DHUHR, Prayer.ASR -> {
                val sy = if (prayer == Prayer.DHUHR) s * 0.45f else s * 0.55f
                val sx = if (prayer == Prayer.DHUHR) cx else s * 0.42f
                c.drawCircle(sx, sy, s * 0.17f, p)
                p.strokeWidth = s * 0.06f; p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 8) {
                    val a = 2 * PI * i / 8
                    c.drawLine((sx + s * 0.25f * cos(a)).toFloat(), (sy + s * 0.25f * sin(a)).toFloat(),
                        (sx + s * 0.34f * cos(a)).toFloat(), (sy + s * 0.34f * sin(a)).toFloat(), p)
                }
                if (prayer == Prayer.ASR) {
                    p.color = (col and 0x00FFFFFF) or 0x99000000.toInt()
                    c.drawRoundRect(RectF(s * 0.45f, s * 0.62f, s * 0.92f, s * 0.82f), s * 0.1f, s * 0.1f, p)
                }
            }
            Prayer.ISHA -> {
                val path = Path()
                path.addCircle(s * 0.45f, s * 0.5f, s * 0.28f, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(s * 0.58f, s * 0.42f, s * 0.24f, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(path, p)
                c.drawCircle(s * 0.78f, s * 0.24f, s * 0.04f, p)
                c.drawCircle(s * 0.86f, s * 0.48f, s * 0.03f, p)
            }
        }
    }

    // ---------------- شخصيات: ذاهب للصلاة / يصلي ----------------

    private fun figurePaints(s: Float): Pair<Paint, Paint> {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, s, C_GOLD_LIGHT, C_GOLD, Shader.TileMode.CLAMP)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C_GOLD
            style = Paint.Style.STROKE
            strokeWidth = s * 0.055f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        return Pair(fill, line)
    }

    /** رجل بالثوب يمشي باتجاه المسجد (قبل الأذان) */
    fun drawWalker(c: Canvas, s: Float) {
        val (fill, line) = figurePaints(s)
        // المسجد الصغير
        val m = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99D9B44A.toInt() }
        c.drawRect(s * 0.66f, s * 0.64f, s * 0.95f, s * 0.90f, m)
        c.drawArc(RectF(s * 0.70f, s * 0.50f, s * 0.91f, s * 0.78f), 180f, 180f, true, m)
        c.drawRect(s * 0.955f - s * 0.03f, s * 0.40f, s * 0.955f, s * 0.90f, m)
        val door = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B231E.toInt() }
        c.drawRoundRect(RectF(s * 0.77f, s * 0.74f, s * 0.84f, s * 0.90f), s * 0.03f, s * 0.03f, door)
        // الأرض
        val ground = Paint(line).apply { alpha = 120; strokeWidth = s * 0.025f }
        c.drawLine(s * 0.04f, s * 0.92f, s * 0.97f, s * 0.92f, ground)
        // الرأس (بالشماغ)
        c.drawCircle(s * 0.34f, s * 0.22f, s * 0.075f, fill)
        val shemagh = Path()
        shemagh.moveTo(s * 0.26f, s * 0.20f)
        shemagh.quadTo(s * 0.34f, s * 0.10f, s * 0.42f, s * 0.20f)
        shemagh.lineTo(s * 0.44f, s * 0.36f)
        shemagh.lineTo(s * 0.24f, s * 0.36f)
        shemagh.close()
        c.drawPath(shemagh, fill)
        // الثوب، مايل لقدام
        val body = Path()
        body.moveTo(s * 0.27f, s * 0.33f)
        body.lineTo(s * 0.43f, s * 0.33f)
        body.lineTo(s * 0.48f, s * 0.82f)
        body.lineTo(s * 0.22f, s * 0.82f)
        body.close()
        c.drawPath(body, fill)
        // الرجلين (خطوة)
        c.drawLine(s * 0.42f, s * 0.80f, s * 0.52f, s * 0.91f, line)
        c.drawLine(s * 0.28f, s * 0.80f, s * 0.19f, s * 0.91f, line)
        // اليدين
        c.drawLine(s * 0.29f, s * 0.40f, s * 0.19f, s * 0.58f, line)
        c.drawLine(s * 0.41f, s * 0.40f, s * 0.50f, s * 0.56f, line)
    }

    /** رجل في الركوع على سجادة (بعد الأذان، انتظار الإقامة) */
    fun drawPraying(c: Canvas, s: Float) {
        val (fill, line) = figurePaints(s)
        // السجادة
        val mat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66D9B44A }
        val matRect = RectF(s * 0.04f, s * 0.87f, s * 0.96f, s * 0.95f)
        c.drawRoundRect(matRect, s * 0.02f, s * 0.02f, mat)
        val matEdge = Paint(line).apply { strokeWidth = s * 0.015f; alpha = 200 }
        c.drawRoundRect(matRect, s * 0.02f, s * 0.02f, matEdge)
        // الرجلين (ثوب)
        val legs = Path()
        legs.moveTo(s * 0.57f, s * 0.42f)
        legs.lineTo(s * 0.77f, s * 0.42f)
        legs.lineTo(s * 0.79f, s * 0.86f)
        legs.lineTo(s * 0.55f, s * 0.86f)
        legs.close()
        c.drawPath(legs, fill)
        // الظهر مستوي (ركوع)
        c.drawRoundRect(RectF(s * 0.26f, s * 0.33f, s * 0.77f, s * 0.50f), s * 0.08f, s * 0.08f, fill)
        // الرأس مع الشماغ المتدلي
        c.drawCircle(s * 0.17f, s * 0.42f, s * 0.085f, fill)
        val shemagh = Path()
        shemagh.moveTo(s * 0.10f, s * 0.38f)
        shemagh.quadTo(s * 0.18f, s * 0.30f, s * 0.28f, s * 0.36f)
        shemagh.lineTo(s * 0.27f, s * 0.62f)
        shemagh.lineTo(s * 0.13f, s * 0.58f)
        shemagh.close()
        c.drawPath(shemagh, fill)
        // اليد على الركبة
        val arm = Paint(line).apply { strokeWidth = s * 0.07f }
        c.drawLine(s * 0.36f, s * 0.46f, s * 0.58f, s * 0.68f, arm)
    }

    fun widgetFigure(sizePx: Int, iqamaPhase: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // هالة خلف الشخصية
        val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        halo.shader = RadialGradient(sizePx / 2f, sizePx / 2f, sizePx / 2f,
            intArrayOf(0x40D9B44A, 0x00D9B44A), null, Shader.TileMode.CLAMP)
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, halo)
        c.save()
        c.translate(sizePx * 0.03f, sizePx * 0.02f)
        val s = sizePx * 0.94f
        if (iqamaPhase) drawPraying(c, s) else drawWalker(c, s)
        c.restore()
        return bmp
    }

    /** خلفية الويدجت: سماء + نجوم + رسمة مسجد مدينتك باهتة + حواف دائرية */
    fun widgetBackground(wPx: Int, hPx: Int, landmark: Landmarks.Type, radiusPx: Float): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = wPx.toFloat(); val h = hPx.toFloat()
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radiusPx, radiusPx, Path.Direction.CW) }
        c.clipPath(clip)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.3f, h, 0xF2071814.toInt(), 0xF2104236.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, bg)
        // نجوم
        val rnd = Random(11)
        val star = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(40) {
            star.color = Color.argb(60 + rnd.nextInt(140), 255, 245, 215)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.6f, 0.6f + rnd.nextFloat() * (h / 140f), star)
        }
        // توهج ذهبي خفيف
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(w * 0.15f, h * 0.35f, h * 0.9f,
            intArrayOf(0x22D9B44A, 0x00D9B44A), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, glow)
        // رسمة المسجد في الأسفل باهتة
        val lh = h * 0.42f
        val lw = (lh * 2.1f).coerceAtMost(w * 0.75f)
        c.save()
        c.translate((w - lw) / 2f, h - lh)
        // نرسمها كظل ذهبي موحّد باهت (بدون ألوان) عشان ما تزحم النص
        c.saveLayerAlpha(0f, 0f, lw, lh, 40)
        Landmarks.draw(c, landmark, lw, lh)
        val tint = Paint().apply {
            color = C_GOLD
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        }
        c.drawRect(0f, 0f, lw, lh, tint)
        c.restore()
        c.restore()
        // تظليل سفلي عشان صف الأوقات يكون واضح
        val shade = Paint().apply {
            shader = LinearGradient(0f, h * 0.55f, 0f, h, 0x00071814, 0xCC071814.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.55f, w, h, shade)
        // إطار ذهبي رفيع
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = h / 110f
            shader = LinearGradient(0f, 0f, w, h, 0x99F6DE8D.toInt(), 0x33D9B44A, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(border.strokeWidth / 2, border.strokeWidth / 2, w - border.strokeWidth / 2, h - border.strokeWidth / 2),
            radiusPx, radiusPx, border)
        return bmp
    }

    // ---------------- صور الويدجت ----------------
    /** مسار «نقطة الوصول»: من البيت (يمين) إلى المسجد (يسار)، والنقطة الذهبية تمشي مع الوقت */
    fun journeyTrack(w: Int, h: Int, progress: Float, iqama: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(60), h.coerceAtLeast(16), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cy = h * 0.55f
        val r = h * 0.22f
        val endX = h * 0.75f            // المسجد على اليسار
        val startX = w - h * 0.35f      // البداية على اليمين
        val pr = progress.coerceIn(0f, 1f)
        val dotX = startX - (startX - endX) * pr
        // الطريق كامل (نقاط باهتة)
        p.color = 0x40F4EFE1; p.strokeWidth = h * 0.08f; p.strokeCap = Paint.Cap.ROUND
        var x = startX
        while (x > endX) { c.drawPoint(x, cy, p); x -= h * 0.28f }
        // المقطوع (ذهبي متدرج)
        p.shader = LinearGradient(startX, 0f, dotX, 0f, 0xFFB8902E.toInt(), 0xFFF3D98B.toInt(), Shader.TileMode.CLAMP)
        p.strokeWidth = h * 0.12f
        c.drawLine(startX, cy, dotX, cy, p); p.shader = null
        // نقطة البداية
        p.color = 0xFFB8902E.toInt(); c.drawCircle(startX, cy, r * 0.55f, p)
        // المسجد: قبة صغيرة + منارة
        p.color = if (pr >= 0.999f) 0xFFF3D98B.toInt() else 0xFFD9B44A.toInt()
        val base = cy + r * 1.1f
        val dw = h * 0.42f
        c.drawRect(endX - dw, base - h * 0.30f, endX + dw * 0.4f, base, p)
        c.drawCircle(endX - dw * 0.3f, base - h * 0.30f, dw * 0.55f, p)
        c.drawRect(endX + dw * 0.45f, base - h * 0.72f, endX + dw * 0.75f, base, p)
        // النقطة المتحركة مع توهج
        p.shader = RadialGradient(dotX, cy, r * 3f, intArrayOf(0x99FFE7A0.toInt(), 0x00FFE7A0), null, Shader.TileMode.CLAMP)
        c.drawCircle(dotX, cy, r * 3f, p); p.shader = null
        p.color = if (iqama) 0xFF7FE0A9.toInt() else 0xFFFFF3C4.toInt()
        c.drawCircle(dotX, cy, r, p)
        p.color = 0xFF0B231E.toInt(); c.drawCircle(dotX, cy, r * 0.4f, p)
        return bmp
    }

    fun widgetRing(sizePx: Int, progress: Float): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx * 0.07f
        drawRing(c, sizePx / 2f, sizePx / 2f, sizePx / 2f - stroke * 1.4f, stroke, progress)
        return bmp
    }

    fun widgetMoon(sizePx: Int, frac: Double): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawMoon(Canvas(bmp), sizePx / 2f, sizePx / 2f, sizePx * 0.3f, frac, 0.8f)
        return bmp
    }
}

// =================== Views متحركة ===================

/** سماء الليل: تدرج + نجوم تلمع + شهب */
/** صورة بحركة «كين برنز»: تكبير وتحريك بطيء جداً + تدرّج غامق تحت + عنوان ذهبي */
class KenBurnsFrame(ctx: Context, radiusDp: Float = 24f) : android.widget.FrameLayout(ctx) {
    val image = android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
    private val shadeV = View(ctx)
    val title = android.widget.TextView(ctx).apply {
        setTextColor(0xFFF3D98B.toInt()); textSize = 20f; gravity = android.view.Gravity.CENTER
        try { typeface = ctx.resources.getFont(R.font.amiri_quran) } catch (e: Exception) {}
        setShadowLayer(12f, 0f, 2f, 0xCC000000.toInt())
    }
    private var anim: android.animation.AnimatorSet? = null
    init {
        val d = ctx.resources.displayMetrics.density
        clipToOutline = true
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = radiusDp * d; setColor(0x22000000) }
        addView(image, LayoutParams(-1, -1))
        shadeV.background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()))
        addView(shadeV, LayoutParams(-1, -1))
        addView(title, LayoutParams(-1, -2, android.view.Gravity.BOTTOM).also { it.bottomMargin = (10 * d).toInt() })
        // إطار ذهبي رفيع
        foreground = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radiusDp * d; setStroke((1 * d).toInt().coerceAtLeast(1), 0x66E3C77B); setColor(0)
        }
    }
    fun showTitle(t: String) { title.text = t; title.visibility = if (t.isEmpty()) GONE else VISIBLE; shadeV.visibility = title.visibility }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val sx = android.animation.ObjectAnimator.ofFloat(image, "scaleX", 1f, 1.10f)
        val sy = android.animation.ObjectAnimator.ofFloat(image, "scaleY", 1f, 1.10f)
        val tx = android.animation.ObjectAnimator.ofFloat(image, "translationX", 0f, -12f * resources.displayMetrics.density)
        listOf(sx, sy, tx).forEach { it.repeatMode = android.animation.ValueAnimator.REVERSE; it.repeatCount = android.animation.ValueAnimator.INFINITE }
        anim = android.animation.AnimatorSet().apply {
            playTogether(sx, sy, tx); duration = 16000
            interpolator = android.view.animation.AccelerateDecelerateInterpolator(); start()
        }
    }
    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
}

class SkyView(ctx: Context, private val photoRes: Int = -1) : View(ctx) {
    private class Star(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

    private val stars = ArrayList<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private var t = 0f
    private val startNs = System.nanoTime()
    private var festive = false
    private val festiveColors = intArrayOf(0xFFF6DE8D.toInt(), 0xFF7FE0C9.toInt(), 0xFFFF9EB5.toInt(), 0xFFB3A6FF.toInt())
    private var meteorStart = -10f
    private var meteorX = 0f
    private var meteorY = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = (System.nanoTime() - startNs) / 1_000_000_000f
            if (t - meteorStart > 9f + Random.nextFloat() * 4f && width > 0) {
                meteorStart = t
                meteorX = width * (0.3f + Random.nextFloat() * 0.7f)
                meteorY = height * Random.nextFloat() * 0.35f
            }
            invalidate()
        }
    }

    private var bgBitmap: android.graphics.Bitmap? = null
    private val bgMatrix = android.graphics.Matrix()
    private val shade = Paint()
    /** حركة بسيطة للخلفية مع التمرير */
    var parallax = 0f
        set(v) { field = v; invalidate() }

    private var look = Look.Mode.NIGHT
    /** نعيد رسم الخلفية لو تغيّر المظهر (مثلاً جا المغرب في الوضع التلقائي) */
    fun reloadIfChanged() { if (Look.effective(context) != look && width > 0) onSizeChanged(width, height, width, height) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        look = Look.effective(context)
        bgBitmap = null
        // صورة الموسم، وفي الأيام العادية: الحرم ليلاً (نهاري: صورة الحرم وقت النهار، داكن: بدون صورة)
        val res = when {
            photoRes >= 0 -> photoRes
            look == Look.Mode.DARK -> 0
            else -> Pictures.seasonBg(context, Season.current()).takeIf { it != 0 }
                ?: if (look == Look.Mode.DAY) Pictures.landmark(context, Landmarks.Type.HARAM).takeIf { it != 0 } ?: Pictures.splash(context)
                   else Pictures.splash(context)
        }
        if (res != 0 && w > 0 && h > 0) try {
            val opts = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
            val b = android.graphics.BitmapFactory.decodeResource(resources, res, opts)
            if (b != null) {
                bgBitmap = b
                val scale = maxOf(w / b.width.toFloat(), (h * 1.15f) / b.height.toFloat())
                bgMatrix.setScale(scale, scale)
                bgMatrix.postTranslate((w - b.width * scale) / 2f, 0f)
                shade.shader = if (look == Look.Mode.DAY)
                    LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x10000000, 0x55000000, 0xAA06201B.toInt()), null, Shader.TileMode.CLAMP)
                else LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x66000000, 0xAA000000.toInt(), 0xDD000000.toInt()), null, Shader.TileMode.CLAMP)
            }
        } catch (e: OutOfMemoryError) { bgBitmap = null }
        stars.clear()
        val rnd = Random(7)
        val d = resources.displayMetrics.density
        repeat(90) {
            stars.add(Star(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.85f,
                (0.6f + rnd.nextFloat() * 1.4f) * d, 0.6f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f))
        }
        val season = Season.current()
        festive = season.festive
        bgPaint.shader = when (look) {
            Look.Mode.DARK -> LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF000000.toInt(), 0xFF06100D.toInt(), Shader.TileMode.CLAMP)
            Look.Mode.DAY -> LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF3C88B2.toInt(), 0xFFE7C98E.toInt(), Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, 0f, 0f, h.toFloat(), season.skyTop, season.skyBottom, Shader.TileMode.CLAMP)
        }
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgBitmap?.let {
            c.save(); c.translate(0f, -parallax.coerceIn(0f, height * 0.12f))
            // تكبير بطيء جداً يعطي إحساس بالحياة
            val z = 1f + 0.05f * ((sin(t * 0.08f) + 1f) / 2f)
            c.scale(z, z, width / 2f, height / 3f)
            c.drawBitmap(it, bgMatrix, null)
            c.restore()
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        }
        if (look == Look.Mode.DAY) {
            // شمس ناعمة بدل النجوم
            val d = resources.displayMetrics.density
            val cx = width * 0.82f; val cy = height * 0.08f
            val pulse = 1f + 0.06f * sin(t * 0.6f)
            paint.shader = android.graphics.RadialGradient(cx, cy, 120f * d * pulse,
                intArrayOf(0x88FFF3C4.toInt(), 0x33FFE7A0, 0x00FFE7A0), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, 120f * d * pulse, paint); paint.shader = null
            return
        }
        val starAlpha = if (look == Look.Mode.DARK) 0.45f else 1f
        for ((i, s) in stars.withIndex()) {
            val a = 0.25f + 0.75f * ((sin(t * s.speed + s.phase) + 1f) / 2f)
            if (festive) {
                // أيام العيد: نجوم ملونة كأنها زينة
                val col = festiveColors[i % festiveColors.size]
                paint.color = Color.argb((a * 230).toInt(), Color.red(col), Color.green(col), Color.blue(col))
                c.drawCircle(s.x, s.y, s.r * 1.4f, paint)
            } else {
                paint.color = Color.argb((a * 220 * starAlpha).toInt(), 255, 245, 215)
                c.drawCircle(s.x, s.y, s.r, paint)
            }
        }
        // شهاب
        val mt = t - meteorStart
        if (mt in 0f..1.2f) {
            val d = resources.displayMetrics.density
            val prog = mt / 1.2f
            val len = 90f * d
            val hx = meteorX - prog * 220f * d
            val hy = meteorY + prog * 110f * d
            val fade = (1f - prog)
            paint.shader = LinearGradient(hx, hy, hx + len, hy - len / 2,
                Color.argb((fade * 255).toInt(), 255, 248, 225), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            paint.strokeWidth = 2f * d
            paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(hx, hy, hx + len, hy - len / 2, paint)
            paint.shader = null
        }
    }
}

/** القمر المرسوم مع نبض توهج خفيف */
class MoonView(ctx: Context) : View(ctx) {
    var frac: Double = 0.0
        set(v) { field = v; invalidate() }
    private var pulse = 1f
    private val anim = ValueAnimator.ofFloat(0.75f, 1.15f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        Art.drawMoon(c, width / 2f, height / 2f, s * 0.3f, frac, pulse)
    }
}

/** حلقة العدّاد: تتعبّى من الصلاة السابقة للقادمة، مع حركة عند الفتح */
class RingView(ctx: Context) : View(ctx) {
    private var target = 0f
    private var shown = 0f
    private var intro: ValueAnimator? = null
    private var glow = 1f
    /** لما يقرب الأذان أو الإقامة (أقل من ١٠ دقائق) النبض يصير أسرع */
    var urgent = false
        set(v) { if (field != v) { field = v; breath.duration = if (v) 900 else 2600 } }
    private val breath = ValueAnimator.ofFloat(0.55f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { glow = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); breath.start() }
    override fun onDetachedFromWindow() { breath.cancel(); super.onDetachedFromWindow() }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // عشان التوهج BlurMaskFilter

    fun setProgress(p: Float, animate: Boolean) {
        target = p.coerceIn(0f, 1f)
        if (animate) {
            intro?.cancel()
            intro = ValueAnimator.ofFloat(0f, target).apply {
                duration = 1600
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (intro?.isRunning != true && kotlin.math.abs(target - shown) > 0.0005f) {
            // حركة ناعمة بدل القفز
            intro = ValueAnimator.ofFloat(shown, target).apply {
                duration = 900
                interpolator = DecelerateInterpolator()
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val stroke = s * 0.045f
        // هالة تتنفس خلف الحلقة
        val cx = width / 2f; val cy = height / 2f
        val haloR = s / 2f
        val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        val a = ((if (urgent) 90 else 55) * glow).toInt()
        halo.shader = RadialGradient(cx, cy, haloR, intArrayOf(0x00D9B44A, Color.argb(a, 217, 180, 74), 0x00D9B44A),
            floatArrayOf(0.6f, 0.82f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, haloR, halo)
        Art.drawRing(c, cx, cy, s / 2f - stroke * 1.6f, stroke, shown)
    }
}

/** زر الرجوع اللي فوق في كل شاشة */
object Nav {
    fun back(a: android.app.Activity): View {
        val d = a.resources.displayMetrics.density
        val row = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val b = android.widget.TextView(a).apply {
            text = "→  رجوع"
            textSize = 16f
            setTextColor(C_GOLD)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * d; setColor(0x26D9B44A); setStroke((1 * d).toInt(), 0x66D9B44A)
            }
            setOnClickListener { a.finish() }
        }
        Press.apply(b)
        row.addView(b)
        return row
    }
}

/** تأثير الضغط: الزر يصغر شوي ويرجع بنعومة */
object Press {
    fun apply(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
            }
            false
        }
    }
}

/** شريط الوقت: من (الصلاة السابقة أو الأذان) إلى (الأذان أو الإقامة)، مع نقطة مضيئة تتحرك */
class TimelineView(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var startLabel = ""; var startTime = ""
    var endLabel = ""; var endTime = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val pad = 10 * d
        val y = 12 * d
        val w = width.toFloat()
        val right = w - pad; val left = pad
        // المسار
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 6 * d
        p.shader = null; p.color = 0x26F6DE8D
        c.drawLine(left, y, right, y, p)
        // الجزء المنقضي (من اليمين لليسار)
        val x = right - (right - left) * progress
        p.shader = LinearGradient(right, 0f, x, 0f, 0xFF9C7A22.toInt(), C_GOLD_LIGHT, Shader.TileMode.CLAMP)
        if (progress > 0.005f) c.drawLine(right, y, x, y, p)
        p.shader = null
        // النقطة المضيئة
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(x, y, 16 * d, intArrayOf(0xB3F6DE8D.toInt(), 0x00F6DE8D), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 16 * d, p)
        p.shader = null; p.color = Color.WHITE
        c.drawCircle(x, y, 8 * d, p)
        // التسميات
        p.textSize = 11 * d; p.color = 0xFFA9BDB4.toInt()
        p.textAlign = Paint.Align.RIGHT
        c.drawText(startLabel, w, y + 26 * d, p)
        p.textAlign = Paint.Align.LEFT
        c.drawText(endLabel, 0f, y + 26 * d, p)
        p.textSize = 13 * d; p.color = C_TEXT; p.isFakeBoldText = true
        p.textAlign = Paint.Align.RIGHT
        c.drawText(startTime, w, y + 42 * d, p)
        p.textAlign = Paint.Align.LEFT
        c.drawText(endTime, 0f, y + 42 * d, p)
        p.isFakeBoldText = false
    }
}

/** رسمة المسجد الذهبية */
class MosqueView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, h, C_GOLD_LIGHT, 0xFF8C6B1F.toInt(), Shader.TileMode.CLAMP)
        paint.alpha = 210
        c.drawPath(Art.mosquePath(w, h), paint)
    }
}

/** أيقونة الصلاة */
class PrayerIconView(ctx: Context, val prayer: Prayer) : View(ctx) {
    var active = false
        set(v) { field = v; invalidate() }
    private val img: android.graphics.drawable.Drawable? =
        Pictures.prayerIcon(ctx, prayer).takeIf { it != 0 }?.let { ctx.getDrawable(it) }
    override fun onDraw(c: Canvas) {
        val d = img
        if (d != null) {
            d.setBounds(0, 0, width, height)
            d.alpha = if (active) 255 else 150
            d.draw(c)
        } else Art.drawPrayerIcon(c, prayer, min(width, height).toFloat(), active)
    }
}
