package com.alawi.mawaqit

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** سطر الصدقة الجارية أسفل كل صفحة في التطبيق */
object Sadaqa {
    const val LINE1 = "صدقة جارية لأغلى الناس عندي"
    const val LINE2 = "للسيد ماهر حسن علوي وحرمه ماجدة أحمد ريس"
    private const val TAG = "sadaqa_wrapped"

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { attach(a) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    /** نلف محتوى الصفحة: المحتوى فوق، وسطر الصدقة تحت (ما يغطي أي زر) */
    fun attach(a: Activity) {
        val content = a.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (content.findViewWithTag<View>(TAG) != null) return
        val page = content.getChildAt(0) ?: return
        content.removeView(page)
        val wrap = LinearLayout(a).apply {
            tag = TAG
            orientation = LinearLayout.VERTICAL
            background = page.background?.constantState?.newDrawable()
        }
        wrap.addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        wrap.addView(footer(a), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(wrap, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun footer(a: Activity): View {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val gold = 0xFFE3C77B.toInt()
        val font = try { a.resources.getFont(R.font.amiri_quran) } catch (e: Exception) { Typeface.SERIF }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(6), dp(16), dp(7))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF0071A15.toInt(), 0xFF04100D.toInt()))
            // خط ذهبي رفيع فوق
            addView(View(a).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.TRANSPARENT, gold, Color.TRANSPARENT))
            }, LinearLayout.LayoutParams(dp(180), dp(1)).also { it.bottomMargin = dp(5); it.gravity = Gravity.CENTER })
            addView(TextView(a).apply {
                text = "🤍 $LINE1"
                textSize = 12f; setTextColor(gold); gravity = Gravity.CENTER
                typeface = font
            })
            addView(TextView(a).apply {
                text = LINE2
                textSize = 13f; setTextColor(0xFFF3E9C8.toInt()); gravity = Gravity.CENTER
                setTypeface(font, Typeface.BOLD)
            })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "$LINE1، $LINE2"
        }
    }

    /** أول تشغيل: نسأل عن الاسم (الافتراضي أحمد، ويقدر يغيّره) */
    fun askNameOnce(a: Activity) {
        val prefs = Prefs(a)
        val sp = a.getSharedPreferences("name_ask", 0)
        if (sp.getBoolean("asked", false)) return
        sp.edit().putBoolean("asked", true).apply()
        val et = EditText(a).apply {
            setText(prefs.userName); setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            gravity = Gravity.CENTER; textSize = 20f
        }
        val box = FrameLayout(a).apply {
            val p = (20 * a.resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, 0); addView(et)
        }
        AlertDialog.Builder(a)
            .setTitle("حيّاك الله في i.Muslim 🌙")
            .setMessage("ايش اسمك؟ عشان التطبيق يناديك فيه ويذكّرك بالصلاة.\n(تقدر تغيّره بعدين من الإعدادات)")
            .setView(box)
            .setPositiveButton("تمام") { _, _ -> et.text.toString().trim().takeIf { it.isNotBlank() }?.let { prefs.userName = it } }
            .setNegativeButton("خلّه أحمد", null)
            .show()
    }
}
