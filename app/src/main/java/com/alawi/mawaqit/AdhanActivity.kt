package com.alawi.mawaqit

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * شاشة الأذان الكاملة: تطلع فوق قفل الشاشة بألوان متحركة،
 * عشان تنتبه للأذان حتى لو الجوال صامت.
 */
class AdhanActivity : Activity() {

    private var colorAnim: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val p = Prayer.byKey(intent.getStringExtra(Scheduler.EXTRA_PRAYER)) ?: Prayer.DHUHR
        val t = intent.getLongExtra(Scheduler.EXTRA_TIME, System.currentTimeMillis())
        val prefs = Prefs(this)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val frame = FrameLayout(this)
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF061512.toInt(), 0xFF0E3A30.toInt()))
        frame.background = bg
        frame.addView(SkyView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }
        fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val fig = ImageView(this).apply {
            val r = Pictures.walker(this@AdhanActivity)
            setImageResource(r)
            scaleType = if (r == R.drawable.fig_walker) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { setColor(0x22000000); cornerRadius = dp(28).toFloat() }
        }
        col.addView(fig, LinearLayout.LayoutParams(dp(220), dp(220)).also { it.bottomMargin = dp(12) })
        col.addView(tv("حان الآن موعد أذان", 22f, C_TEXT))
        val name = tv(p.displayName(t), 56f, C_GOLD, true).apply {
            setShadowLayer(dp(18).toFloat(), 0f, 0f, 0xAAD9B44A.toInt())
        }
        col.addView(name)
        col.addView(tv("${Fmt.time(t)} • ${prefs.city}", 17f, C_TEXT))
        col.addView(tv("الإقامة ${Fmt.time(t + prefs.iqamaMin(p) * 60_000L)}", 16f, C_GOLD_LIGHT))

        val lmType = Landmarks.detect(prefs.lat, prefs.lng)
        val lmImg = Pictures.landmark(this, lmType)
        if (lmImg == 0) {
            val lm = LandmarkView(this).apply { type = lmType }
            col.addView(lm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)).also { it.topMargin = dp(18) })
        } else {
            val lm = ImageView(this).apply {
                setImageResource(lmImg)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply { setColor(0x22000000); cornerRadius = dp(22).toFloat() }
            }
            col.addView(lm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)).also { it.topMargin = dp(18) })
        }

        val stop = Button(this).apply {
            text = "إيقاف الأذان"
            textSize = 18f
            setTextColor(0xFF0B231E.toInt())
            background = GradientDrawable().apply { setColor(C_GOLD); cornerRadius = dp(30).toFloat() }
            setOnClickListener { AdhanService.stop(this@AdhanActivity); finish() }
        }
        col.addView(stop, LinearLayout.LayoutParams(dp(240), dp(58)).also { it.topMargin = dp(24) })
        col.addView(tv("اضغط في أي مكان للإخفاء (الأذان يكمل)", 12f, 0xFFA9BDB4.toInt()))
        frame.setOnClickListener { finish() }

        frame.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)

        // ألوان متحركة: أخضر ← ذهبي ← أزرق ليلي
        colorAnim = ValueAnimator.ofObject(ArgbEvaluator(),
            0xFF0E3A30.toInt(), 0xFF6B5418.toInt(), 0xFF0F2A4A.toInt(), 0xFF0E3A30.toInt()).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { bg.colors = intArrayOf(0xFF061512.toInt(), it.animatedValue as Int) }
            start()
        }
        // نبض اسم الصلاة
        name.animate().scaleX(1.08f).scaleY(1.08f).setDuration(900).setInterpolator(DecelerateInterpolator()).withEndAction {
            name.animate().scaleX(1f).scaleY(1f).setDuration(900).start()
        }.start()
        fig.alpha = 0f
        fig.translationY = dp(40).toFloat()
        fig.animate().alpha(1f).translationY(0f).setDuration(900).start()
    }

    override fun onDestroy() {
        colorAnim?.cancel()
        super.onDestroy()
    }
}
