package com.alawi.mawaqit

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.icu.util.IslamicCalendar
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** التقويم الهجري (أم القرى) مع الميلادي، والمناسبات، وأحداث التاريخ الإسلامي */
class HijriCalendarActivity : Activity() {
    private var year = 0
    private var month = 0
    private lateinit var title: TextView
    private lateinit var grid: LinearLayout
    private lateinit var details: LinearLayout
    private lateinit var monthEvents: LinearLayout
    private var selected: Long = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(color: Int, r: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = r * resources.displayMetrics.density; if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(0xCC0F2E27.toInt(), 20f, 0x33D9B44A)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = System.currentTimeMillis()
        val h = Hijri.date(now)
        year = h.year; month = h.month; selected = now

        val frame = FrameLayout(this)
        frame.addView(SkyView(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        frame.addView(scroll)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val w = resources.configuration.screenWidthDp
            val side = if (w > 600) ((w - 600) / 2).coerceAtLeast(16) else 16
            setPadding(dp(side), dp(22), dp(side), dp(40))
        }
        scroll.addView(root)

        root.addView(tv("📅 التقويم الهجري", 28f, C_GOLD, true).apply { gravity = Gravity.CENTER })
        root.addView(tv("حسب تقويم أم القرى", 12f, 0xFFA9BDB4.toInt()).apply { gravity = Gravity.CENTER })

        // التنقل بين الشهور
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(6)) }
        fun arrow(s: String, d: Int) = tv(s, 22f, C_GOLD, true).apply {
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { shift(d) }
        }
        nav.addView(arrow("›", -1))
        title = tv("", 20f, C_TEXT, true).apply { gravity = Gravity.CENTER }
        nav.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(arrow("‹", 1))
        root.addView(nav)

