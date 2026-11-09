package com.alawi.mawaqit

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView

object Tasbih {
    val PHRASES = listOf(
        "سُبْحَانَ اللَّهِ",
        "الْحَمْدُ لِلَّهِ",
        "اللَّهُ أَكْبَرُ",
        "لَا إِلَهَ إِلَّا اللَّهُ",
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
        "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"
    )
    val TARGETS = listOf(33, 99, 100, 1000)

    private fun today() = Tracker.dayKey(System.currentTimeMillis())

    /** يزيد العدّاد ويرجع true لو وصل للهدف */
    fun add(ctx: Context): Boolean {
        val p = Prefs(ctx)
        if (p.tasbihDayKey != today()) { p.tasbihDayKey = today(); p.tasbihToday = 0 }
        p.tasbihCount = p.tasbihCount + 1
        p.tasbihToday = p.tasbihToday + 1
        p.tasbihTotal = p.tasbihTotal + 1
        return p.tasbihCount % p.tasbihTarget == 0
    }

    fun todayCount(ctx: Context): Int {
        val p = Prefs(ctx)
        return if (p.tasbihDayKey == today()) p.tasbihToday else 0
    }

    fun vibrate(ctx: Context, strong: Boolean) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(if (strong) VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 200), -1)
                else VibrationEffect.createOneShot(18, 120))
        } catch (e: Exception) { }
    }
}

class TasbihActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ring: RingView
    private lateinit var countTv: TextView
    private lateinit var phraseTv: TextView
    private lateinit var statsTv: TextView
    private val phraseChips = ArrayList<TextView>()
    private val targetChips = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun chip(s: String, onClick: () -> Unit) = tv(s, 14f, C_TEXT, true).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { onClick() }
        }

        val frame = FrameLayout(this)
        frame.addView(SkyView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(30), dp(16), dp(20))
        }
        col.addView(Nav.back(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(tv("📿 المسبحة", 28f, C_GOLD, true))

        // اختيار الذكر
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(4)) }
        Tasbih.PHRASES.forEachIndexed { i, s ->
            val c = chip(s) { prefs.tasbihPhrase = i; prefs.tasbihCount = 0; refresh() }
            phraseChips.add(c)
            row.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(8) })
        }
        hs.addView(row)
        col.addView(hs)

        // اختيار العدد
        val trow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        Tasbih.TARGETS.forEach { t ->
            val c = chip(Fmt.num(t)) { prefs.tasbihTarget = t; prefs.tasbihCount = 0; refresh() }
            targetChips.add(c)
            trow.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(8) })
        }
        col.addView(trow)

        phraseTv = tv("", 26f, C_GOLD_LIGHT, true).apply { setPadding(0, dp(18), 0, 0) }
        col.addView(phraseTv)

        // الحلقة الكبيرة: اضغط في أي مكان فيها
        val box = FrameLayout(this)
        ring = RingView(this)
        box.addView(ring, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        countTv = tv("0", 72f, C_TEXT, true).apply { setShadowLayer(dp(14).toFloat(), 0f, 0f, 0x88F6DE8D.toInt()) }
        box.addView(countTv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        box.setOnClickListener { tap() }
        col.addView(box, LinearLayout.LayoutParams(dp(300), dp(300)).also { it.topMargin = dp(10) })

        statsTv = tv("", 14f, 0xFFA9BDB4.toInt())
        col.addView(statsTv)
        col.addView(tv("↺ تصفير", 15f, C_GOLD, true).apply {
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setStroke(dp(1), C_GOLD) }
            setOnClickListener { prefs.tasbihCount = 0; refresh() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })

        frame.addView(col)
        setContentView(frame)
        refresh()
    }

    private fun tap() {
        val done = Tasbih.add(this)
        countTv.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        Tasbih.vibrate(this, done)
        countTv.animate().scaleX(1.12f).scaleY(1.12f).setDuration(70).withEndAction {
            countTv.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }.start()
        refresh()
        TasbihWidget.updateAll(this)
    }

    private fun refresh() {
        val target = prefs.tasbihTarget
        val c = prefs.tasbihCount
        phraseTv.text = Tasbih.PHRASES[prefs.tasbihPhrase.coerceIn(0, Tasbih.PHRASES.size - 1)]
        countTv.text = Fmt.num(c)
        val inRound = c % target
        ring.setProgress(if (c > 0 && inRound == 0) 1f else inRound / target.toFloat(), false)
        statsTv.text = "الدورة ${Fmt.num(c / target + 1)} • اليوم ${Fmt.num(Tasbih.todayCount(this))} • المجموع ${Fmt.num(prefs.tasbihTotal.toInt())}"
        phraseChips.forEachIndexed { i, v ->
            val sel = i == prefs.tasbihPhrase
            v.background = GradientDrawable().apply { cornerRadius = 40f; setColor(if (sel) C_GOLD else 0x26FFFFFF); }
            v.setTextColor(if (sel) 0xFF0B231E.toInt() else C_TEXT)
        }
        targetChips.forEachIndexed { i, v ->
            val sel = Tasbih.TARGETS[i] == target
            v.background = GradientDrawable().apply { cornerRadius = 40f; setColor(if (sel) C_GOLD else 0x26FFFFFF) }
            v.setTextColor(if (sel) 0xFF0B231E.toInt() else C_TEXT)
        }
    }
}

/** ويدجت المسبحة: اضغط عليها تسبّح بدون ما تفتح التطبيق */
class TasbihWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TAP) {
            val done = Tasbih.add(ctx)
            Tasbih.vibrate(ctx, done)
            updateAll(ctx)
            return
        }
        super.onReceive(ctx, intent)
    }

    companion object {
        const val ACTION_TAP = "com.alawi.mawaqit.TASBIH_TAP"

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TasbihWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val p = Prefs(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_tasbih)
            rv.setTextViewText(R.id.t_phrase, Tasbih.PHRASES[p.tasbihPhrase.coerceIn(0, Tasbih.PHRASES.size - 1)])
            rv.setTextViewText(R.id.t_count, Fmt.num(p.tasbihCount))
            rv.setTextViewText(R.id.t_info, "من ${Fmt.num(p.tasbihTarget)} • اليوم ${Fmt.num(Tasbih.todayCount(ctx))}")
            val target = p.tasbihTarget
            val inRound = p.tasbihCount % target
            val prog = if (p.tasbihCount > 0 && inRound == 0) 1f else inRound / target.toFloat()
            val size = (110 * ctx.resources.displayMetrics.density.coerceAtMost(2.5f)).toInt()
            rv.setImageViewBitmap(R.id.t_ring, Art.widgetRing(size, prog))
            val tap = PendingIntent.getBroadcast(ctx, 500, Intent(ctx, TasbihWidget::class.java).setAction(ACTION_TAP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.t_root, tap)
            val open = PendingIntent.getActivity(ctx, 501, Intent(ctx, TasbihActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.t_open, open)
            mgr.updateAppWidget(ids, rv)
        }
    }
}
