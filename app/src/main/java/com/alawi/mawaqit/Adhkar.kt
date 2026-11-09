package com.alawi.mawaqit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

// =================== البيانات ===================

data class Dhikr(val text: String, val count: Int, val audio: String)
data class DhikrCategory(val id: Int, val name: String, val items: List<Dhikr>)

/** حصن المسلم (سعيد بن علي بن وهف القحطاني) — النص والتسجيلات بقراءة الأخ حمد الدريهم */
object Adhkar {
    const val MORNING_EVENING = 1
    const val SLEEP = 2
    const val WAKE = 3
    const val MOSQUE_GO = 10
    const val ADHAN = 13
    const val AFTER_PRAYER = 27

    @Volatile private var cache: List<DhikrCategory>? = null

    fun all(ctx: Context): List<DhikrCategory> {
        cache?.let { return it }
        val json = ctx.assets.open("adhkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(json)
        val list = ArrayList<DhikrCategory>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val its = c.getJSONArray("items")
            val items = ArrayList<Dhikr>(its.length())
            for (j in 0 until its.length()) {
                val o = its.getJSONObject(j)
                items.add(Dhikr(o.getString("t"), o.optInt("n", 1).coerceAtLeast(1), o.optString("a", "")))
            }
            list.add(DhikrCategory(c.getInt("id"), c.getString("name"), items))
        }
        cache = list
        return list
    }

    fun byId(ctx: Context, id: Int) = all(ctx).firstOrNull { it.id == id }

    fun open(ctx: Context, id: Int) {
        ctx.startActivity(Intent(ctx, AdhkarReaderActivity::class.java)
            .putExtra("cat", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// =================== أدوات واجهة مشتركة ===================

private object Ui {
    const val GOLD = 0xFFD9B44A.toInt()
    const val TEXT = 0xFFF4EFE1.toInt()
    const val MUTED = 0xFFA9BDB4.toInt()

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(ctx: Context, color: Int, r: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = r * ctx.resources.displayMetrics.density
        if (stroke != null) setStroke(dp(ctx, 1), stroke)
    }

    fun tv(ctx: Context, s: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun screen(a: Activity): Pair<FrameLayout, LinearLayout> {
        val frame = FrameLayout(a)
        frame.addView(SkyView(a), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; tag = "scroll" }
        frame.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val w = a.resources.configuration.screenWidthDp
            val side = if (w > 600) ((w - 600) / 2).coerceAtLeast(16) else 16
            setPadding(dp(a, side), dp(a, 20), dp(a, side), dp(a, 40))
        }
        scroll.addView(root)
        return Pair(frame, root)
    }
}

// =================== قائمة حصن المسلم ===================

class AdhkarActivity : Activity() {
    private lateinit var listBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (frame, root) = Ui.screen(this)
        val d = { v: Int -> Ui.dp(this, v) }

        root.addView(Nav.back(this))
        root.addView(Ui.tv(this, "🏰 حصن المسلم", 30f, Ui.GOLD, true).apply { gravity = Gravity.CENTER })
        root.addView(Ui.tv(this, "من أذكار الكتاب والسنة • سعيد بن علي بن وهف القحطاني", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        root.addView(Ui.tv(this, "بصوت: حمد الدريهم", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER })

        // اختصارات
        val quick = listOf(
            "☀️ الصباح والمساء" to Adhkar.MORNING_EVENING,
            "🕌 الأذان وما بعده" to Adhkar.ADHAN,
            "🤲 بعد السلام" to Adhkar.AFTER_PRAYER,
            "🌙 النوم" to Adhkar.SLEEP,
            "🌅 الاستيقاظ" to Adhkar.WAKE,
            "🚶 الذهاب للمسجد" to Adhkar.MOSQUE_GO
        )
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(14), 0, d(8)) }
        for ((label, id) in quick) {
            val chip = Ui.tv(this, label, 15f, 0xFF0B231E.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(d(16), d(12), d(16), d(12))
                background = Ui.rounded(this@AdhkarActivity, Ui.GOLD, 22f)
                setOnClickListener { Adhkar.open(this@AdhkarActivity, id) }
            }
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = d(8) })
        }
        hs.addView(row)
        root.addView(hs)

        // البحث
        val search = EditText(this).apply {
            hint = "🔍 ابحث في الأذكار…"
            setHintTextColor(Ui.MUTED)
            setTextColor(Ui.TEXT)
            textSize = 16f
            background = Ui.rounded(this@AdhkarActivity, 0x33000000, 16f, 0x55D9B44A)
            setPadding(d(14), d(12), d(14), d(12))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = d(8) })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)
        fill("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { fill(s?.toString()?.trim() ?: "") }
        })
        setContentView(frame)
    }

    /** نشيل التشكيل عشان البحث يلقى الكلمة بأي شكل */
    private fun plain(s: String) = s.replace(Regex("[ً-ْٰـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')

    private fun fill(q: String) {
        listBox.removeAllViews()
        val d = { v: Int -> Ui.dp(this, v) }
        val pq = plain(q)
        Adhkar.all(this).forEachIndexed { idx, c ->
            if (pq.isNotEmpty() && !plain(c.name).contains(pq) && c.items.none { plain(it.text).contains(pq) }) return@forEachIndexed
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(14), d(14), d(14), d(14))
                background = Ui.rounded(this@AdhkarActivity, 0xCC0F2E27.toInt(), 16f, 0x26D9B44A)
                setOnClickListener { Adhkar.open(this@AdhkarActivity, c.id) }
            }
            val num = Ui.tv(this, Fmt.num(c.id), 13f, Ui.GOLD, true).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(this@AdhkarActivity, 0x26D9B44A, 14f)
            }
            r.addView(num, LinearLayout.LayoutParams(d(36), d(36)).also { it.marginEnd = d(12) })
            r.addView(Ui.tv(this, c.name, 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(Ui.tv(this, "${Fmt.num(c.items.size)} ذكر", 12f, Ui.MUTED))
            listBox.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = d(8) })
            if (pq.isEmpty() && idx < 12) {
                r.alpha = 0f; r.translationY = d(20).toFloat()
                r.animate().alpha(1f).translationY(0f).setStartDelay(40L * idx).setDuration(400).start()
            }
        }
    }
}

