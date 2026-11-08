package com.alawi.mawaqit

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

// =================== البيانات ===================

data class Surah(val n: Int, val name: String, val meccan: Boolean, val verses: List<String>)

/**
 * نص القرآن الكريم: Tanzil Quran Text (Uthmani, v1.1)
 * Copyright (C) 2007-2021 Tanzil Project — CC BY 3.0 — https://tanzil.net
 * النص منقول حرفياً بدون أي تعديل (شرط الترخيص).
 */
object Quran {
    @Volatile private var surahs: List<Surah>? = null
    private var juz: List<Pair<Int, Int>> = emptyList()
    private var hizb: List<Pair<Int, Int>> = emptyList()
    private var pages: List<Pair<Int, Int>> = emptyList()
    private var sajdas: Set<Pair<Int, Int>> = emptySet()
    var source: String = ""
        private set

    @Synchronized
    fun load(ctx: Context): List<Surah> {
        surahs?.let { return it }
        val root = JSONObject(ctx.assets.open("quran.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        source = root.optString("source")
        val arr = root.getJSONArray("surahs")
        val list = ArrayList<Surah>(114)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val v = o.getJSONArray("v")
            list.add(Surah(o.getInt("n"), o.getString("name"), o.optString("type") == "meccan",
                List(v.length()) { v.getString(it) }))
        }
        fun pairs(key: String): List<Pair<Int, Int>> {
            val a = root.optJSONArray(key) ?: return emptyList()
            return List(a.length()) { Pair(a.getJSONArray(it).getInt(0), a.getJSONArray(it).getInt(1)) }
        }
        juz = pairs("juz"); hizb = pairs("hizb"); pages = pairs("pages"); sajdas = pairs("sajdas").toSet()
        surahs = list
        return list
    }

    fun surah(ctx: Context, n: Int) = load(ctx)[(n - 1).coerceIn(0, 113)]

    /** البسملة: في المصدر هي أول ٤ كلمات من الآية الأولى (إلا الفاتحة والتوبة). نفصلها عرضاً فقط */
    fun hasBasmalaHeader(n: Int) = n != 1 && n != 9

    fun basmala(s: Surah): String = if (hasBasmalaHeader(s.n)) s.verses[0].split(' ').take(4).joinToString(" ") else ""

    fun verseText(s: Surah, i: Int): String =
        if (i == 0 && hasBasmalaHeader(s.n)) s.verses[0].split(' ').drop(4).joinToString(" ") else s.verses[i]

    private fun indexOf(list: List<Pair<Int, Int>>, s: Int, a: Int): Int {
        var r = 0
        for ((i, p) in list.withIndex()) {
            if (p.first < s || (p.first == s && p.second <= a)) r = i else break
        }
        return r + 1
    }

    fun juzOf(s: Int, a: Int) = indexOf(juz, s, a)
    fun pageOf(s: Int, a: Int) = indexOf(pages, s, a)
    fun juzStartsAt(s: Int, a: Int) = juz.indexOf(Pair(s, a)).let { if (it >= 0) it + 1 else 0 }
    fun hizbStartsAt(s: Int, a: Int) = hizb.indexOf(Pair(s, a)).let { if (it >= 0) it + 1 else 0 }
    fun isSajda(s: Int, a: Int) = Pair(s, a) in sajdas
    fun juzStart(j: Int): Pair<Int, Int> = juz.getOrElse(j - 1) { Pair(1, 1) }

    fun pageStart(ctx: Context, p: Int): Pair<Int, Int> { load(ctx); return pages.getOrElse(p - 1) { Pair(1, 1) } }

    /** آخر آية في الصفحة */
    fun pageEnd(ctx: Context, p: Int): Pair<Int, Int> {
        load(ctx)
        if (p >= pages.size) return Pair(114, 6)
        val (s, a) = pages[p]
        return if (a > 1) Pair(s, a - 1) else Pair(s - 1, surah(ctx, s - 1).verses.size)
    }

    /** للبحث: نشيل التشكيل وعلامات الرسم العثماني */
    fun plain(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = ch.code
            if (c in 0x0610..0x061A || c in 0x064B..0x065F || c == 0x0670 || c in 0x06D6..0x06ED || c == 0x0640) continue
            sb.append(when (ch) { 'ٱ', 'أ', 'إ', 'آ' -> 'ا'; 'ة' -> 'ه'; 'ى' -> 'ي'; 'ؤ' -> 'و'; 'ئ' -> 'ي'; else -> ch })
        }
        return sb.toString()
    }

