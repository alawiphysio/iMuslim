package com.alawi.mawaqit

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object QiblaMath {
    const val KAABA_LAT = 21.4225
    const val KAABA_LNG = 39.8262

    /** اتجاه القبلة بالدرجات من الشمال الحقيقي */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun distanceKm(lat: Double, lng: Double): Int {
        val r = FloatArray(1)
        Location.distanceBetween(lat, lng, KAABA_LAT, KAABA_LNG, r)
        return (r[0] / 1000f).toInt()
    }
}

/** البوصلة: القرص يدور مع الجوال، والكعبة على اتجاه القبلة */
class CompassView(ctx: Context) : View(ctx) {
    var azimuth = 0f          // اتجاه الجوال من الشمال الحقيقي
        set(v) { field = v; invalidate() }
    var qibla = 0f
    var aligned = false
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f * 0.86f
        val d = resources.displayMetrics.density

        // هالة
        p.shader = RadialGradient(cx, cy, r * 1.15f,
            intArrayOf(if (aligned) 0x6634D399 else 0x40D9B44A, 0x00000000), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.15f, p)
        p.shader = null

        // القرص
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, 0xFF16463A.toInt(), 0xFF071814.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * d
        p.color = if (aligned) 0xFF34D399.toInt() else C_GOLD
        c.drawCircle(cx, cy, r, p)

        c.save()
        c.rotate(-azimuth, cx, cy)
        // علامات الدرجات
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0 - 90)
            val len = if (i % 6 == 0) 0.1f else 0.05f
            p.strokeWidth = (if (i % 6 == 0) 2f else 1f) * d
            p.color = if (i % 6 == 0) 0xCCF6DE8D.toInt() else 0x66F6DE8D
            c.drawLine((cx + r * 0.95f * cos(a)).toFloat(), (cy + r * 0.95f * sin(a)).toFloat(),
                (cx + r * (0.95f - len) * cos(a)).toFloat(), (cy + r * (0.95f - len) * sin(a)).toFloat(), p)
        }
        // الجهات
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = 20f * d
        val dirs = arrayOf("ش" to 0f, "ق" to 90f, "ج" to 180f, "غ" to 270f)
        for ((t, deg) in dirs) {
            val a = Math.toRadians(deg - 90.0)
            p.color = if (deg == 0f) 0xFFFF6B6B.toInt() else C_TEXT
            c.drawText(t, (cx + r * 0.72f * cos(a)).toFloat(), (cy + r * 0.72f * sin(a)).toFloat() + 7 * d, p)
        }
        // سهم القبلة
        c.save()
        c.rotate(qibla, cx, cy)
        val needle = Path().apply {
            moveTo(cx, cy - r * 0.62f)
            lineTo(cx - r * 0.06f, cy)
            lineTo(cx, cy + r * 0.12f)
            lineTo(cx + r * 0.06f, cy)
            close()
        }
        p.color = if (aligned) 0xFF34D399.toInt() else C_GOLD
        c.drawPath(needle, p)
        // الكعبة على طرف السهم
        val ks = r * 0.16f
        val kx = cx; val ky = cy - r * 0.78f
        p.color = 0xFF0C0C0C.toInt()
        c.drawRect(kx - ks / 2, ky - ks / 2, kx + ks / 2, ky + ks / 2, p)
        p.color = C_GOLD
        c.drawRect(kx - ks / 2, ky - ks * 0.22f, kx + ks / 2, ky - ks * 0.1f, p)
        c.drawRect(kx + ks * 0.1f, ky, kx + ks * 0.28f, ky + ks / 2, p)
        c.restore()
        c.restore()

        // المركز
        p.color = C_GOLD_LIGHT
        c.drawCircle(cx, cy, 7 * d, p)
        // مؤشر ثابت فوق (اتجاه الجوال)
        val tri = Path().apply {
            moveTo(cx, cy - r - 4 * d); lineTo(cx - 10 * d, cy - r - 22 * d); lineTo(cx + 10 * d, cy - r - 22 * d); close()
        }
        p.color = if (aligned) 0xFF34D399.toInt() else C_TEXT
        c.drawPath(tri, p)
    }
}

class QiblaActivity : Activity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private lateinit var compass: CompassView
    private lateinit var info: TextView
    private lateinit var hint: TextView
    private var smooth = Float.NaN
    private var declination = 0f
    private var qibla = 0f
    private var wasAligned = false
    private val rot = FloatArray(9)
    private val orient = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        qibla = QiblaMath.bearing(prefs.lat, prefs.lng).toFloat()
        declination = GeomagneticField(prefs.lat.toFloat(), prefs.lng.toFloat(), 0f, System.currentTimeMillis()).declination

        val frame = FrameLayout(this)
        frame.addView(SkyView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(36), dp(20), dp(24))
        }
        fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        col.addView(tv("🕋 اتجاه القبلة", 28f, C_GOLD, true))
        col.addView(tv("📍 ${prefs.city} • تبعد عن مكة ${Fmt.num(QiblaMath.distanceKm(prefs.lat, prefs.lng))} كم", 14f, C_TEXT))
        compass = CompassView(this).apply { qibla = this@QiblaActivity.qibla }
        col.addView(compass, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)).also { it.topMargin = dp(20) })
        info = tv("", 22f, C_TEXT, true)
        col.addView(info)
        hint = tv("ضع الجوال مسطّح، ولفّ لين يصير السهم الذهبي فوق", 13f, 0xFFA9BDB4.toInt())
        col.addView(hint)
        col.addView(tv("اتجاه القبلة ${Fmt.num(qibla.toInt())}° من الشمال الحقيقي", 12f, 0xFFA9BDB4.toInt()).apply { setPadding(0, dp(10), 0, 0) })
        frame.addView(col)
        setContentView(frame)

        sm = getSystemService(SensorManager::class.java)
    }

    override fun onResume() {
        super.onResume()
        val s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s == null) { info.text = "جوالك ما فيه حساس بوصلة" ; return }
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        val az = ((Math.toDegrees(orient[0].toDouble()).toFloat() + declination) + 360f) % 360f
        // تنعيم الحركة
        smooth = if (smooth.isNaN()) az else {
            var diff = az - smooth
            if (diff > 180) diff -= 360
            if (diff < -180) diff += 360
            (smooth + diff * 0.15f + 360f) % 360f
        }
        compass.azimuth = smooth
        var off = qibla - smooth
        if (off > 180) off -= 360
        if (off < -180) off += 360
        val aligned = abs(off) < 3f
        compass.aligned = aligned
        info.text = when {
            aligned -> "✅ أنت على اتجاه القبلة"
            off > 0 -> "لف يمين ${Fmt.num(abs(off).toInt())}°"
            else -> "لف يسار ${Fmt.num(abs(off).toInt())}°"
        }
        info.setTextColor(if (aligned) 0xFF34D399.toInt() else C_TEXT)
        if (aligned && !wasAligned) vibrate()
        wasAligned = aligned
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        hint.text = if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            "⚠️ البوصلة تحتاج معايرة: حرّك الجوال على شكل رقم 8 في الهواء"
        else "ضع الجوال مسطّح، ولفّ لين يصير السهم الذهبي فوق"
    }

    private fun vibrate() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
        } catch (e: Exception) { }
    }
}