// =================== قارئ الأذكار مع العدّاد والصوت ===================

class AdhkarReaderActivity : Activity() {
    private var player: MediaPlayer? = null
    private var playAll = false
    private var playingIndex = -1
    private lateinit var cat: DhikrCategory
    private val remaining = ArrayList<Int>()
    private val cards = ArrayList<LinearLayout>()
    private val counters = ArrayList<TextView>()
    private val playBtns = ArrayList<TextView>()
    private lateinit var progress: ProgressBar
    private lateinit var progressTv: TextView
    private lateinit var playAllBtn: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("cat", Adhkar.MORNING_EVENING)
        val many = intent.getIntArrayExtra("cats")
        cat = if (many != null && many.isNotEmpty())
            DhikrCategory(id, intent.getStringExtra("title") ?: "أذكار", many.toList().mapNotNull { Adhkar.byId(this, it) }.flatMap { it.items })
        else Adhkar.byId(this, id) ?: Adhkar.all(this).first()
        val (frame, root) = Ui.screen(this)
        scroll = frame.findViewWithTag("scroll")
        val d = { v: Int -> Ui.dp(this, v) }

        root.addView(Nav.back(this))
        root.addView(Ui.tv(this, cat.name, 26f, Ui.GOLD, true).apply { gravity = Gravity.CENTER })
        if (cat.id == Adhkar.ADHAN) {
            root.addView(Ui.tv(this, "🤲 الدعاء بين الأذان والإقامة لا يُرد، فاغتنم الوقت", 14f, 0xFFF6DE8D.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(d(12), d(10), d(12), d(10))
                background = Ui.rounded(this@AdhkarReaderActivity, 0x26D9B44A, 14f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = d(8) })
        }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = cat.items.size
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.GOLD)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33F6DE8D)
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(10)).also { it.topMargin = d(14) })
        progressTv = Ui.tv(this, "", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(progressTv)

        playAllBtn = Ui.tv(this, "▶ تشغيل الكل بالصوت", 16f, 0xFF0B231E.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(d(16), d(12), d(16), d(12))
            background = Ui.rounded(this@AdhkarReaderActivity, Ui.GOLD, 24f)
            setOnClickListener { togglePlayAll() }
        }
        root.addView(playAllBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = d(10) })

        cat.items.forEachIndexed { i, dh ->
            remaining.add(dh.count)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(16), d(16), d(16), d(14))
                background = Ui.rounded(this@AdhkarReaderActivity, 0xD90F2E27.toInt(), 20f, 0x33D9B44A)
                setOnClickListener { tap(i) }
            }
            val text = Ui.tv(this, dh.text, 25f, Ui.TEXT).apply {
                setLineSpacing(0f, 1.55f)
                textDirection = View.TEXT_DIRECTION_RTL
            }
            card.addView(text)

            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(12), 0, 0)
            }
            val counter = Ui.tv(this, Fmt.num(dh.count), 22f, 0xFF0B231E.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.GOLD) }
                setOnClickListener { tap(i) }
            }
            bar.addView(counter, LinearLayout.LayoutParams(d(58), d(58)))
            bar.addView(Ui.tv(this, if (dh.count > 1) "اضغط للعدّ • ${Fmt.num(dh.count)} مرات" else "مرة واحدة", 13f, Ui.MUTED).apply {
                setPadding(d(12), 0, d(12), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val play = Ui.tv(this, "🔊 استمع", 14f, Ui.GOLD, true).apply {
                setPadding(d(14), d(8), d(14), d(8))
                background = Ui.rounded(this@AdhkarReaderActivity, Color.TRANSPARENT, 18f, Ui.GOLD)
                visibility = if (dh.audio.isNotEmpty()) View.VISIBLE else View.GONE
                setOnClickListener { playAll = false; updatePlayAllBtn(); play(i) }
            }
            bar.addView(play)
            card.addView(bar)

            cards.add(card); counters.add(counter); playBtns.add(play)
            root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = d(12) })
            card.alpha = 0f; card.translationY = d(24).toFloat()
            card.animate().alpha(1f).translationY(0f).setStartDelay(60L * i.coerceAtMost(8)).setDuration(450).start()
        }
        root.addView(Ui.tv(this, "المصدر: كتاب حصن المسلم • التسجيلات: hisnmuslim.com", 11f, Ui.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, d(20), 0, 0)
        })
        updateProgress()
        setContentView(frame)
        if (intent.getBooleanExtra("autoplay", false)) togglePlayAll()
    }

    private fun tap(i: Int) {
        if (remaining[i] <= 0) return
        remaining[i]--
        counters[i].text = if (remaining[i] > 0) Fmt.num(remaining[i]) else "✓"
        counters[i].performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        counters[i].animate().scaleX(1.18f).scaleY(1.18f).setDuration(90).withEndAction {
            counters[i].animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        if (remaining[i] == 0) {
            buzz()
            cards[i].animate().alpha(0.5f).setDuration(400).start()
            updateProgress()
            // ينتقل للذكر اللي بعده
            val next = (i + 1 until cards.size).firstOrNull { remaining[it] > 0 }
            if (next != null) scroll.postDelayed({ scroll.smoothScrollTo(0, cards[next].top - Ui.dp(this, 12)) }, 350)
        }
    }

    private fun buzz() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }

    private fun updateProgress() {
        val done = remaining.count { it == 0 }
        progress.progress = done
        progressTv.text = if (done == cat.items.size) "ما شاء الله، أتممت ${cat.name} 🤍"
        else "${Fmt.num(done)} من ${Fmt.num(cat.items.size)}"
    }

    // ---------- الصوت ----------
    private fun play(i: Int) {
        stopAudio()
        val a = cat.items[i].audio
        if (a.isEmpty()) { onAudioDone(i); return }
        try {
            val fd = assets.openFd("adhkar/$a.ogg")
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            fd.close()
            mp.setOnCompletionListener { onAudioDone(i) }
            mp.prepare()
            mp.start()
            player = mp
            playingIndex = i
            playBtns[i].text = "⏸ إيقاف"
            playBtns[i].setOnClickListener { playAll = false; updatePlayAllBtn(); stopAudio() }
            cards[i].background = Ui.rounded(this, 0xE6143A31.toInt(), 20f, Ui.GOLD)
            scroll.smoothScrollTo(0, cards[i].top - Ui.dp(this, 12))
        } catch (e: Exception) {
            onAudioDone(i)
        }
    }

    private fun onAudioDone(i: Int) {
        resetCard(i)
        if (playAll) {
            val next = i + 1
            if (next < cat.items.size) play(next) else { playAll = false; updatePlayAllBtn() }
        }
    }

    private fun resetCard(i: Int) {
        if (i !in cards.indices) return
        playBtns[i].text = "🔊 استمع"
        playBtns[i].setOnClickListener { playAll = false; updatePlayAllBtn(); play(i) }
        cards[i].background = Ui.rounded(this, 0xD90F2E27.toInt(), 20f, 0x33D9B44A)
    }

    private fun stopAudio() {
        try { player?.stop() } catch (e: Exception) { }
        player?.release()
        player = null
        if (playingIndex >= 0) resetCard(playingIndex)
        playingIndex = -1
    }

    private fun togglePlayAll() {
        if (playAll) { playAll = false; stopAudio() } else {
            playAll = true
            val start = (0 until cat.items.size).firstOrNull { remaining[it] > 0 } ?: 0
            play(start)
        }
        updatePlayAllBtn()
    }

    private fun updatePlayAllBtn() {
        playAllBtn.text = if (playAll) "⏸ إيقاف التشغيل" else "▶ تشغيل الكل بالصوت"
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