    fun search(ctx: Context, q: String, max: Int = 200): List<Triple<Int, Int, String>> {
        val pq = plain(q.trim())
        if (pq.length < 2) return emptyList()
        val out = ArrayList<Triple<Int, Int, String>>()
        for (s in load(ctx)) for (i in s.verses.indices) {
            val t = verseText(s, i)
            if (plain(t).contains(pq)) { out.add(Triple(s.n, i + 1, t)); if (out.size >= max) return out }
        }
        return out
    }

    fun open(ctx: Context, surah: Int, ayah: Int = 1) {
        ctx.startActivity(Intent(ctx, QuranReaderActivity::class.java)
            .putExtra("s", surah).putExtra("a", ayah).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** رقم الآية داخل الزخرفة ﴿١٢﴾ */
    fun marker(n: Int) = "﴿${Fmt.num(n)}﴾"
}

/** آخر قراءة والعلامات */
object QuranPrefs {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("quran", Context.MODE_PRIVATE)
    fun lastSurah(ctx: Context) = sp(ctx).getInt("last_s", 0)
    fun lastAyah(ctx: Context) = sp(ctx).getInt("last_a", 1)
    fun setLast(ctx: Context, s: Int, a: Int) = sp(ctx).edit().putInt("last_s", s).putInt("last_a", a).apply()
    /** وين وقفت في الاستماع */
    fun listenSurah(ctx: Context) = sp(ctx).getInt("listen_s", 0)
    fun listenMs(ctx: Context) = sp(ctx).getInt("listen_ms", 0)
    fun setListen(ctx: Context, s: Int, ms: Int) = sp(ctx).edit().putInt("listen_s", s).putInt("listen_ms", ms).apply()
    fun fontSize(ctx: Context) = sp(ctx).getFloat("font2", 36f)
    fun setFontSize(ctx: Context, v: Float) = sp(ctx).edit().putFloat("font2", v).apply()
    fun paper(ctx: Context) = sp(ctx).getBoolean("paper", false)
    fun setPaper(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("paper", v).apply()
    fun bookmarks(ctx: Context): List<Pair<Int, Int>> =
        (sp(ctx).getString("marks", "") ?: "").split(',').mapNotNull {
            val p = it.split(':'); if (p.size == 2) Pair(p[0].toIntOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: return@mapNotNull null) else null
        }
    fun toggleBookmark(ctx: Context, s: Int, a: Int): Boolean {
        val list = bookmarks(ctx).toMutableList()
        val added = if (list.remove(Pair(s, a))) false else { list.add(0, Pair(s, a)); true }
        sp(ctx).edit().putString("marks", list.joinToString(",") { "${it.first}:${it.second}" }).apply()
        return added
    }
}

// =================== فهرس المصحف ===================

class QuranActivity : Activity() {
    private lateinit var listBox: LinearLayout
    private lateinit var tabSurah: TextView
    private lateinit var tabJuz: TextView
    private lateinit var tabMarks: TextView
    private var tab = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(color: Int, r: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = r * resources.displayMetrics.density; if (stroke != null) setStroke(dp(1), stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Quran.load(this)
        val frame = FrameLayout(this)
        frame.addView(SkyView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = android.widget.ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        frame.addView(scroll)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val w = resources.configuration.screenWidthDp
            val side = if (w > 600) ((w - 600) / 2).coerceAtLeast(16) else 16
            setPadding(dp(side), dp(22), dp(side), dp(40))
        }
        scroll.addView(root)
        val amiri = resources.getFont(R.font.amiri_quran)

        root.addView(tv("المصحف الشريف", 32f, C_GOLD, true).apply { gravity = Gravity.CENTER; typeface = amiri })
        root.addView(tv("برواية حفص عن عاصم • الرسم العثماني", 12f, 0xFFA9BDB4.toInt()).apply { gravity = Gravity.CENTER })

        // متابعة القراءة
        val ls = QuranPrefs.lastSurah(this)
        if (ls > 0) {
            val s = Quran.surah(this, ls)
            root.addView(tv("📖 تابع القراءة: سورة ${s.name} • آية ${Fmt.num(QuranPrefs.lastAyah(this))}", 16f, 0xFF0B231E.toInt(), true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(C_GOLD, 18f)
                setOnClickListener { Quran.open(this@QuranActivity, ls, QuranPrefs.lastAyah(this@QuranActivity)) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(14) })
        }
        // تابع الاستماع
        val lsS = QuranPrefs.listenSurah(this)
        if (lsS > 0) {
            val su = Quran.surah(this, lsS)
            val ms = QuranPrefs.listenMs(this)
            root.addView(tv("🎧 تابع الاستماع: سورة ${su.name} (${Fmt.num(ms / 60000)}:${(ms / 1000 % 60).toString().padStart(2, '0')})", 15f, C_GOLD, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(0x26D9B44A, 16f, C_GOLD)
                setOnClickListener {
                    QuranPlayer.play(this@QuranActivity, lsS, 1, ms)
                    startActivity(Intent(this@QuranActivity, QuranReaderActivity::class.java).putExtra("s", lsS).putExtra("follow", true))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        }
        root.addView(tv("🎙 بصوت ${QuranPlayer.RECITER}", 12f, 0xFFA9BDB4.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        // الكهف
        root.addView(tv("سورة الكهف", 15f, C_GOLD, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0x00000000, 16f, C_GOLD)
            setOnClickListener { Quran.open(this@QuranActivity, 18) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })

        // البحث
        val search = EditText(this).apply {
            hint = "🔍 ابحث في القرآن (بدون تشكيل)…"
            setHintTextColor(0xFFA9BDB4.toInt()); setTextColor(C_TEXT); textSize = 16f
            background = rounded(0x33000000, 16f, 0x55D9B44A)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })

        // الختمة
        val kh = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xCC0F2E27.toInt(), 18f, 0x40D9B44A)
        }
        kh.addView(tv("🎯 ختمة القرآن", 18f, C_GOLD, true))
        if (!Khatma.active(this)) {
            kh.addView(tv("اختر كم يوم تبغى تختم فيه، والتطبيق يقسم لك الورد اليومي ويذكّرك فيه بعد العشاء.", 13f, 0xFFA9BDB4.toInt()))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            for (d in listOf(30, 20, 15, 10, 7)) row.addView(tv("${Fmt.num(d)} يوم", 14f, 0xFF0B231E.toInt(), true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                background = rounded(C_GOLD, 14f)
                setOnClickListener { Khatma.start(this@QuranActivity, d); Scheduler.scheduleNext(this@QuranActivity); recreate() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) })
            kh.addView(row)
        } else if (Khatma.finished(this)) {
            kh.addView(tv("🎉 ما شاء الله، أتممت الختمة! اللهم اجعل القرآن ربيع قلوبنا", 15f, C_TEXT, true).apply { setPadding(0, dp(8), 0, 0) })
            kh.addView(tv("ابدأ ختمة جديدة", 14f, C_GOLD, true).apply {
                setPadding(0, dp(10), 0, 0); setOnClickListener { Khatma.stop(this@QuranActivity); recreate() }
            })
        } else {
            kh.addView(tv("ختمة في ${Fmt.num(Khatma.days(this))} يوم • أنجزت ${Fmt.num(Khatma.percent(this))}٪ • ${Fmt.num(Khatma.pagesPerDay(this))} صفحة يومياً", 13f, 0xFFA9BDB4.toInt()))
            val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = Khatma.percent(this@QuranActivity)
                progressTintList = android.content.res.ColorStateList.valueOf(C_GOLD)
            }
            kh.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).also { it.topMargin = dp(8) })
            kh.addView(tv((if (Khatma.doneToday(this)) "✅ خلّصت ورد اليوم. الورد القادم: " else "📖 ورد اليوم: ") + Khatma.describe(this), 14f, C_TEXT).apply { setPadding(0, dp(8), 0, 0) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            row.addView(tv("📖 اقرأ الورد", 14f, 0xFF0B231E.toInt(), true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); background = rounded(C_GOLD, 14f)
                setOnClickListener { val (s, a) = Quran.pageStart(this@QuranActivity, Khatma.nextRange(this@QuranActivity).first); Quran.open(this@QuranActivity, s, a) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) })
            row.addView(tv("✓ أنهيت الورد", 14f, C_GOLD, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); background = rounded(0x00000000, 14f, C_GOLD)
                setOnClickListener { Khatma.markDone(this@QuranActivity); Toast.makeText(this@QuranActivity, "بارك الله فيك 🤍", Toast.LENGTH_SHORT).show(); recreate() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            kh.addView(row)
            kh.addView(tv("إلغاء الختمة", 11f, 0xFFA9BDB4.toInt()).apply {
                setPadding(0, dp(8), 0, 0); setOnClickListener { Khatma.stop(this@QuranActivity); recreate() }
            })
        }
        root.addView(kh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })

        // التلاوة بدون إنترنت
        val dl = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(0xCC0F2E27.toInt(), 18f, 0x26D9B44A)
        }
        dl.addView(tv("⬇ التلاوة بدون إنترنت", 17f, C_GOLD, true))
        dl.addView(tv("محمّل ${Fmt.num(QuranDownloads.readyCount(this))} من ١١٤ سورة (${Fmt.num(QuranDownloads.sizeMb(this))} ميجا)", 13f, 0xFFA9BDB4.toInt()))
        fun dlBtn(label: String, list: List<Int>, confirm: String? = null) = tv(label, 14f, C_TEXT, true).apply {
            setPadding(0, dp(10), 0, dp(4))
            setOnClickListener {
                val go = {
                    val n = QuranDownloads.download(this@QuranActivity, list)
                    Toast.makeText(this@QuranActivity, if (n > 0) "بدأ تحميل ${Fmt.num(n)} سورة، تابعه من الإشعارات" else "محمّلة من قبل", Toast.LENGTH_SHORT).show()
                }
                if (confirm != null) AlertDialog.Builder(this@QuranActivity).setMessage(confirm)
                    .setPositiveButton("حمّل") { _, _ -> go() }.setNegativeButton("لا", null).show() else go()
            }
        }
        dl.addView(dlBtn("• تحميل جزء عمّ (٧٨–١١٤)", (78..114).toList()))
        dl.addView(dlBtn("• تحميل الكهف ويس والملك والواقعة والرحمن", listOf(18, 36, 67, 56, 55)))
        dl.addView(dlBtn("• تحميل المصحف كامل (حوالي ١٫٤ جيجا)", (1..114).toList(),
            "المصحف كامل حجمه حوالي ١٫٤ جيجا. يفضّل تحمّله على الواي فاي. تبدأ؟"))
        dl.addView(tv("🗑 حذف التحميلات", 12f, 0xFFA9BDB4.toInt()).apply {
            setPadding(0, dp(10), 0, 0)
            setOnClickListener {
                AlertDialog.Builder(this@QuranActivity).setMessage("تحذف كل التلاوات المحمّلة؟")
                    .setPositiveButton("احذف") { _, _ -> QuranDownloads.deleteAll(this@QuranActivity); recreate() }
                    .setNegativeButton("لا", null).show()
            }
        })
        root.addView(dl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })

        // تبويبات
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(4)) }
        fun mkTab(label: String, i: Int) = tv(label, 15f, C_TEXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { tab = i; search.setText(""); fill() }
        }
        tabSurah = mkTab("السور", 0); tabJuz = mkTab("الأجزاء", 1); tabMarks = mkTab("العلامات", 2)
        for (t in listOf(tabSurah, tabJuz, tabMarks)) tabs.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) })
        root.addView(tabs)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)
        root.addView(tv(Quran.source.ifEmpty { "Tanzil Quran Text — CC BY 3.0 — tanzil.net" }.let { "مصدر النص: $it" }, 10f, 0xFFA9BDB4.toInt()).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0)
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                if (q.trim().length >= 2) showResults(q) else fill()
            }
        })
        setContentView(frame)
        fill()
    }

    override fun onResume() { super.onResume(); if (::listBox.isInitialized && tab == 2) fill() }

    private fun row(title: String, sub: String, badge: String, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xCC0F2E27.toInt(), 16f, 0x26D9B44A)
            setOnClickListener { onClick() }
        }
        r.addView(tv(badge, 13f, C_GOLD, true).apply { gravity = Gravity.CENTER; background = rounded(0x26D9B44A, 14f) },
            LinearLayout.LayoutParams(dp(40), dp(40)).also { it.marginEnd = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 22f, C_TEXT, true).apply { typeface = resources.getFont(R.font.amiri_quran) })
        col.addView(tv(sub, 12f, 0xFFA9BDB4.toInt()))
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    private fun add(v: View) = listBox.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })

    private fun fill() {
        listBox.removeAllViews()
        listOf(tabSurah, tabJuz, tabMarks).forEachIndexed { i, t ->
            t.background = rounded(if (i == tab) C_GOLD else 0x1AFFFFFF, 14f)
            t.setTextColor(if (i == tab) 0xFF0B231E.toInt() else C_TEXT)
        }
        when (tab) {
            0 -> for (s in Quran.load(this)) add(row("سورة ${s.name}",
                "${if (s.meccan) "مكية" else "مدنية"} • ${Fmt.num(s.verses.size)} آية • الجزء ${Fmt.num(Quran.juzOf(s.n, 1))}" +
                    if (QuranDownloads.isReady(this, s.n)) " • 🎧 محمّلة" else "",
                Fmt.num(s.n)) { Quran.open(this, s.n) })
            1 -> for (j in 1..30) {
                val (s, a) = Quran.juzStart(j)
                add(row("الجزء ${Fmt.num(j)}", "يبدأ من سورة ${Quran.surah(this, s).name} آية ${Fmt.num(a)}", Fmt.num(j)) { Quran.open(this, s, a) })
            }
            else -> {
                val marks = QuranPrefs.bookmarks(this)
                if (marks.isEmpty()) listBox.addView(tv("ما عندك علامات. اضغط مطوّلاً على أي آية واختر «حفظ علامة».", 14f, 0xFFA9BDB4.toInt()).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
                })
                for ((s, a) in marks) {
                    val su = Quran.surah(this, s)
                    add(row("سورة ${su.name} • آية ${Fmt.num(a)}", Quran.verseText(su, a - 1).take(70) + "…", "🔖") { Quran.open(this, s, a) })
                }
            }
        }
    }

    private fun showResults(q: String) {
        listBox.removeAllViews()
        val res = Quran.search(this, q)
        listBox.addView(tv(if (res.isEmpty()) "ما فيه نتائج" else "${Fmt.num(res.size)}${if (res.size >= 200) "+" else ""} نتيجة", 13f, 0xFFA9BDB4.toInt()).apply {
            setPadding(0, dp(8), 0, 0)
        })
        for ((s, a, t) in res.take(100)) {
            add(row("سورة ${Quran.surah(this, s).name} • ${Fmt.num(a)}", t.take(90), "🔍") { Quran.open(this, s, a) })
        }
    }
}