        val gc = card()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in arrayOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة"))
            head.addView(tv(d, 11f, 0xFFA9BDB4.toInt()).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        gc.addView(head)
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gc.addView(grid)
        root.addView(gc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4) })

        root.addView(tv("🟡 مناسبة  •  🟢 صيام مستحب  •  ⭐ حدث تاريخي", 11f, 0xFFA9BDB4.toInt()).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })

        details = card()
        root.addView(details, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })
        monthEvents = card()
        root.addView(monthEvents, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })

        root.addView(tv("التقويم مبني على جداول أم القرى الرسمية. بداية رمضان والعيدين وذي الحجة تُعتمد برؤية الهلال وإعلان المحكمة العليا، فلو اختلف الإعلان عدّل التاريخ من الإعدادات (± يوم).",
            11f, 0xFFA9BDB4.toInt()).apply { setPadding(dp(6), dp(14), dp(6), 0) })

        setContentView(frame)
        render()
    }

    private fun shift(d: Int) {
        month += d
        if (month < 0) { month = 11; year-- }
        if (month > 11) { month = 0; year++ }
        render()
    }

    /** مناسبات اليوم (عيد، عرفة، رمضان، صيام…) */
    private fun occasions(h: HDate, millis: Long): List<String> {
        val out = ArrayList<String>()
        when {
            h.month == IslamicCalendar.SHAWWAL && h.day == 1 -> out.add("🎉 عيد الفطر المبارك")
            h.month == IslamicCalendar.DHU_AL_HIJJAH && h.day == 10 -> out.add("🎉 عيد الأضحى المبارك (يوم النحر)")
            h.month == IslamicCalendar.DHU_AL_HIJJAH && h.day in 11..13 -> out.add("🐑 من أيام التشريق")
            h.month == IslamicCalendar.DHU_AL_HIJJAH && h.day == 9 -> out.add("🤲 يوم عرفة")
            h.month == IslamicCalendar.RAMADAN && h.day == 1 -> out.add("🌙 أول أيام رمضان")
            h.month == IslamicCalendar.RAMADAN && h.day >= 21 && h.day % 2 == 0 -> out.add("✨ ليلة ${Fmt.num(h.day + 1)} من الليالي الوترية (تبدأ مغرب هذا اليوم)")
        }
        Fasting.reason(millis)?.let { out.add("🌙 يُستحب صيامه: $it") }
        return out
    }

    private fun render() {
        title.text = "${Hijri.MONTHS[month]} ${Fmt.num(year)} هـ"
        grid.removeAllViews()
        val start = Hijri.monthStart(year, month)
        val len = Hijri.monthLength(year, month)
        val c = Calendar.getInstance(); c.timeInMillis = start
        // السبت أول عمود
        val firstCol = (c.get(Calendar.DAY_OF_WEEK) % 7)   // سبت=0، أحد=1 … جمعة=6
        val todayKey = Tracker.dayKey(System.currentTimeMillis())
        val selKey = Tracker.dayKey(selected)
        val gFmt = SimpleDateFormat("d MMM", Locale("ar"))

        var col = 0
        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(firstCol) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(58), 1f)); col++ }
        for (d in 1..len) {
            val millis = start + (d - 1) * 86_400_000L
            val h = HDate(year, month, d)
            val key = Tracker.dayKey(millis)
            val hasOcc = occasions(h, millis).any { !it.startsWith("🌙 يُستحب") }
            val fast = Fasting.reason(millis) != null
            val hist = IslamicHistory.on(h).isNotEmpty()
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                val isToday = key == todayKey
                background = when {
                    key == selKey -> rounded(0x40D9B44A, 12f, C_GOLD)
                    isToday -> rounded(0x26D9B44A, 12f)
                    hasOcc -> rounded(0x1AD9B44A, 12f)
                    else -> null
                }
                setOnClickListener { selected = millis; render() }
            }
            cell.addView(tv(Fmt.num(d), 17f, if (key == todayKey) C_GOLD else C_TEXT, true).apply { gravity = Gravity.CENTER })
            cell.addView(tv(gFmt.format(Date(millis)), 9f, 0xFFA9BDB4.toInt()).apply { gravity = Gravity.CENTER })
            val marks = (if (hasOcc) "🟡" else "") + (if (fast) "🟢" else "") + (if (hist) "⭐" else "")
            cell.addView(tv(marks, 7f, C_TEXT).apply { gravity = Gravity.CENTER })
            row.addView(cell, LinearLayout.LayoutParams(0, dp(58), 1f).also { it.setMargins(dp(1), dp(1), dp(1), dp(1)) })
            col++
            if (col == 7) { grid.addView(row); row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; col = 0 }
        }
        if (col > 0) { while (col < 7) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(58), 1f)); col++ }; grid.addView(row) }

        // تفاصيل اليوم المختار
        details.removeAllViews()
        val sh = Hijri.date(selected)
        details.addView(tv(Hijri.format(selected), 20f, C_GOLD, true))
        details.addView(tv(Fmt.date(selected), 14f, C_TEXT))
        val occ = occasions(sh, selected)
        val hist = IslamicHistory.on(sh)
        if (occ.isEmpty() && hist.isEmpty()) details.addView(tv("لا توجد مناسبة خاصة في هذا اليوم", 13f, 0xFFA9BDB4.toInt()).apply { setPadding(0, dp(8), 0, 0) })
        for (o in occ) details.addView(tv(o, 14f, C_TEXT).apply { setPadding(0, dp(8), 0, 0) })
        if (hist.isNotEmpty()) {
            details.addView(tv("⭐ في مثل هذا اليوم من التاريخ الإسلامي", 14f, C_GOLD, true).apply { setPadding(0, dp(12), 0, 0) })
            for (e in hist) details.addView(tv("• $e", 14f, C_TEXT).apply { setPadding(0, dp(6), 0, 0); setLineSpacing(0f, 1.25f) })
        }

        // أحداث الشهر
        monthEvents.removeAllViews()
        monthEvents.addView(tv("📜 من أحداث شهر ${Hijri.MONTHS[month]}", 16f, C_GOLD, true))
        val evs = IslamicHistory.inMonth(month)
        if (evs.isEmpty()) monthEvents.addView(tv("—", 13f, 0xFFA9BDB4.toInt()))
        for (e in evs) monthEvents.addView(tv((if (e.day > 0) "${Fmt.num(e.day)} ${Hijri.MONTHS[month]}: " else "") + e.text, 13f, C_TEXT).apply {
            setPadding(0, dp(8), 0, 0); setLineSpacing(0f, 1.25f)
        })
    }
}