// =================== قارئ السورة ===================

class QuranReaderActivity : Activity() {
    private lateinit var surah: Surah
    private lateinit var list: ListView
    private lateinit var adapter: VerseAdapter
    private var fontSize = 36f
    private var paper = false
    private var highlight = -1
    private var follow = false
    private var lastPlayed = -1
    private lateinit var playBtn: TextView
    private lateinit var audioInfo: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val bg get() = if (paper) 0xFFFBF5E6.toInt() else 0xFF071814.toInt()
    private val fg get() = if (paper) 0xFF1E1A12.toInt() else C_TEXT
    private val accent get() = if (paper) 0xFF8C6B1F.toInt() else C_GOLD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fontSize = QuranPrefs.fontSize(this)
        paper = QuranPrefs.paper(this)
        surah = Quran.surah(this, intent.getIntExtra("s", 1))
        follow = intent.getBooleanExtra("follow", false)
        build(intent.getIntExtra("a", 1))
    }

    private fun build(startAyah: Int) {
        val amiri = resources.getFont(R.font.amiri_quran)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bg)
        }
        window.statusBarColor = bg
        window.navigationBarColor = bg

        // الشريط العلوي
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        fun btn(s: String, onClick: () -> Unit) = TextView(this).apply {
            text = s; textSize = 16f; setTextColor(accent); gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
        bar.addView(btn("◀") { go(surah.n - 1) })
        bar.addView(TextView(this).apply {
            text = "سورة ${surah.name}"; textSize = 22f; setTextColor(accent); gravity = Gravity.CENTER; typeface = amiri
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("أ−") { setFont(fontSize - 4f) })
        bar.addView(btn("أ+") { setFont(fontSize + 4f) })
        bar.addView(btn(if (paper) "🌙" else "📜") { paper = !paper; QuranPrefs.setPaper(this, paper); build(firstVisibleAyah()) })
        bar.addView(btn("▶") { go(surah.n + 1) })
        root.addView(bar)

        list = ListView(this).apply {
            divider = null
            isVerticalScrollBarEnabled = true
            setPadding(dp(14), 0, dp(14), dp(30))
            clipToPadding = false
            val w = resources.configuration.screenWidthDp
            if (w > 640) setPadding(dp((w - 600) / 2), 0, dp((w - 600) / 2), dp(30))
        }
        adapter = VerseAdapter(amiri)
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, pos, _ -> if (pos > 0) verseMenu(pos - 1); true }
        list.setOnItemClickListener { _, _, pos, _ -> if (pos > 0) { highlight = if (highlight == pos - 1) -1 else pos - 1; adapter.notifyDataSetChanged() } }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) {
                if (state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) QuranPrefs.setLast(this@QuranReaderActivity, surah.n, firstVisibleAyah())
            }
            override fun onScroll(v: AbsListView?, first: Int, count: Int, total: Int) {}
        })
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // شريط التلاوة
        val audio = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(if (paper) 0xFFF1E6C8.toInt() else 0xFF0F2E27.toInt()) }
        }
        playBtn = TextView(this).apply {
            textSize = 22f; gravity = Gravity.CENTER; setTextColor(if (paper) 0xFFFBF5E6.toInt() else 0xFF0B231E.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            setOnClickListener {
                if (QuranPlayer.surah == surah.n && (QuranPlayer.playing || QuranPlayer.preparing)) QuranPlayer.toggle(this@QuranReaderActivity)
                else {
                    follow = true
                    val resume = if (QuranPrefs.listenSurah(this@QuranReaderActivity) == surah.n) QuranPrefs.listenMs(this@QuranReaderActivity) else -1
                    if (QuranPlayer.surah == surah.n && !QuranPlayer.playing && !QuranPlayer.preparing && QuranPlayer.durationMs() > 0) QuranPlayer.toggle(this@QuranReaderActivity)
                    else QuranPlayer.play(this@QuranReaderActivity, surah.n, 1, resume)
                }
            }
        }
        audio.addView(playBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        audioInfo = TextView(this).apply {
            textSize = 13f; setTextColor(fg); setPadding(dp(12), 0, dp(12), 0)
        }
        audio.addView(audioInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        audio.addView(TextView(this).apply {
            text = "■"; textSize = 18f; setTextColor(accent); setPadding(dp(12), dp(6), dp(6), dp(6))
            setOnClickListener { QuranPlayer.stop(this@QuranReaderActivity) }
        })
        root.addView(audio)
        setContentView(root)
        updateAudioUi()
        if (startAyah > 1) list.setSelection(startAyah) // +1 للرأس، -1 للفهرسة
        QuranPrefs.setLast(this, surah.n, startAyah)
    }

    private fun firstVisibleAyah() = list.firstVisiblePosition.coerceAtLeast(1)

    private fun setFont(v: Float) {
        fontSize = v.coerceIn(24f, 64f)
        QuranPrefs.setFontSize(this, fontSize)
        adapter.notifyDataSetChanged()
    }

    private fun go(n: Int) {
        if (n !in 1..114) return
        surah = Quran.surah(this, n)
        highlight = -1
        build(1)
    }

    private fun verseMenu(i: Int) {
        val a = i + 1
        val text = Quran.verseText(surah, i)
        val ref = "﴿$text﴾ [${surah.name}: ${Fmt.num(a)}]"
        val marked = QuranPrefs.bookmarks(this).contains(Pair(surah.n, a))
        AlertDialog.Builder(this)
            .setTitle("سورة ${surah.name} • آية ${Fmt.num(a)}")
            .setItems(arrayOf(if (marked) "🔖 إزالة العلامة" else "🔖 حفظ علامة", "📋 نسخ الآية", "📤 مشاركة الآية", "🎧 استمع من هذه الآية",
                "📘 التفسير الميسر", if (QuranDownloads.isReady(this, surah.n)) "✅ السورة محمّلة" else "⬇ تحميل السورة للاستماع بدون إنترنت")) { _, w ->
                when (w) {
                    4 -> showTafsir(a)
                    5 -> if (!QuranDownloads.isReady(this, surah.n)) {
                        QuranDownloads.download(this, listOf(surah.n))
                        Toast.makeText(this, "بدأ التحميل، تابعه من الإشعارات", Toast.LENGTH_SHORT).show()
                    }
                    3 -> { follow = true; QuranPlayer.play(this, surah.n, a) }
                    0 -> { val added = QuranPrefs.toggleBookmark(this, surah.n, a)
                        Toast.makeText(this, if (added) "تم حفظ العلامة" else "تمت إزالة العلامة", Toast.LENGTH_SHORT).show() }
                    1 -> { getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("آية", ref))
                        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show() }
                    2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ref), "مشاركة"))
                }
            }.show()
    }

    /** التفسير الميسر للآية */
    fun showTafsir(a: Int) {
        val t = Tafsir.of(this, surah.n, a).replace("[التفسير]", "\n📘 التفسير:")
        val box = android.widget.ScrollView(this)
        box.addView(TextView(this).apply {
            text = "﴿${Quran.verseText(surah, a - 1)}﴾\n\n$t\n\n— التفسير الميسر، مجمع الملك فهد لطباعة المصحف الشريف"
            textSize = 17f; setTextColor(0xFF1E1A12.toInt()); setLineSpacing(0f, 1.4f)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            textDirection = View.TEXT_DIRECTION_RTL
        })
        AlertDialog.Builder(this)
            .setTitle("سورة ${surah.name} • آية ${Fmt.num(a)}")
            .setView(box)
            .setPositiveButton("تم", null)
            .setNeutralButton("الآية التالية") { _, _ -> if (a < surah.verses.size) showTafsir(a + 1) }
            .show()
    }

    override fun onResume() {
        super.onResume()
        QuranPlayer.setListener { updateAudioUi() }
        if (::playBtn.isInitialized) updateAudioUi()
    }

    override fun onPause() {
        super.onPause()
        QuranPlayer.setListener(null)
        if (::list.isInitialized) QuranPrefs.setLast(this, surah.n, firstVisibleAyah())
    }

    /** نحدّث زر التشغيل ونظلل الآية اللي يقرأها الشيخ ونلحقها بالتمرير */
    private fun updateAudioUi() {
        // لو الشيخ انتقل للسورة اللي بعدها والمتابعة شغالة، ننتقل معه
        if (follow && QuranPlayer.surah > 0 && QuranPlayer.surah != surah.n && (QuranPlayer.playing || QuranPlayer.preparing)) {
            surah = Quran.surah(this, QuranPlayer.surah); highlight = -1; lastPlayed = -1
            build(1); return
        }
        val mine = QuranPlayer.surah == surah.n
        playBtn.text = if (mine && QuranPlayer.playing) "⏸" else if (mine && QuranPlayer.preparing) "…" else "▶"
        val pos = QuranPlayer.positionMs()
        audioInfo.text = when {
            mine && QuranPlayer.preparing -> "جاري التحميل… (يحتاج إنترنت)"
            mine && QuranPlayer.durationMs() > 0 -> "${QuranPlayer.RECITER} • ${pos / 60000}:${(pos / 1000 % 60).toString().padStart(2, '0')} / ${QuranPlayer.durationMs() / 60000}:${(QuranPlayer.durationMs() / 1000 % 60).toString().padStart(2, '0')}"
            QuranPrefs.listenSurah(this) == surah.n && QuranPrefs.listenMs(this) > 0 -> "اضغط ▶ وتكمل من وين وقفت"
            else -> "استمع بصوت ${QuranPlayer.RECITER}"
        }
        val cur = if (mine) QuranPlayer.currentAyah() else -1
        if (cur != lastPlayed) {
            lastPlayed = cur
            adapter.notifyDataSetChanged()
            if (follow && cur >= 1) list.smoothScrollToPositionFromTop(cur, dp(80))
        }
    }

    private inner class VerseAdapter(val amiri: Typeface) : BaseAdapter() {
        override fun getCount() = surah.verses.size + 1
        override fun getItem(p: Int): Any = p
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(p: Int) = if (p == 0) 0 else 1

        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            if (p == 0) return header()
            val i = p - 1
            val a = i + 1
            val box = (convert as? LinearLayout) ?: LinearLayout(this@QuranReaderActivity).apply { orientation = LinearLayout.VERTICAL }
            box.removeAllViews()
            // بداية جزء / حزب
            val j = Quran.juzStartsAt(surah.n, a)
            val h = Quran.hizbStartsAt(surah.n, a)
            if ((j > 0 || h > 0) && !(surah.n == 1 && a == 1)) {
                box.addView(TextView(this@QuranReaderActivity).apply {
                    text = if (j > 0) "۞ الجزء ${Fmt.num(j)}" else "۞ الحزب ${Fmt.num(h)}"
                    textSize = 13f; setTextColor(accent); gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(4))
                })
            }
            val t = TextView(this@QuranReaderActivity).apply {
                typeface = amiri
                textSize = fontSize
                setTextColor(fg)
                setLineSpacing(0f, 1.6f)
                gravity = Gravity.START
                textDirection = View.TEXT_DIRECTION_RTL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                text = Quran.verseText(surah, i) + " " + Quran.marker(a) + if (Quran.isSajda(surah.n, a)) " ۩" else ""
                val playingThis = QuranPlayer.surah == surah.n && lastPlayed == a
                if (playingThis) setTextColor(accent)
                background = if (playingThis) GradientDrawable().apply {
                    setColor(if (paper) 0x40D9B44A else 0x33D9B44A); cornerRadius = dp(12).toFloat(); setStroke(dp(1), accent)
                } else if (i == highlight) GradientDrawable().apply {
                    setColor(if (paper) 0x33D9B44A else 0x26D9B44A); cornerRadius = dp(12).toFloat()
                } else null
            }
            box.addView(t)
            return box
        }

        private fun header(): View {
            val box = LinearLayout(this@QuranReaderActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(10))
            }
            box.addView(TextView(this@QuranReaderActivity).apply {
                text = "سورة ${surah.name}"
                typeface = amiri; textSize = 36f; setTextColor(accent); gravity = Gravity.CENTER
                setPadding(dp(20), dp(10), dp(20), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setStroke(dp(2), accent)
                    setColor(if (paper) 0x1AD9B44A else 0x1AD9B44A)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            box.addView(TextView(this@QuranReaderActivity).apply {
                text = "${if (surah.meccan) "مكية" else "مدنية"} • ${Fmt.num(surah.verses.size)} آية • الجزء ${Fmt.num(Quran.juzOf(surah.n, 1))} • الصفحة ${Fmt.num(Quran.pageOf(surah.n, 1))}"
                textSize = 12f; setTextColor(if (paper) 0xFF6B5E45.toInt() else 0xFFA9BDB4.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            val b = Quran.basmala(surah)
            if (b.isNotEmpty()) box.addView(TextView(this@QuranReaderActivity).apply {
                text = b; typeface = amiri; textSize = fontSize + 4f; setTextColor(fg); gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(4))
            })
            return box
        }
    }
}
